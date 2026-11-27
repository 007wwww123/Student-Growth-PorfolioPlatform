package com.college.growth.completion.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.college.growth.common.constant.CommonConstants;
import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.context.UserContext;
import com.college.growth.common.enums.ProfileStatusEnum;
import com.college.growth.common.enums.SectionCodeEnum;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.log.service.AuditLogService;
import com.college.growth.common.org.entity.OrgClass;
import com.college.growth.common.org.service.StudentClassService;
import com.college.growth.common.profile.entity.ArcProfile;
import com.college.growth.common.profile.mapper.ArcProfileMapper;
import com.college.growth.common.result.ResultCode;
import com.college.growth.common.student.service.StudentMasterService;
import com.college.growth.common.util.AcademicYearUtils;
import com.college.growth.completion.common.CompletionConstants;
import com.college.growth.completion.service.CompletionService;
import com.college.growth.completion.vo.CompletionRefreshVO;
import com.college.growth.completion.vo.CompletionSectionVO;
import com.college.growth.completion.vo.CompletionStatusVO;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 完成标记实现（BR-S10）。
 *
 * <p>判定按「板块编码去重后的集合」计算，而不是数记录条数：唯一键保证一个板块只有一条，
 * 但历史数据里出现过重复建档，按条数统计会把五块误判成六块。</p>
 *
 * <p>写库只在标记翻转时发生。定时任务每天扫一遍全学年，如果每次无条件 UPDATE，
 * {@code arc_profile.updated_at} 会被刷新成「今天」，与「学生最后改动时间」语义冲突。</p>
 */
@Service
public class CompletionServiceImpl implements CompletionService {

    private static final Logger log = LoggerFactory.getLogger(CompletionServiceImpl.class);

    private final ArcProfileMapper profileMapper;

    private final StudentMasterService studentMasterService;

    private final StudentClassService studentClassService;

    private final AuditLogService auditLogService;

    public CompletionServiceImpl(ArcProfileMapper profileMapper,
                                 StudentMasterService studentMasterService,
                                 StudentClassService studentClassService,
                                 AuditLogService auditLogService) {
        this.profileMapper = profileMapper;
        this.studentMasterService = studentMasterService;
        this.studentClassService = studentClassService;
        this.auditLogService = auditLogService;
    }

    @Override
    public boolean isCompleted(List<ArcProfile> profiles) {
        if (profiles == null || profiles.isEmpty()) {
            return false;
        }
        Set<SectionCodeEnum> done = EnumSet.noneOf(SectionCodeEnum.class);
        for (ArcProfile profile : profiles) {
            SectionCodeEnum section = SectionCodeEnum.tryFromCode(profile.getSectionCode());
            if (section != null && isCompletedStatus(profile.getStatus())) {
                done.add(section);
            }
        }
        return done.size() >= CommonConstants.SECTION_COUNT;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean refresh(Long studentId, String academicYear) {
        if (studentId == null || studentId <= 0) {
            return false;
        }
        String year = AcademicYearUtils.resolve(academicYear);
        return refreshOne(studentId, year, CompletionConstants.TRIGGER_WORKFLOW)
                .getChangedStudents() > 0;
    }

    @Override
    public CompletionStatusVO statusOf(Long studentId, String academicYear) {
        if (studentId == null || studentId <= 0) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "缺少学生 ID");
        }
        String year = AcademicYearUtils.resolve(academicYear);
        requireReadable(studentId, year);

        List<ArcProfile> profiles = load(studentId, year);
        Map<SectionCodeEnum, ArcProfile> bySection = new EnumMap<>(SectionCodeEnum.class);
        for (ArcProfile profile : profiles) {
            SectionCodeEnum section = SectionCodeEnum.tryFromCode(profile.getSectionCode());
            if (section != null) {
                bySection.put(section, profile);
            }
        }

        List<CompletionSectionVO> sections = new ArrayList<>();
        List<String> pending = new ArrayList<>();
        int created = 0;
        int completedSections = 0;
        for (SectionCodeEnum section : SectionCodeEnum.values()) {
            ArcProfile profile = bySection.get(section);
            CompletionSectionVO item = new CompletionSectionVO();
            item.setSectionCode(section.getCode());
            item.setSectionName(section.getLabel());
            if (profile == null) {
                item.setStatusLabel(CompletionConstants.STATUS_NOT_CREATED);
                pending.add(section.getLabel());
            } else {
                created++;
                item.setStatus(profile.getStatus());
                item.setStatusLabel(statusLabel(profile.getStatus()));
                item.setCompleted(isCompletedStatus(profile.getStatus()));
                if (item.isCompleted()) {
                    completedSections++;
                } else {
                    pending.add(section.getLabel());
                }
            }
            sections.add(item);
        }

