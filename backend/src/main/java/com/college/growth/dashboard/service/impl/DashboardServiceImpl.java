package com.college.growth.dashboard.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.context.UserContext;
import com.college.growth.common.enums.SectionCodeEnum;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.org.entity.OrgClass;
import com.college.growth.common.org.mapper.OrgClassMapper;
import com.college.growth.common.result.ResultCode;
import com.college.growth.common.util.AcademicYearUtils;
import com.college.growth.common.util.TextUtils;
import com.college.growth.dashboard.common.DashboardConstants;
import com.college.growth.dashboard.mapper.DashboardClassRow;
import com.college.growth.dashboard.mapper.DashboardMapper;
import com.college.growth.dashboard.mapper.DashboardSectionRow;
import com.college.growth.dashboard.service.DashboardService;
import com.college.growth.dashboard.vo.ClassProgressVO;
import com.college.growth.dashboard.vo.DashboardOverviewVO;
import com.college.growth.dashboard.vo.SectionProgressVO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 首页进度聚合实现。
 *
 * <p>两次分组查询（班级维度、板块×状态维度）之后全部在内存里拼装：
 * 统计口径只依赖这两份行数据，加一个指标不需要再加 SQL，也不会出现
 * 「总数来自一条 SQL、分布来自另一条」的口径漂移。</p>
 *
 * <p>退回率与平均审核时长都在这里做「先求和、再相除」：分子分母分别来自同一批班级行，
 * 班级行只回传份数与秒数之和，整体值按秒数加权，避免对各班均值再平均。</p>
 *
 * <p>本类不写库、不重算完成标记，是纯读能力，因此不加事务。</p>
 */
@Service
public class DashboardServiceImpl implements DashboardService {

    private final DashboardMapper dashboardMapper;

    private final OrgClassMapper orgClassMapper;

    public DashboardServiceImpl(DashboardMapper dashboardMapper, OrgClassMapper orgClassMapper) {
        this.dashboardMapper = dashboardMapper;
        this.orgClassMapper = orgClassMapper;
    }

    @Override
    public DashboardOverviewVO overview(String academicYear, String classCode) {
        CurrentUser user = UserContext.get();
        String year = AcademicYearUtils.resolve(academicYear);
        Long counselorId = resolveCounselorScope(user);
        String normalizedClass = TextUtils.isBlank(classCode) ? null : classCode.trim();
        OrgClass targetClass = null;
        if (normalizedClass != null) {
            targetClass = requireVisibleClass(normalizedClass, user, counselorId);
        }

        List<DashboardClassRow> classRows = dashboardMapper.selectClassRows(year, counselorId, normalizedClass);
        List<DashboardSectionRow> sectionRows = dashboardMapper.selectSectionRows(year, counselorId,
                normalizedClass);

        DashboardOverviewVO vo = new DashboardOverviewVO();
        vo.setAcademicYear(year);
        vo.setScopeLabel(scopeLabel(user, targetClass));
        vo.setSections(toSections(sectionRows));
        vo.setClasses(toClasses(classRows));

        int studentTotal = 0;
        int completedStudents = 0;
        int pendingReview = 0;
        int returnedProfiles = 0;
        int draftProfiles = 0;
        int submittedProfilesTotal = 0;
        int reviewedProfiles = 0;
        long reviewSeconds = 0L;
        for (DashboardClassRow row : classRows) {
            // 一个学生只属于一个班，按班求和不会重复计数。
            studentTotal += nvl(row.getStudentTotal());
            completedStudents += nvl(row.getCompletedStudents());
            pendingReview += nvl(row.getSubmittedProfiles());
            returnedProfiles += nvl(row.getReturnedProfiles());
            draftProfiles += nvl(row.getDraftProfiles());
            submittedProfilesTotal += nvl(row.getSubmittedProfilesTotal());
            // 平均审核时长按「秒数之和 / 份数之和」加权，不能对班级均值再求平均。
            reviewedProfiles += nvl(row.getReviewedProfiles());
            reviewSeconds += nvl(row.getReviewSeconds());
        }
        vo.setStudentTotal(studentTotal);
        vo.setCompletedStudents(completedStudents);
        vo.setCompletionRate(rate(completedStudents, studentTotal));
        vo.setPendingReview(pendingReview);
        vo.setReturnedProfiles(returnedProfiles);
        // 退回率的分子分母分别求和后再除：先算各班退回率再平均会把小班放大。
        vo.setReturnedRate(rate(returnedProfiles, submittedProfilesTotal));
        vo.setReviewedProfiles(reviewedProfiles);
        vo.setAverageReviewHours(averageHours(reviewSeconds, reviewedProfiles));
        vo.setDraftProfiles(draftProfiles);
        return vo;
    }

    /**
     * 解析数据范围。
     *
     * @return 辅导员 ID（只看带班）；null 表示不限（仅持临时授权的管理员）
     */
    private Long resolveCounselorScope(CurrentUser user) {
        if (user.isCounselor()) {
            if (user.userId() == null || user.userId() <= 0) {
                throw new BusinessException(ResultCode.DATA_SCOPE_DENIED, "缺少辅导员身份，无法查看看板");
            }
            return user.userId();
        }
        if (user.isAdminWithGrant()) {
            return null;
        }
        if (user.isStudent()) {
            throw new BusinessException(ResultCode.DATA_SCOPE_DENIED,
                    "学生请在各板块页面查看本人进度");
        }
        throw new BusinessException(ResultCode.DATA_SCOPE_DENIED, "管理员默认无业务数据权限");
    }

