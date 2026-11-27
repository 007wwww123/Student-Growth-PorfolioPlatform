package com.college.growth.enroll.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.college.growth.common.constant.CommonConstants;
import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.context.UserContext;
import com.college.growth.common.enums.ProfileStatusEnum;
import com.college.growth.common.enums.SectionCodeEnum;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.log.service.AuditLogService;
import com.college.growth.common.org.entity.OrgClass;
import com.college.growth.common.org.mapper.OrgClassMapper;
import com.college.growth.common.profile.entity.ArcProfile;
import com.college.growth.common.profile.mapper.ArcProfileMapper;
import com.college.growth.common.result.ResultCode;
import com.college.growth.common.student.entity.StuStudent;
import com.college.growth.common.student.mapper.StuStudentMapper;
import com.college.growth.common.util.AcademicYearUtils;
import com.college.growth.common.util.TextUtils;
import com.college.growth.enroll.common.EnrollConstants;
import com.college.growth.enroll.dto.BatchCreateDTO;
import com.college.growth.enroll.service.ProfileEnrollService;
import com.college.growth.enroll.vo.BatchCreateResultVO;
import com.college.growth.enroll.vo.ClassEnrollVO;
import com.college.growth.enroll.vo.StudentEnrollItemVO;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 批量建档实现（S-1-01）。
 *
 * <p>建档只写 {@code arc_profile}，六个板块各一条 DRAFT 记录；字段值区分文不动，
 * 因此这里不需要字段定义、不需要校验器，也不会触碰 {@code arc_profile_item}。</p>
 *
 * <p>关于「已存在则跳过」：唯一键是「学生 × 板块 × 学年」，
 * 因此逐板块判断，而不是逐学生判断——上学期只建了一半的档案，这次会把缺的补齐，
 * 而不是因为「该学生已有档案」整人跳过。</p>
 */
@Service
public class ProfileEnrollServiceImpl implements ProfileEnrollService {

    private static final Logger log = LoggerFactory.getLogger(ProfileEnrollServiceImpl.class);

    /** 学生主数据状态：在读。休学、退学、毕业不参与新建档。 */
    private static final int STUDENT_ACTIVE = 1;

    private final ArcProfileMapper profileMapper;

    private final StuStudentMapper studentMapper;

    private final OrgClassMapper orgClassMapper;

    private final AuditLogService auditLogService;

    public ProfileEnrollServiceImpl(ArcProfileMapper profileMapper,
                                    StuStudentMapper studentMapper,
                                    OrgClassMapper orgClassMapper,
                                    AuditLogService auditLogService) {
        this.profileMapper = profileMapper;
        this.studentMapper = studentMapper;
        this.orgClassMapper = orgClassMapper;
        this.auditLogService = auditLogService;
    }

