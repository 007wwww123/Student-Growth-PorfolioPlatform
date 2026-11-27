package com.college.growth.archive.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.college.growth.archive.common.ArchiveConstants;
import com.college.growth.archive.dto.ArchiveBatchDTO;
import com.college.growth.archive.service.ArchiveService;
import com.college.growth.archive.vo.ArchiveCandidateVO;
import com.college.growth.archive.vo.ArchiveResultVO;
import com.college.growth.archive.vo.ArchiveSectionStatusVO;
import com.college.growth.archive.vo.ArchiveStudentResultVO;
import com.college.growth.common.constant.CommonConstants;
import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.context.UserContext;
import com.college.growth.common.enums.ProfileStatusEnum;
import com.college.growth.common.enums.SectionCodeEnum;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.log.service.AuditLogService;
import com.college.growth.common.log.service.ChangeLogService;
import com.college.growth.common.org.entity.OrgClass;
import com.college.growth.common.org.mapper.OrgClassMapper;
import com.college.growth.common.profile.entity.ArcProfile;
import com.college.growth.common.profile.mapper.ArcProfileMapper;
import com.college.growth.common.result.ResultCode;
import com.college.growth.common.student.entity.StuStudent;
import com.college.growth.common.student.mapper.StuStudentMapper;
import com.college.growth.common.org.service.StudentClassService;
import com.college.growth.common.util.AcademicYearUtils;
import com.college.growth.common.util.TextUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 批量归档实现（S-1-11）。
 *
 * <p>判断口径只有一条：六板块齐全，且每块都是 {@code APPROVED} 或 {@code LOCKED}。
 * 已归档计入「完成」，否则同一批名单重复提交时会永远被判为未完成。</p>
 *
 * <p>整批语义：先全量校验、再统一写库。校验阶段发现任一未完成即整批返回未完成清单（EX-12），
 * 不做「先归档能归档的」——毕业归档是一次性动作，混入部分成功会让名单与实物材料对不上。</p>
 */
@Service
public class ArchiveServiceImpl implements ArchiveService {

    private static final Logger log = LoggerFactory.getLogger(ArchiveServiceImpl.class);

    /** 未建档的板块状态展示文案。 */
    private static final String LABEL_NOT_CREATED = "未建档";

    private final ArcProfileMapper profileMapper;

    private final StuStudentMapper studentMapper;

    private final OrgClassMapper orgClassMapper;

    private final ChangeLogService changeLogService;

    private final AuditLogService auditLogService;

    private final StudentClassService studentClassService;

    public ArchiveServiceImpl(ArcProfileMapper profileMapper,
                              StuStudentMapper studentMapper,
                              OrgClassMapper orgClassMapper,
                              ChangeLogService changeLogService,
                              AuditLogService auditLogService,
                              StudentClassService studentClassService) {
        this.profileMapper = profileMapper;
        this.studentMapper = studentMapper;
        this.orgClassMapper = orgClassMapper;
        this.changeLogService = changeLogService;
        this.auditLogService = auditLogService;
        this.studentClassService = studentClassService;
    }