        CompletionStatusVO vo = new CompletionStatusVO();
        vo.setStudentId(studentId);
        vo.setAcademicYear(year);
        // 标记以库内派生值为准，不在这里重算，避免「页面显示完成但归档接口说没完成」。
        vo.setCompleted(isCompleted(profiles));
        vo.setCreatedSectionCount(created);
        vo.setCompletedSectionCount(completedSections);
        vo.setPendingSections(pending);
        vo.setSections(sections);
        studentMasterService.find(studentId).ifPresent(student -> {
            vo.setStudentNo(student.getStudentNo());
            vo.setStudentName(student.getName());
            vo.setClassCode(student.getClassCode());
        });
        OrgClass orgClass = studentClassService.findClass(studentId, year);
        if (orgClass != null) {
            vo.setClassName(orgClass.getClassName());
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CompletionRefreshVO manualRefresh(Long studentId, String academicYear) {
        String year = AcademicYearUtils.resolve(academicYear);
        CurrentUser user = UserContext.get();
        CompletionRefreshVO result;
        if (studentId == null) {
            if (!user.isAdminWithGrant()) {
                throw new BusinessException(ResultCode.DATA_SCOPE_DENIED,
                        "整学年重算仅管理员（持临时授权）可执行");
            }
            result = refreshAcademicYear(year, CompletionConstants.TRIGGER_MANUAL);
            audit(null, "整学年重算完成标记：" + year + "，判定 " + result.getCheckedStudents()
                    + " 名学生，完成 " + result.getCompletedStudents()
                    + " 名，标记变化 " + result.getChangedStudents() + " 名");
            return result;
        }

        requireRefreshable(studentId, year, user);
        result = refreshOne(studentId, year, CompletionConstants.TRIGGER_MANUAL);
        audit(studentId, "重算学生完成标记：" + year + "，完成=" + (result.getCompletedStudents() > 0)
                + "，标记" + (result.getChangedStudents() > 0 ? "已变化" : "无变化"));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CompletionRefreshVO refreshAcademicYear(String academicYear, String trigger) {
        String year = AcademicYearUtils.resolve(academicYear);
        List<ArcProfile> profiles = profileMapper.selectList(Wrappers.<ArcProfile>lambdaQuery()
                .select(ArcProfile::getStudentId, ArcProfile::getSectionCode,
                        ArcProfile::getStatus, ArcProfile::getCompleted)
                .eq(ArcProfile::getAcademicYear, year));

        Map<Long, List<ArcProfile>> byStudent = new LinkedHashMap<>();
        if (profiles != null) {
            for (ArcProfile profile : profiles) {
                byStudent.computeIfAbsent(profile.getStudentId(), key -> new ArrayList<>()).add(profile);
            }
        }

        int completedStudents = 0;
        int changedStudents = 0;
        for (Map.Entry<Long, List<ArcProfile>> entry : byStudent.entrySet()) {
            List<ArcProfile> list = entry.getValue();
            boolean completed = isCompleted(list);
            if (completed) {
                completedStudents++;
            }
            if (applyCompleted(entry.getKey(), year, list, completed)) {
                changedStudents++;
            }
        }

        CompletionRefreshVO vo = new CompletionRefreshVO();
        vo.setAcademicYear(year);
        vo.setTrigger(trigger);
        vo.setCheckedStudents(byStudent.size());
        vo.setCompletedStudents(completedStudents);
        vo.setChangedStudents(changedStudents);
        vo.setRefreshedAt(LocalDateTime.now());
        log.info("整学年重算完成标记 year={} trigger={} 学生={} 完成={} 变化={}",
                year, trigger, vo.getCheckedStudents(), completedStudents, changedStudents);
        return vo;
    }

    /** 单学生重算并返回可读结果。 */
    private CompletionRefreshVO refreshOne(Long studentId, String year, String trigger) {
        List<ArcProfile> profiles = load(studentId, year);
        boolean completed = isCompleted(profiles);
        boolean changed = applyCompleted(studentId, year, profiles, completed);

        CompletionRefreshVO vo = new CompletionRefreshVO();
        vo.setAcademicYear(year);
        vo.setTrigger(trigger);
        vo.setCheckedStudents(profiles.isEmpty() ? 0 : 1);
        vo.setCompletedStudents(completed ? 1 : 0);
        vo.setChangedStudents(changed ? 1 : 0);
        vo.setRefreshedAt(LocalDateTime.now());
        return vo;
    }

    /**
     * 把判定结果写进该学生本学年的全部板块实例。
     *
     * <p>标记是「学生 × 学年」级别的，六条记录冗余同一份值，读单板块时不用再聚合。
     * 这里用 lambdaUpdate 而不是 updateById：派生标记不参与乐观锁，
     * 否则会和用户正在编辑的板块抢版本号（EC-40907）。</p>
     *
     * @return 是否发生了实际写库
     */
    private boolean applyCompleted(Long studentId, String year, List<ArcProfile> profiles,
                                   boolean completed) {
        if (profiles == null || profiles.isEmpty()) {
            return false;
        }
        boolean unchanged = profiles.stream()
                .allMatch(item -> Objects.equals(item.getCompleted(), completed));
        if (unchanged) {
            return false;
        }
        profileMapper.update(null, Wrappers.<ArcProfile>lambdaUpdate()
                .eq(ArcProfile::getStudentId, studentId)
                .eq(ArcProfile::getAcademicYear, year)
                .set(ArcProfile::getCompleted, completed));
        return true;
    }

    private List<ArcProfile> load(Long studentId, String year) {
        List<ArcProfile> profiles = profileMapper.selectList(Wrappers.<ArcProfile>lambdaQuery()
                .eq(ArcProfile::getStudentId, studentId)
                .eq(ArcProfile::getAcademicYear, year));
        return profiles == null ? List.of() : profiles;
    }

    /** 已完成口径：已通过与已归档都算完成，归档不能把标记算回未完成。 */
    private boolean isCompletedStatus(String status) {
        return ProfileStatusEnum.APPROVED.name().equals(status)
                || ProfileStatusEnum.LOCKED.name().equals(status);
    }

    private String statusLabel(String status) {
        if (status == null) {
            return CompletionConstants.STATUS_NOT_CREATED;
        }
        for (ProfileStatusEnum item : ProfileStatusEnum.values()) {
            if (item.name().equals(status)) {
                return item.getLabel();
            }
        }
        return status;
    }

    /** 读权限：学生本人 / 本班辅导员 / 持临时授权的管理员。 */
    private void requireReadable(Long studentId, String year) {
        CurrentUser user = UserContext.get();
        if (user.isStudent()) {
            if (!studentId.equals(user.userId())) {
                throw new BusinessException(ResultCode.DATA_SCOPE_DENIED, "只能查看本人档案的完成标记");
            }
            return;
        }
        if (user.isCounselor()) {
            if (!studentClassService.isCounselorOf(studentId, year, user.userId())) {
                throw new BusinessException(ResultCode.DATA_SCOPE_DENIED, "该学生不在你负责的班级内");
            }
            return;
        }
        if (!user.isAdminWithGrant()) {
            throw new BusinessException(ResultCode.DATA_SCOPE_DENIED, "无权查看完成标记");
        }
    }

    /**
     * 写权限：管理员需临时授权，辅导员限本班，学生一律拒绝。
     *
     * <p>学生不能触发重算，否则「完成」会变成学生可自行制造的状态。</p>
     */
    private void requireRefreshable(Long studentId, String year, CurrentUser user) {
        if (user.isAdminWithGrant()) {
            return;
        }
        if (user.isCounselor()) {
            if (!studentClassService.isCounselorOf(studentId, year, user.userId())) {
                throw new BusinessException(ResultCode.DATA_SCOPE_DENIED, "只能重算本班学生的完成标记");
            }
            return;
        }
        throw new BusinessException(ResultCode.DATA_SCOPE_DENIED, "仅管理员或辅导员可以重算完成标记");
    }

    private void audit(Long studentId, String detail) {
        auditLogService.record(AuditLogService.BIZ_COMPLETION, CompletionConstants.TARGET_STUDENT,
                studentId, studentId, null, null, CompletionConstants.ACTION_REFRESH, detail);
    }
}
