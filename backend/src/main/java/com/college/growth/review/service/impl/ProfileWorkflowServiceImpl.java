package com.college.growth.review.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.college.growth.common.constant.CommonConstants;
import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.context.UserContext;
import com.college.growth.common.enums.ProfileStatusEnum;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.log.service.AuditLogService;
import com.college.growth.common.log.service.ChangeLogService;
import com.college.growth.common.org.service.StudentClassService;
import com.college.growth.common.profile.entity.ArcProfile;
import com.college.growth.common.profile.mapper.ArcProfileMapper;
import com.college.growth.common.profile.service.ProfileCompletenessService;
import com.college.growth.common.result.FieldErrorDetail;
import com.college.growth.common.result.ResultCode;
import com.college.growth.common.util.TextUtils;
import com.college.growth.review.common.ReviewConstants;
import com.college.growth.review.service.ProfileWorkflowService;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 公共档案状态机实现。
 *
 * <p>三类角色在这里被区分对待：学生只能提交与撤回本人的板块，辅导员只能审核本班板块，
 * 管理员不参与业务审核（BR-S03）。数据范围判定统一走 {@link StudentClassService}，
 * 不在这里重复读 {@code basic.classId}。</p>
 */
@Service
public class ProfileWorkflowServiceImpl implements ProfileWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(ProfileWorkflowServiceImpl.class);

    private final ArcProfileMapper profileMapper;

    private final ChangeLogService changeLogService;

    private final AuditLogService auditLogService;

    private final StudentClassService studentClassService;

    private final ProfileCompletenessService completenessService;

    public ProfileWorkflowServiceImpl(ArcProfileMapper profileMapper,
                                      ChangeLogService changeLogService,
                                      AuditLogService auditLogService,
                                      StudentClassService studentClassService,
                                      ProfileCompletenessService completenessService) {
        this.profileMapper = profileMapper;
        this.changeLogService = changeLogService;
        this.auditLogService = auditLogService;
        this.studentClassService = studentClassService;
        this.completenessService = completenessService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(ArcProfile profile, String reason) {
        CurrentUser user = UserContext.get();
        if (!user.isStudent()) {
            throw new BusinessException(ResultCode.DATA_SCOPE_DENIED, "只有学生本人可以提交本人板块");
        }
        ProfileStatusEnum from = statusOf(profile);
        if (!from.isWritable()) {
            throw new BusinessException(ResultCode.STATE_CONFLICT,
                    "当前状态为「" + from.getLabel() + "」，不能提交");
        }

        profile.setStatus(ProfileStatusEnum.SUBMITTED.name());
        profile.setSubmitCount(nvl(profile.getSubmitCount()) + 1);
        profile.setSubmittedAt(LocalDateTime.now());
        if (profile.getWithdrawCount() == null) {
            profile.setWithdrawCount(0);
        }
        refreshCompleteness(profile);
        updateProfile(profile);

        String note = TextUtils.isBlank(reason) ? "学生提交" : reason.trim();
        changeLogService.record(profile, ChangeLogService.BIZ_STATUS_CHANGE, null, null, null,
                from.name(), ProfileStatusEnum.SUBMITTED.name(), note);
        auditLogService.record(AuditLogService.BIZ_STATUS_CHANGE, AuditLogService.TARGET_PROFILE,
                profile.getId(), profile.getStudentId(), profile.getSectionCode(), null,
                ReviewConstants.ACTION_SUBMIT, note);
        refreshCompleted(profile.getStudentId(), profile.getAcademicYear());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void withdraw(ArcProfile profile) {
        CurrentUser user = UserContext.get();
        // BR-S06：撤回仅限学生本人，辅导员与管理员都不能替学生撤回。
        if (!user.isStudent() || !profile.getStudentId().equals(user.userId())) {
            throw new BusinessException(ResultCode.DATA_SCOPE_DENIED, "只有学生本人可以撤回本人板块");
        }
        ProfileStatusEnum from = statusOf(profile);
        if (from != ProfileStatusEnum.SUBMITTED) {
            throw new BusinessException(ResultCode.STATE_CONFLICT,
                    "当前状态为「" + from.getLabel() + "」，只有已提交的板块可以撤回");
        }
        int used = nvl(profile.getWithdrawCount());
        if (used >= 1) {
            throw new BusinessException(ResultCode.WITHDRAW_EXCEEDED);
        }

        profile.setStatus(ProfileStatusEnum.DRAFT.name());
        profile.setWithdrawCount(used + 1);
        refreshCompleteness(profile);
        updateProfile(profile);

        changeLogService.record(profile, ChangeLogService.BIZ_STATUS_CHANGE, null, null, null,
                from.name(), ProfileStatusEnum.DRAFT.name(), "学生撤回");
        auditLogService.record(AuditLogService.BIZ_STATUS_CHANGE, AuditLogService.TARGET_PROFILE,
                profile.getId(), profile.getStudentId(), profile.getSectionCode(), null,
                ReviewConstants.ACTION_WITHDRAW, "学生撤回，剩余撤回次数 0");
        refreshCompleted(profile.getStudentId(), profile.getAcademicYear());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(ArcProfile profile, String comment) {
        requireCounselor(profile);
        ProfileStatusEnum from = statusOf(profile);
        if (from != ProfileStatusEnum.SUBMITTED) {
            throw new BusinessException(ResultCode.STATE_CONFLICT,
                    "当前状态为「" + from.getLabel() + "」，只有已提交的板块可以审核");
        }
        completenessService.requireReviewReady(profile);
        String note = normalizeComment(comment);

        CurrentUser user = UserContext.get();
        profile.setStatus(ProfileStatusEnum.APPROVED.name());
        profile.setReviewerId(user.userId());
        profile.setReviewedAt(LocalDateTime.now());
        refreshCompleteness(profile);
        updateProfile(profile);

        changeLogService.record(profile, ChangeLogService.BIZ_REVIEW, null, null, null,
                from.name(), ProfileStatusEnum.APPROVED.name(),
                TextUtils.isBlank(note) ? "审核通过" : note);
        auditLogService.record(AuditLogService.BIZ_REVIEW, AuditLogService.TARGET_PROFILE,
                profile.getId(), profile.getStudentId(), profile.getSectionCode(), null,
                ReviewConstants.ACTION_APPROVE, note);
        refreshCompleted(profile.getStudentId(), profile.getAcademicYear());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(ArcProfile profile, String reason) {
        requireCounselor(profile);
        ProfileStatusEnum from = statusOf(profile);
        if (from != ProfileStatusEnum.SUBMITTED) {
            throw new BusinessException(ResultCode.STATE_CONFLICT,
                    "当前状态为「" + from.getLabel() + "」，只有已提交的板块可以退回");
        }
        String note = reason == null ? "" : reason.trim();
        // BR-S04：理由必填且不少于 10 字，理由对学生可见，因此不能截断后入库。
        if (note.length() < ReviewConstants.REASON_MIN_LENGTH) {
            throw new BusinessException(ResultCode.RETURN_REASON_TOO_SHORT,
                    "退回理由不少于 " + ReviewConstants.REASON_MIN_LENGTH + " 字",
                    List.of(new FieldErrorDetail("reason", "TOO_SHORT",
                            "退回理由不少于 " + ReviewConstants.REASON_MIN_LENGTH + " 字")));
        }
        if (note.length() > ReviewConstants.COMMENT_MAX_LENGTH) {
            throw new BusinessException(ResultCode.PARAM_INVALID,
                    "退回理由不能超过 " + ReviewConstants.COMMENT_MAX_LENGTH + " 字");
        }

        CurrentUser user = UserContext.get();
        profile.setStatus(ProfileStatusEnum.RETURNED.name());
        profile.setReviewerId(user.userId());
        profile.setReviewedAt(LocalDateTime.now());
        refreshCompleteness(profile);
        updateProfile(profile);

        changeLogService.record(profile, ChangeLogService.BIZ_REVIEW, null, null, null,
                from.name(), ProfileStatusEnum.RETURNED.name(), note);
        auditLogService.record(AuditLogService.BIZ_REVIEW, AuditLogService.TARGET_PROFILE,
                profile.getId(), profile.getStudentId(), profile.getSectionCode(), null,
                ReviewConstants.ACTION_REJECT, note);
        refreshCompleted(profile.getStudentId(), profile.getAcademicYear());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refreshCompleted(Long studentId, String academicYear) {
        List<ArcProfile> profiles = profileMapper.selectList(Wrappers.<ArcProfile>lambdaQuery()
                .eq(ArcProfile::getStudentId, studentId)
                .eq(ArcProfile::getAcademicYear, academicYear));
        boolean completed = profiles.size() >= CommonConstants.SECTION_COUNT
                && profiles.stream()
                .allMatch(item -> ProfileStatusEnum.APPROVED.name().equals(item.getStatus()));
        // 派生标记不参与乐观锁：它不是用户内容，不因并发查看而冲突。
        profileMapper.update(null, Wrappers.<ArcProfile>lambdaUpdate()
                .eq(ArcProfile::getStudentId, studentId)
                .eq(ArcProfile::getAcademicYear, academicYear)
                .set(ArcProfile::getCompleted, completed));
        log.debug("重算完成标记 studentId={} year={} completed={}", studentId, academicYear, completed);
    }

    /** BR-S03：只有该班现任辅导员可以审核；管理员没有业务审核权。 */
    private void requireCounselor(ArcProfile profile) {
        CurrentUser user = UserContext.get();
        boolean allowed = user.isCounselor()
                && studentClassService.isCounselorOf(profile.getStudentId(), profile.getAcademicYear(), user.userId());
        if (!allowed) {
            throw new BusinessException(ResultCode.DATA_SCOPE_DENIED, "仅该班现任辅导员可以审核");
        }
    }

    /**
     * 状态流转前重算完成度。
     *
     * <p>完成度不只在保存时变化：提交、撤回、审核之后都可能与库里的值不一致
     * （典型场景是学工系统导入字段值，不经过保存接口）。待审列表直接展示该字段，
     * 过期值会误导辅导员，因此四个流转节点统一刷新。值随本次 updateProfile 一起写库，
     * 不额外产生一次 UPDATE 与版本号自增。</p>
     */
    private void refreshCompleteness(ArcProfile profile) {
        profile.setCompleteness(completenessService.compute(profile));
    }

    /**
     * 更新板块实例并处理乐观锁冲突。
     *
     * <p>更新语句由 @Version 自动带 {@code version = 旧值}；影响行数为 0 说明在读与写之间
     * 有人改过（典型场景：学生保存的同时辅导员审核），此时必须让调用方刷新重试。
     */
    private void updateProfile(ArcProfile profile) {
        int rows = profileMapper.updateById(profile);
        if (rows == 0) {
            throw new BusinessException(ResultCode.VERSION_CONFLICT);
        }
    }

    private ProfileStatusEnum statusOf(ArcProfile profile) {
        String status = profile.getStatus();
        if (status == null) {
            throw new BusinessException(ResultCode.STATE_CONFLICT, "板块状态异常，请联系管理员");
        }
        try {
            return ProfileStatusEnum.valueOf(status);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ResultCode.STATE_CONFLICT, "板块状态异常，请联系管理员");
        }
    }

    private String normalizeComment(String comment) {
        if (TextUtils.isBlank(comment)) {
            return null;
        }
        String trimmed = comment.trim();
        if (trimmed.length() > ReviewConstants.COMMENT_MAX_LENGTH) {
            throw new BusinessException(ResultCode.PARAM_INVALID,
                    "审核意见不能超过 " + ReviewConstants.COMMENT_MAX_LENGTH + " 字");
        }
        return trimmed;
    }

    private int nvl(Integer value) {
        return value == null ? 0 : value;
    }
}