    @Override
    public List<ArchiveCandidateVO> listCandidates(String academicYear, String classCode) {
        String year = AcademicYearUtils.resolve(academicYear);
        List<OrgClass> classes = scopedClasses(classCode, false);
        if (classes.isEmpty()) {
            return List.of();
        }
        Map<String, String> classNames = classes.stream()
                .collect(Collectors.toMap(OrgClass::getClassCode, OrgClass::getClassName, (left, right) -> left));

        List<StuStudent> students = studentMapper.selectList(Wrappers.<StuStudent>lambdaQuery()
                .in(StuStudent::getClassCode, classNames.keySet())
                .orderByAsc(StuStudent::getStudentNo));
        Map<Long, Map<String, ArcProfile>> profiles = loadProfiles(
                students.stream().map(StuStudent::getId).toList(), year);

        List<ArchiveCandidateVO> result = new ArrayList<>(students.size());
        for (StuStudent student : students) {
            ArchiveCandidateVO vo = new ArchiveCandidateVO();
            vo.setStudentId(student.getId());
            vo.setStudentNo(student.getStudentNo());
            vo.setStudentName(student.getName());
            vo.setClassCode(student.getClassCode());
            vo.setClassName(classNames.get(student.getClassCode()));

            Map<String, ArcProfile> owned = profiles.getOrDefault(student.getId(), Map.of());
            List<ArchiveSectionStatusVO> incomplete = new ArrayList<>();
            int approved = 0;
            int locked = 0;
            for (SectionCodeEnum section : SectionCodeEnum.values()) {
                ArcProfile profile = owned.get(section.name());
                if (profile == null) {
                    incomplete.add(sectionStatus(section, null));
                    continue;
                }
                if (ProfileStatusEnum.APPROVED.name().equals(profile.getStatus())) {
                    approved++;
                } else if (ProfileStatusEnum.LOCKED.name().equals(profile.getStatus())) {
                    locked++;
                } else {
                    incomplete.add(sectionStatus(section, profile.getStatus()));
                }
            }
            vo.setSectionCount(owned.size());
            vo.setApprovedCount(approved);
            vo.setLockedCount(locked);
            vo.setIncompleteSections(incomplete);
            vo.setArchivable(incomplete.isEmpty() && owned.size() == CommonConstants.SECTION_COUNT);
            vo.setArchived(locked == CommonConstants.SECTION_COUNT);
            result.add(vo);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ArchiveResultVO archive(ArchiveBatchDTO dto) {
        CurrentUser user = requireCounselor();
        String year = AcademicYearUtils.resolve(dto == null ? null : dto.getAcademicYear());
        List<Long> studentIds = normalizeStudentIds(dto == null ? null : dto.getStudentIds());

        List<StuStudent> students = studentMapper.selectBatchIds(studentIds);
        if (students.size() != studentIds.size()) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "部分学生不存在，请刷新名单后重试");
        }
        Map<Long, StuStudent> studentById = students.stream()
                .collect(Collectors.toMap(StuStudent::getId, Function.identity(), (left, right) -> left));
        List<StuStudent> ordered = studentIds.stream()
                .map(studentById::get)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(StuStudent::getStudentNo, Comparator.nullsLast(String::compareTo)))
                .toList();

        // BR-S03：归档同样是本班写操作，先逐人确认班级归属，再判断板块状态。
        List<OrgClass> classes = scopedClasses(null, true);
        Map<String, String> classNames = classes.stream()
                .collect(Collectors.toMap(OrgClass::getClassCode, OrgClass::getClassName, (left, right) -> left));
        Map<Long, String> classNameByStudent = new HashMap<>();
        for (StuStudent student : ordered) {
            String classCode = resolveClassCode(student, year);
            if (classCode == null || !classNames.containsKey(classCode)) {
                throw new BusinessException(ResultCode.DATA_SCOPE_DENIED,
                        "只能归档本班学生：" + displayName(student));
            }
            classNameByStudent.put(student.getId(), classNames.get(classCode));
        }

        Map<Long, Map<String, ArcProfile>> profiles = loadProfiles(
                ordered.stream().map(StuStudent::getId).toList(), year);

        List<ArchiveStudentResultVO> incomplete = new ArrayList<>();
        List<ArcProfile> toLock = new ArrayList<>();
        List<StuStudent> archivedStudents = new ArrayList<>();
        int skippedStudents = 0;

