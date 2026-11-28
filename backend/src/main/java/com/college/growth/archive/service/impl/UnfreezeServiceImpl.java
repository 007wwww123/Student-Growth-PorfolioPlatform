package com.college.growth.archive.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.college.growth.archive.service.UnfreezeService;
import com.college.growth.archive.vo.UnfreezeVO;
import com.college.growth.auth.entity.SysUser;
import com.college.growth.auth.mapper.SysUserMapper;
import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.context.UserContext;
import com.college.growth.common.enums.ProfileStatusEnum;
import com.college.growth.common.enums.UserRoleEnum;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.log.service.AuditLogService;
import com.college.growth.common.log.service.ChangeLogService;
import com.college.growth.common.org.service.StudentClassService;
import com.college.growth.common.profile.entity.ArcProfile;
import com.college.growth.common.profile.mapper.ArcProfileMapper;
import com.college.growth.common.result.ResultCode;
import com.college.growth.common.student.entity.StuStudent;
import com.college.growth.common.student.mapper.StuStudentMapper;
import com.college.growth.common.util.AcademicYearUtils;
import com.college.growth.common.util.TextUtils;
import com.college.growth.grant.entity.SysApproval;
import com.college.growth.grant.mapper.SysApprovalMapper;
import com.college.growth.notification.service.NotificationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 归档解冻（B-6）：学生六板块 LOCKED 后，COU 申请、ADMIN 审批，
 * 通过后回到 APPROVED 以便更正；全程写变更/审计，并通知学生。
 */
@Service
public class UnfreezeServiceImpl implements UnfreezeService {

    private static final String TYPE = "UNFREEZE";

    private static final String PENDING = "PENDING";

    private static final String APPROVED = "APPROVED";

    private static final String REJECTED = "REJECTED";

    private final SysApprovalMapper approvalMapper;

    private final ArcProfileMapper profileMapper;

    private final StuStudentMapper studentMapper;

    private final SysUserMapper userMapper;

    private final StudentClassService studentClassService;

    private final ChangeLogService changeLogService;

    private final AuditLogService auditLogService;

    private final NotificationService notificationService;

    private final ObjectMapper objectMapper;