    @Override
    public List<ClassEnrollVO> listClasses(String academicYear) {
        CurrentUser user = requireCounselor();
        String year = AcademicYearUtils.resolve(academicYear);

        List<OrgClass> classes = orgClassMapper.selectList(Wrappers.<OrgClass>lambdaQuery()
                .eq(OrgClass::getCounselorUserId, user.userId())
                .eq(OrgClass::getStatus, 1)
                .orderByAsc(OrgClass::getClassCode));
        if (classes.isEmpty()) {
            return List.of();
        }

        List<String> classCodes = classes.stream().map(OrgClass::getClassCode).toList();
        Map<String, List<StuStudent>> studentsByClass = loadStudents(classCodes);
        Map<Long, Set<String>> sectionsByStudent = loadSections(
                studentsByClass.values().stream().flatMap(List::stream).map(StuStudent::getId).toList(), year);

        List<ClassEnrollVO> result = new ArrayList<>(classes.size());
        for (OrgClass orgClass : classes) {
            List<StuStudent> students = studentsByClass.getOrDefault(orgClass.getClassCode(), List.of());
            int enrolled = (int) students.stream()
                    .filter(student -> sectionCount(sectionsByStudent, student.getId()) >= CommonConstants.SECTION_COUNT)
                    .count();
            ClassEnrollVO vo = new ClassEnrollVO();
            vo.setClassCode(orgClass.getClassCode());
            vo.setClassName(orgClass.getClassName());
            vo.setMajorName(orgClass.getMajorName());
            vo.setGradeYear(orgClass.getGradeYear());
            vo.setStudentCount(students.size());
            vo.setEnrolledStudentCount(enrolled);
            vo.setMissingStudentCount(students.size() - enrolled);
            vo.setEnrolled(!students.isEmpty() && enrolled == students.size());
            result.add(vo);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchCreateResultVO batchCreate(BatchCreateDTO dto) {
        CurrentUser user = requireCounselor();
        String year = AcademicYearUtils.resolve(dto == null ? null : dto.getAcademicYear());
        List<String> classCodes = normalizeClassCodes(dto == null ? null : dto.getClassCodes());

        List<OrgClass> classes = orgClassMapper.selectList(Wrappers.<OrgClass>lambdaQuery()
                .in(OrgClass::getClassCode, classCodes)
                .eq(OrgClass::getStatus, 1));
        Map<String, OrgClass> classByCode = classes.stream()
                .collect(Collectors.toMap(OrgClass::getClassCode, item -> item, (left, right) -> left));
        List<String> missing = classCodes.stream().filter(code -> !classByCode.containsKey(code)).toList();
        if (!missing.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "班级不存在或已停用：" + String.join("、", missing));
        }
        // BR-S03：辅导员的写权限按班级判定，跨班建档与跨班审核同样拒绝。
        for (String code : classCodes) {
            if (!Objects.equals(classByCode.get(code).getCounselorUserId(), user.userId())) {
                throw new BusinessException(ResultCode.DATA_SCOPE_DENIED,
                        "只能为本班学生建档：" + classByCode.get(code).getClassName());
            }
        }

        Map<String, List<StuStudent>> studentsByClass = loadStudents(classCodes);
        List<StuStudent> students = studentsByClass.values().stream()
                .flatMap(List::stream)
                .sorted(Comparator.comparing(StuStudent::getStudentNo, Comparator.nullsLast(String::compareTo)))
                .toList();
        if (students.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "所选班级下没有在读学生");
        }
        if (students.size() > EnrollConstants.MAX_STUDENT_COUNT) {
            throw new BusinessException(ResultCode.PARAM_INVALID,
                    "单次最多为 " + EnrollConstants.MAX_STUDENT_COUNT + " 名学生建档，请分班分批操作");
        }

        Map<Long, Set<String>> existing = loadSections(students.stream().map(StuStudent::getId).toList(), year);
        List<StudentEnrollItemVO> items = new ArrayList<>(students.size());
        int createdProfiles = 0;
        int existingProfiles = 0;
        int createdStudents = 0;

        for (StuStudent student : students) {
            Set<String> owned = existing.getOrDefault(student.getId(), Set.of());
            List<String> createdSections = new ArrayList<>();
            for (SectionCodeEnum section : SectionCodeEnum.values()) {
                if (owned.contains(section.name())) {
                    existingProfiles++;
                    continue;
                }
                // 逐条插入而不是 insert(Collection)：单批不超过 500 人，逐条走的是同一事务，
                // 出错时整批回滚，语义比批量会话更好懂。
                profileMapper.insert(newProfile(student.getId(), section.name(), year));
                createdSections.add(section.name());
                createdProfiles++;
            }

            StudentEnrollItemVO item = new StudentEnrollItemVO();
            item.setStudentId(student.getId());
            item.setStudentNo(student.getStudentNo());
            item.setStudentName(student.getName());
            item.setClassCode(student.getClassCode());
            item.setClassName(classByCode.containsKey(student.getClassCode())
                    ? classByCode.get(student.getClassCode()).getClassName() : null);
            item.setCreatedSections(createdSections);
            item.setSkippedSectionCount(CommonConstants.SECTION_COUNT - createdSections.size());
            item.setStatus(createdSections.isEmpty()
                    ? EnrollConstants.STATUS_SKIPPED : EnrollConstants.STATUS_CREATED);
            items.add(item);

            if (!createdSections.isEmpty()) {
                createdStudents++;
                // 建档是一次「学生级」动作：六个板块同生同灭，按板块逐条写审计只会制造噪音。
                auditLogService.record(AuditLogService.BIZ_IMPORT, EnrollConstants.TARGET_STUDENT,
                        student.getId(), student.getId(), null, null, EnrollConstants.ACTION_BATCH_CREATE,
                        "批量建档：" + year + " 新建 " + createdSections.size() + " 个板块实例");
            }
        }

        BatchCreateResultVO result = new BatchCreateResultVO();
        result.setAcademicYear(year);
        result.setClassCodes(classCodes);
        result.setTotalStudents(students.size());
        result.setCreatedStudents(createdStudents);
        result.setSkippedStudents(students.size() - createdStudents);
        result.setCreatedProfiles(createdProfiles);
        result.setExistingProfiles(existingProfiles);
        result.setStudents(items);
        log.info("批量建档完成 year={} classes={} students={} createdProfiles={} existingProfiles={}",
                year, classCodes, students.size(), createdProfiles, existingProfiles);
        return result;
    }