        for (StuStudent student : ordered) {
            Map<String, ArcProfile> owned = profiles.getOrDefault(student.getId(), Map.of());
            List<String> missing = new ArrayList<>();
            for (SectionCodeEnum section : SectionCodeEnum.values()) {
                ArcProfile profile = owned.get(section.name());
                if (profile == null || !isCompleted(profile.getStatus())) {
                    missing.add(section.getLabel());
                }
            }
            if (!missing.isEmpty()) {
                incomplete.add(studentResult(student, classNameByStudent.get(student.getId()),
                        ArchiveConstants.STATUS_INCOMPLETE, missing));
                continue;
            }
            boolean allLocked = owned.values().stream()
                    .allMatch(profile -> ProfileStatusEnum.LOCKED.name().equals(profile.getStatus()));
            if (allLocked) {
                skippedStudents++;
                continue;
            }
            archivedStudents.add(student);
            for (SectionCodeEnum section : SectionCodeEnum.values()) {
                toLock.add(owned.get(section.name()));
            }
        }

        ArchiveResultVO result = new ArchiveResultVO();
        result.setAcademicYear(year);
        result.setRequestedStudents(ordered.size());

        // EX-12：名单里只要有一个人没完成，整批不动，返回未完成清单。
        if (!incomplete.isEmpty()) {
            result.setRejected(true);
            result.setArchivedStudents(0);
            result.setArchivedProfiles(0);
            result.setSkippedStudents(skippedStudents);
            result.setStudents(incomplete);
            log.info("批量归档被拒 year={} requested={} incomplete={}", year, ordered.size(), incomplete.size());
            return result;
        }

        for (ArcProfile profile : toLock) {
            String from = profile.getStatus();
            profile.setStatus(ProfileStatusEnum.LOCKED.name());
            // 归档后完成标记保持为真：否则「全部通过」的结论会在归档当天被清掉（BR-S10）。
            profile.setCompleted(true);
            int rows = profileMapper.updateById(profile);
            if (rows == 0) {
                throw new BusinessException(ResultCode.VERSION_CONFLICT);
            }
            changeLogService.record(profile, ChangeLogService.BIZ_ARCHIVE, null, null, null,
                    from, ProfileStatusEnum.LOCKED.name(), ArchiveConstants.ARCHIVE_REASON);
        }

        for (StuStudent student : archivedStudents) {
            auditLogService.record(AuditLogService.BIZ_ARCHIVE, ArchiveConstants.TARGET_STUDENT,
                    null, student.getId(), null, null, ArchiveConstants.ACTION_ARCHIVE,
                    "毕业归档：" + year + " 六板块转已归档");
        }