    public UnfreezeServiceImpl(SysApprovalMapper approvalMapper, ArcProfileMapper profileMapper,
                               StuStudentMapper studentMapper, SysUserMapper userMapper,
                               StudentClassService studentClassService, ChangeLogService changeLogService,
                               AuditLogService auditLogService, NotificationService notificationService,
                               ObjectMapper objectMapper) {
        this.approvalMapper = approvalMapper;
        this.profileMapper = profileMapper;
        this.studentMapper = studentMapper;
        this.userMapper = userMapper;
        this.studentClassService = studentClassService;
        this.changeLogService = changeLogService;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    @Override
    public UnfreezeVO apply(Long studentId, String academicYear, String reason) {
        CurrentUser user = UserContext.get();
        if (user.role() != UserRoleEnum.COU) {
            throw new BusinessException(ResultCode.PERMISSION_DENIED, "仅辅导员可申请归档解冻");
        }
        String year = AcademicYearUtils.resolve(academicYear);
        studentClassService.requireStudentScope(studentId, year, user);
        if (TextUtils.isBlank(reason) || reason.trim().length() < 10) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "解冻原因不少于 10 字");
        }
        List<ArcProfile> profiles = profileMapper.selectList(Wrappers.<ArcProfile>lambdaQuery()
                .eq(ArcProfile::getStudentId, studentId)
                .eq(ArcProfile::getAcademicYear, year));
        if (profiles.size() != 6
                || profiles.stream().anyMatch(profile -> !ProfileStatusEnum.LOCKED.name().equals(profile.getStatus()))) {
            throw new BusinessException(ResultCode.STATE_CONFLICT, "只有六板块全部归档的学生才能申请解冻");
        }
        long pending = approvalMapper.selectCount(Wrappers.<SysApproval>lambdaQuery()
                .eq(SysApproval::getApprovalType, TYPE)
                .eq(SysApproval::getStudentId, studentId)
                .eq(SysApproval::getStatus, PENDING));
        if (pending > 0) {
            throw new BusinessException(ResultCode.STATE_CONFLICT, "该学生已有待审批的解冻申请");
        }
        SysApproval approval = new SysApproval();
        approval.setApprovalType(TYPE);
        approval.setApplicantId(user.userId());
        approval.setStudentId(studentId);
        approval.setTargetJson("{\"year\":\"" + year + "\"}");
        approval.setReason(reason.trim());
        approval.setStatus(PENDING);
        approvalMapper.insert(approval);
        audit(approval, "APPLY", "申请归档解冻：" + reason.trim());
        return toVO(approval);
    }

    @Override
    public List<UnfreezeVO> list() {
        CurrentUser user = UserContext.get();
        List<SysApproval> rows = approvalMapper.selectList(Wrappers.<SysApproval>lambdaQuery()
                .eq(SysApproval::getApprovalType, TYPE)
                .eq(user.role() == UserRoleEnum.COU, SysApproval::getApplicantId, user.userId())
                .orderByDesc(SysApproval::getId)
                .last("limit 100"));
        return rows.stream().map(this::toVO).toList();
    }

    @Override
    @Transactional
    public UnfreezeVO approve(Long id, String remark) {
        CurrentUser approver = requireAdmin();
        SysApproval approval = requirePending(id);
        String year = yearOf(approval);
        approval.setStatus(APPROVED);
        approval.setApproverId(approver.userId());
        approval.setApproverRemark(TextUtils.isBlank(remark) ? null : remark.trim());
        approval.setApprovedAt(LocalDateTime.now());
        approvalMapper.updateById(approval);
        List<ArcProfile> profiles = profileMapper.selectList(Wrappers.<ArcProfile>lambdaQuery()
                .eq(ArcProfile::getStudentId, approval.getStudentId())
                .eq(ArcProfile::getAcademicYear, year));
        for (ArcProfile profile : profiles) {
            if (!ProfileStatusEnum.LOCKED.name().equals(profile.getStatus())) {
                continue;
            }
            profile.setStatus(ProfileStatusEnum.APPROVED.name());
            profile.setReviewerId(approver.userId());
            profile.setReviewedAt(LocalDateTime.now());
            profile.setCompleted(false);
            profileMapper.updateById(profile);
            changeLogService.record(profile, ChangeLogService.BIZ_STATUS_CHANGE, null, null, null,
                    ProfileStatusEnum.LOCKED.name(), ProfileStatusEnum.APPROVED.name(),
                    "归档解冻：" + approval.getReason());
        }
        audit(approval, "APPROVE", "归档解冻通过，六板块回到已通过");
        notificationService.notify(approval.getStudentId(), "UNFREEZE", "档案已解冻",
                "归档已解冻，可继续更正；完成后需重新归档", "APPROVAL", approval.getId());
        return toVO(approval);
    }

    @Override
    @Transactional
    public UnfreezeVO reject(Long id, String remark) {
        requireAdmin();
        if (TextUtils.isBlank(remark)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "拒绝时必须填写意见");
        }
        SysApproval approval = requirePending(id);
        approval.setStatus(REJECTED);
        approval.setApproverId(UserContext.get().userId());
        approval.setApproverRemark(remark.trim());
        approval.setApprovedAt(LocalDateTime.now());
        approvalMapper.updateById(approval);
        audit(approval, "REJECT", "归档解冻拒绝：" + remark.trim());
        return toVO(approval);
    }

    private CurrentUser requireAdmin() {
        CurrentUser user = UserContext.get();
        if (user.role() != UserRoleEnum.ADMIN) {
            throw new BusinessException(ResultCode.PERMISSION_DENIED, "仅管理员可审批归档解冻");
        }
        return user;
    }

    private SysApproval requirePending(Long id) {
        SysApproval approval = id == null ? null : approvalMapper.selectById(id);
        if (approval == null || !TYPE.equals(approval.getApprovalType())) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "解冻申请不存在：" + id);
        }
        if (!PENDING.equals(approval.getStatus())) {
            throw new BusinessException(ResultCode.STATE_CONFLICT, "申请已处理");
        }
        return approval;
    }

    private String yearOf(SysApproval approval) {
        try {
            JsonNode node = objectMapper.readTree(approval.getTargetJson());
            return node.path("year").asText(AcademicYearUtils.current());
        } catch (Exception ex) {
            return AcademicYearUtils.current();
        }
    }

    private UnfreezeVO toVO(SysApproval approval) {
        UnfreezeVO vo = new UnfreezeVO();
        vo.setId(approval.getId());
        vo.setStudentId(approval.getStudentId());
        vo.setAcademicYear(yearOf(approval));
        vo.setApplicantId(approval.getApplicantId());
        vo.setReason(approval.getReason());
        vo.setStatus(approval.getStatus());
        vo.setApproverId(approval.getApproverId());
        vo.setApproverRemark(approval.getApproverRemark());
        vo.setApprovedAt(approval.getApprovedAt());
        vo.setCreatedAt(approval.getCreatedAt());
        Set<Long> ids = java.util.stream.Stream.of(approval.getApplicantId(), approval.getApproverId(),
                        approval.getStudentId())
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        if (!ids.isEmpty()) {
            Map<Long, SysUser> users = userMapper.selectBatchIds(ids).stream()
                    .collect(Collectors.toMap(SysUser::getId, user -> user, (a, b) -> a));
            vo.setApplicantName(users.containsKey(approval.getApplicantId())
                    ? users.get(approval.getApplicantId()).getDisplayName() : null);
            vo.setApproverName(approval.getApproverId() == null ? null
                    : users.containsKey(approval.getApproverId())
                    ? users.get(approval.getApproverId()).getDisplayName() : null);
            StuStudent student = studentMapper.selectById(approval.getStudentId());
            vo.setStudentName(student == null ? null : student.getName());
        }
        return vo;
    }

    private void audit(SysApproval approval, String action, String detail) {
        auditLogService.record("ARCHIVE", AuditLogService.TARGET_APPROVAL, approval.getId(),
                approval.getStudentId(), null, null, action, detail);
    }
}