    /** 班级筛选也要过数据范围：辅导员只能查自己的班，否则传个别人的班号就能看到别人数据。 */
    private OrgClass requireVisibleClass(String classCode, CurrentUser user, Long counselorId) {
        OrgClass orgClass = orgClassMapper.selectOne(Wrappers.<OrgClass>lambdaQuery()
                .eq(OrgClass::getClassCode, classCode)
                .last("limit 1"));
        if (orgClass == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "班级不存在：" + classCode);
        }
        if (counselorId != null && !counselorId.equals(orgClass.getCounselorUserId())) {
            throw new BusinessException(ResultCode.DATA_SCOPE_DENIED, "只能查看本班数据");
        }
        return orgClass;
    }

    private String scopeLabel(CurrentUser user, OrgClass targetClass) {
        if (targetClass != null) {
            return TextUtils.isBlank(targetClass.getClassName())
                    ? targetClass.getClassCode() : targetClass.getClassName();
        }
        return user.isCounselor() ? DashboardConstants.SCOPE_MY_CLASSES : DashboardConstants.SCOPE_SCHOOL;
    }

    private List<SectionProgressVO> toSections(List<DashboardSectionRow> rows) {
        Map<SectionCodeEnum, SectionProgressVO> bySection = new EnumMap<>(SectionCodeEnum.class);
        for (SectionCodeEnum section : SectionCodeEnum.values()) {
            SectionProgressVO vo = new SectionProgressVO();
            vo.setSectionCode(section.getCode());
            vo.setSectionName(section.getLabel());
            vo.setCompletionRate(BigDecimal.ZERO.setScale(DashboardConstants.RATE_SCALE));
            bySection.put(section, vo);
        }
        if (rows != null) {
            for (DashboardSectionRow row : rows) {
                SectionCodeEnum section = SectionCodeEnum.tryFromCode(row.getSectionCode());
                if (section == null) {
                    continue;
                }
                SectionProgressVO vo = bySection.get(section);
                int count = nvl(row.getProfileCount());
                switch (String.valueOf(row.getStatus())) {
                    case "DRAFT" -> vo.setDraft(vo.getDraft() + count);
                    case "SUBMITTED" -> vo.setSubmitted(vo.getSubmitted() + count);
                    case "RETURNED" -> vo.setReturned(vo.getReturned() + count);
                    case "APPROVED" -> vo.setApproved(vo.getApproved() + count);
                    case "LOCKED" -> vo.setLocked(vo.getLocked() + count);
                    default -> {
                        // 未知状态不入分布，避免脏数据把总数撑大
                    }
                }
                // 同一板块内每个学生只有一条记录，各状态学生数相加即该板块建档学生数
                vo.setStudentCount(vo.getStudentCount() + nvl(row.getStudentCount()));
            }
        }
        List<SectionProgressVO> sections = new ArrayList<>(bySection.values());
        for (SectionProgressVO vo : sections) {
            vo.setCompletionRate(rate(vo.getApproved() + vo.getLocked(), vo.getStudentCount()));
        }
        return sections;
    }

    private List<ClassProgressVO> toClasses(List<DashboardClassRow> rows) {
        List<ClassProgressVO> classes = new ArrayList<>();
        if (rows == null) {
            return classes;
        }
        for (DashboardClassRow row : rows) {
            ClassProgressVO vo = new ClassProgressVO();
            vo.setClassCode(row.getClassCode());
            vo.setClassName(TextUtils.isBlank(row.getClassName())
                    ? DashboardConstants.UNASSIGNED_CLASS : row.getClassName());
            vo.setStudentTotal(nvl(row.getStudentTotal()));
            vo.setCompletedStudents(nvl(row.getCompletedStudents()));
            vo.setPendingReview(nvl(row.getSubmittedProfiles()));
            vo.setCompletionRate(rate(vo.getCompletedStudents(), vo.getStudentTotal()));
            vo.setReturnedRate(rate(nvl(row.getReturnedProfiles()), nvl(row.getSubmittedProfilesTotal())));
            vo.setReviewedProfiles(nvl(row.getReviewedProfiles()));
            vo.setAverageReviewHours(averageHours(nvl(row.getReviewSeconds()), vo.getReviewedProfiles()));
            classes.add(vo);
        }
        return classes;
    }

    /** 百分比一位小数；分母为 0 时返回 0.0，避免前端出现 NaN。 */
    private BigDecimal rate(int part, int total) {
        if (total <= 0) {
            return BigDecimal.ZERO.setScale(DashboardConstants.RATE_SCALE);
        }
        return BigDecimal.valueOf(part)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), DashboardConstants.RATE_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 平均审核时长（小时，一位小数）。
     *
     * <p>入参是「秒数之和」与「份数之和」，除法只做一次：先算各班均值再平均会让
     * 一个只有 1 份样本的班和一个 40 份样本的班权重相同，得出谁都不是的数。
     * 样本为 0（或秒数合计非正）时返回 0.0，由前端按样本量显示「—」。</p>
     */
    private BigDecimal averageHours(long reviewSeconds, int reviewedProfiles) {
        if (reviewedProfiles <= 0 || reviewSeconds <= 0) {
            return BigDecimal.ZERO.setScale(DashboardConstants.HOURS_SCALE);
        }
        // 一次除到位：总秒数 / (份数 × 每小时秒数)，先除份数再除 3600 也能算，但两步各自取整会多一次误差
        BigDecimal divisor = BigDecimal.valueOf((long) reviewedProfiles * DashboardConstants.SECONDS_PER_HOUR);
        return BigDecimal.valueOf(reviewSeconds)
                .divide(divisor, DashboardConstants.HOURS_SCALE, RoundingMode.HALF_UP);
    }

    private int nvl(Integer value) {
        return value == null ? 0 : value;
    }

    private long nvl(Long value) {
        return value == null ? 0L : value;
    }
}