        List<ArchiveStudentResultVO> results = new ArrayList<>(archivedStudents.size());
        for (StuStudent student : archivedStudents) {
            results.add(studentResult(student, classNameByStudent.get(student.getId()),
                    ArchiveConstants.STATUS_ARCHIVED, List.of()));
        }
        result.setRejected(false);
        result.setArchivedStudents(archivedStudents.size());
        result.setArchivedProfiles(toLock.size());
        result.setSkippedStudents(skippedStudents);
        result.setStudents(results);
        log.info("批量归档完成 year={} students={} profiles={} skipped={}",
                year, archivedStudents.size(), toLock.size(), skippedStudents);
        return result;
    }

    /** 已通过与已归档都算完成；其余状态（含未建档）都进未完成清单。 */
    private boolean isCompleted(String status) {
        return ProfileStatusEnum.APPROVED.name().equals(status)
                || ProfileStatusEnum.LOCKED.name().equals(status);
    }

    private ArchiveSectionStatusVO sectionStatus(SectionCodeEnum section, String status) {
        ArchiveSectionStatusVO vo = new ArchiveSectionStatusVO();
        vo.setSectionCode(section.name());
        vo.setSectionName(section.getLabel());
        vo.setStatus(status);
        vo.setStatusLabel(status == null ? LABEL_NOT_CREATED : statusLabel(status));
        return vo;
    }

    private String statusLabel(String status) {
        try {
            return ProfileStatusEnum.valueOf(status).getLabel();
        } catch (IllegalArgumentException ex) {
            return status;
        }
    }

    private ArchiveStudentResultVO studentResult(StuStudent student, String className,
                                                 String status, List<String> missingSections) {
        ArchiveStudentResultVO vo = new ArchiveStudentResultVO();
        vo.setStudentId(student.getId());
        vo.setStudentNo(student.getStudentNo());
        vo.setStudentName(student.getName());
        vo.setClassName(className);
        vo.setStatus(status);
        vo.setMissingSections(missingSections);
        return vo;
    }

    /** 班级优先取学生主数据，主数据缺失时回退到基本信息板块（存量档案）。 */
    private String resolveClassCode(StuStudent student, String academicYear) {
        if (student == null) {
            return null;
        }
        if (!TextUtils.isBlank(student.getClassCode())) {
            return student.getClassCode();
        }
        if (TextUtils.isBlank(academicYear)) {
            return null;
        }
        return studentClassService.findClassValue(student.getId(), academicYear);
    }

    /**
     * 解析数据范围。
     *
     * @param classCode 指定班级，空表示不限
     * @param write     true 表示写操作，只有辅导员可以通过
     * @return 范围内的班级（含班级名），为空表示没有带班
     */
    private List<OrgClass> scopedClasses(String classCode, boolean write) {
        CurrentUser user = UserContext.get();
        boolean counselor = user.isCounselor() && user.userId() != null && user.userId() > 0;
        if (write && !counselor) {
            throw new BusinessException(ResultCode.DATA_SCOPE_DENIED, "仅辅导员可以执行毕业归档");
        }
        if (!counselor && !user.isAdminWithGrant()) {
            throw new BusinessException(ResultCode.DATA_SCOPE_DENIED, "无权查看归档名单");
        }
        List<OrgClass> classes = orgClassMapper.selectList(Wrappers.<OrgClass>lambdaQuery()
                .eq(counselor, OrgClass::getCounselorUserId, user.userId())
                .eq(OrgClass::getStatus, 1)
                .orderByAsc(OrgClass::getClassCode));
        // 管理员持临时授权时是只读全量范围：不加辅导员条件，查询即全部在读班级
        if (!TextUtils.isBlank(classCode)) {
            String target = classCode.trim();
            boolean allowed = classes.stream().anyMatch(item -> target.equals(item.getClassCode()));
            if (!allowed) {
                throw new BusinessException(ResultCode.DATA_SCOPE_DENIED, "无权访问该班级的归档名单");
            }
            return classes.stream().filter(item -> target.equals(item.getClassCode())).toList();
        }
        return classes;
    }

    private Map<Long, Map<String, ArcProfile>> loadProfiles(List<Long> studentIds, String academicYear) {
        Map<Long, Map<String, ArcProfile>> result = new HashMap<>();
        if (studentIds.isEmpty()) {
            return result;
        }
        List<ArcProfile> profiles = profileMapper.selectList(Wrappers.<ArcProfile>lambdaQuery()
                .in(ArcProfile::getStudentId, studentIds)
                .eq(ArcProfile::getAcademicYear, academicYear));
        for (ArcProfile profile : profiles) {
            result.computeIfAbsent(profile.getStudentId(), key -> new LinkedHashMap<>())
                    .put(profile.getSectionCode(), profile);
        }
        return result;
    }

    private List<Long> normalizeStudentIds(List<Long> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "请至少选择一名学生");
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (Long id : raw) {
            if (id != null && id > 0) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "请至少选择一名学生");
        }
        if (ids.size() > ArchiveConstants.MAX_STUDENT_COUNT) {
            throw new BusinessException(ResultCode.PARAM_INVALID,
                    "单次最多归档 " + ArchiveConstants.MAX_STUDENT_COUNT + " 名学生");
        }
        return List.copyOf(ids);
    }

    private CurrentUser requireCounselor() {
        CurrentUser user = UserContext.get();
        if (!user.isCounselor() || user.userId() == null || user.userId() <= 0) {
            throw new BusinessException(ResultCode.DATA_SCOPE_DENIED, "仅辅导员可以执行毕业归档");
        }
        return user;
    }

    private String displayName(StuStudent student) {
        return TextUtils.isBlank(student.getName()) ? String.valueOf(student.getId()) : student.getName();
    }
}