    /** 新建一条板块实例：DRAFT、完成度 0、未计完成、次数清零。 */
    private ArcProfile newProfile(Long studentId, String sectionCode, String academicYear) {
        ArcProfile profile = new ArcProfile();
        profile.setStudentId(studentId);
        profile.setAcademicYear(academicYear);
        profile.setSectionCode(sectionCode);
        profile.setStatus(ProfileStatusEnum.DRAFT.name());
        profile.setCompleted(false);
        profile.setCompleteness(BigDecimal.ZERO);
        profile.setSubmitCount(0);
        profile.setWithdrawCount(0);
        profile.setVersion(0);
        return profile;
    }

    /** 按班级取在读学生，返回顺序与入参班级一致。 */
    private Map<String, List<StuStudent>> loadStudents(List<String> classCodes) {
        List<StuStudent> students = studentMapper.selectList(Wrappers.<StuStudent>lambdaQuery()
                .in(StuStudent::getClassCode, classCodes)
                .eq(StuStudent::getStatus, STUDENT_ACTIVE)
                .orderByAsc(StuStudent::getStudentNo));
        Map<String, List<StuStudent>> result = new LinkedHashMap<>();
        for (String code : classCodes) {
            result.put(code, new ArrayList<>());
        }
        for (StuStudent student : students) {
            result.computeIfAbsent(student.getClassCode(), key -> new ArrayList<>()).add(student);
        }
        return result;
    }

    /** 取这些学生本学年已存在的板块编码。 */
    private Map<Long, Set<String>> loadSections(List<Long> studentIds, String academicYear) {
        Map<Long, Set<String>> result = new HashMap<>();
        if (studentIds.isEmpty()) {
            return result;
        }
        List<ArcProfile> profiles = profileMapper.selectList(Wrappers.<ArcProfile>lambdaQuery()
                .in(ArcProfile::getStudentId, studentIds)
                .eq(ArcProfile::getAcademicYear, academicYear));
        for (ArcProfile profile : profiles) {
            result.computeIfAbsent(profile.getStudentId(), key -> new LinkedHashSet<>())
                    .add(profile.getSectionCode());
        }
        return result;
    }

    private int sectionCount(Map<Long, Set<String>> sectionsByStudent, Long studentId) {
        Set<String> sections = sectionsByStudent.get(studentId);
        return sections == null ? 0 : sections.size();
    }

    /** 去空白、去重、保持入参顺序，并校验数量上限。 */
    private List<String> normalizeClassCodes(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "请至少选择一个班级");
        }
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        for (String code : raw) {
            if (!TextUtils.isBlank(code)) {
                codes.add(code.trim());
            }
        }
        if (codes.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "请至少选择一个班级");
        }
        if (codes.size() > EnrollConstants.MAX_CLASS_COUNT) {
            throw new BusinessException(ResultCode.PARAM_INVALID,
                    "单次最多选择 " + EnrollConstants.MAX_CLASS_COUNT + " 个班级");
        }
        return List.copyOf(codes);
    }

    /** 仅辅导员可建档；管理员是「只读授权」，不能写业务数据（BR-S03）。 */
    private CurrentUser requireCounselor() {
        CurrentUser user = UserContext.get();
        if (!user.isCounselor() || user.userId() == null || user.userId() <= 0) {
            throw new BusinessException(ResultCode.DATA_SCOPE_DENIED, "仅辅导员可以为本班学生建档");
        }
        return user;
    }
}
