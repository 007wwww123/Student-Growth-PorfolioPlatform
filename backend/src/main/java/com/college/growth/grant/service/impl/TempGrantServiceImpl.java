package com.college.growth.grant.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.college.growth.auth.entity.SysUser;
import com.college.growth.auth.mapper.SysUserMapper;
import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.context.UserContext;
import com.college.growth.common.enums.UserRoleEnum;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.log.service.AuditLogService;
import com.college.growth.common.result.ResultCode;
import com.college.growth.common.util.TextUtils;
import com.college.growth.grant.dto.TempGrantApplyDTO;
import com.college.growth.grant.entity.SysApproval;
import com.college.growth.grant.mapper.SysApprovalMapper;
import com.college.growth.grant.service.TempGrantService;
import com.college.growth.grant.vo.TempGrantVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 临时授权（F-17 / BR-S03）。
 *
 * <p>管理员申请 1–4 小时只读授权，必须由另一名管理员审批；通过后写
 * {@code sys_user.temp_grant_expire_at}，JWT 过滤器按数据库有效期实时判定，
 * 到期/撤销后旧令牌立即失效。审批单同时是 F-13/F-18 的通用基础。</p>
 */
@Service
public class TempGrantServiceImpl implements TempGrantService {

    private static final Logger log = LoggerFactory.getLogger(TempGrantServiceImpl.class);

    private static final String TYPE = "TEMP_GRANT";

    private static final String PENDING = "PENDING";

    private static final String APPROVED = "APPROVED";

    private static final String REJECTED = "REJECTED";

    private static final String EXPIRED = "EXPIRED";

    private static final String REVOKED = "REVOKED";

    private final SysApprovalMapper approvalMapper;

    private final SysUserMapper userMapper;

    private final AuditLogService auditLogService;

    private final ObjectMapper objectMapper;

    public TempGrantServiceImpl(SysApprovalMapper approvalMapper, SysUserMapper userMapper,
                                AuditLogService auditLogService, ObjectMapper objectMapper) {
        this.approvalMapper = approvalMapper;
        this.userMapper = userMapper;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @Override
    public TempGrantVO apply(TempGrantApplyDTO dto) {
        CurrentUser user = requireAdmin();
        sweepExpired();
        long pending = approvalMapper.selectCount(Wrappers.<SysApproval>lambdaQuery()
                .eq(SysApproval::getApprovalType, TYPE)
                .eq(SysApproval::getApplicantId, user.userId())
                .eq(SysApproval::getStatus, PENDING));
        if (pending > 0) {
            throw new BusinessException(ResultCode.STATE_CONFLICT, "已有待审批的临时授权申请");
        }
        if (activeOf(user.userId()) != null) {
            throw new BusinessException(ResultCode.STATE_CONFLICT, "当前已有生效中的临时授权");
        }
        SysApproval approval = new SysApproval();
        approval.setApprovalType(TYPE);
        approval.setApplicantId(user.userId());
        approval.setTargetJson("{\"hours\":" + dto.getHours() + "}");
        approval.setReason(dto.getReason().trim());
        approval.setStatus(PENDING);
        approvalMapper.insert(approval);
        audit(approval, AuditLogService.ACTION_APPLY,
                "申请临时授权 " + dto.getHours() + " 小时：" + approval.getReason());
        return toVO(approval, Map.of());
    }

    @Override
    public List<TempGrantVO> list() {
        requireAdmin();
        sweepExpired();
        List<SysApproval> rows = approvalMapper.selectList(Wrappers.<SysApproval>lambdaQuery()
                .eq(SysApproval::getApprovalType, TYPE)
                .orderByDesc(SysApproval::getId)
                .last("limit 100"));
        return toVOs(rows);
    }

    @Override
    public List<TempGrantVO> active() {
        CurrentUser user = requireAdmin();
        sweepExpired();
        SysApproval active = activeOf(user.userId());
        return active == null ? List.of() : List.of(toVO(active, Map.of()));
    }

    @Override
    @Transactional
    public TempGrantVO approve(Long id, String remark) {
        CurrentUser approver = requireAdmin();
        SysApproval approval = requirePending(id);
        if (approval.getApplicantId().equals(approver.userId())) {
            throw new BusinessException(ResultCode.DATA_SCOPE_DENIED, "不能审批自己的临时授权申请");
        }
        if (activeOf(approval.getApplicantId()) != null) {
            throw new BusinessException(ResultCode.STATE_CONFLICT, "申请人已有生效中的临时授权");
        }
        int hours = hoursOf(approval);
        LocalDateTime expireAt = LocalDateTime.now().plusHours(hours);
        approval.setStatus(APPROVED);
        approval.setApproverId(approver.userId());
        approval.setApproverRemark(TextUtils.isBlank(remark) ? null : remark.trim());
        approval.setApprovedAt(LocalDateTime.now());
        approval.setExpireAt(expireAt);
        approvalMapper.updateById(approval);
        userMapper.update(null, Wrappers.<SysUser>lambdaUpdate()
                .eq(SysUser::getId, approval.getApplicantId())
                .set(SysUser::getTempGrant, 1)
                .set(SysUser::getTempGrantExpireAt, expireAt));
        audit(approval, AuditLogService.ACTION_APPROVE,
                "通过临时授权 " + hours + " 小时，到期 " + expireAt);
        return toVO(approval, Map.of());
    }

    @Override
    @Transactional
    public TempGrantVO reject(Long id, String remark) {
        CurrentUser approver = requireAdmin();
        if (TextUtils.isBlank(remark)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "拒绝时必须填写审批意见");
        }
        SysApproval approval = requirePending(id);
        if (approval.getApplicantId().equals(approver.userId())) {
            throw new BusinessException(ResultCode.DATA_SCOPE_DENIED, "不能审批自己的临时授权申请");
        }
        approval.setStatus(REJECTED);
        approval.setApproverId(approver.userId());
        approval.setApproverRemark(remark.trim());
        approval.setApprovedAt(LocalDateTime.now());
        approvalMapper.updateById(approval);
        audit(approval, AuditLogService.ACTION_REJECT, "拒绝临时授权：" + remark.trim());
        return toVO(approval, Map.of());
    }

    @Override
    @Transactional
    public TempGrantVO revoke(Long id) {
        CurrentUser user = requireAdmin();
        SysApproval approval = findOrThrow(id);
        boolean applicant = approval.getApplicantId().equals(user.userId());
        boolean approver = user.userId().equals(approval.getApproverId());
        if (!applicant && !approver) {
            throw new BusinessException(ResultCode.DATA_SCOPE_DENIED, "只能撤销本人申请或本人审批的授权");
        }
        if (REJECTED.equals(approval.getStatus()) || REVOKED.equals(approval.getStatus())
                || EXPIRED.equals(approval.getStatus())) {
            throw new BusinessException(ResultCode.STATE_CONFLICT, "当前状态不能撤销");
        }
        boolean wasApproved = APPROVED.equals(approval.getStatus());
        approval.setStatus(REVOKED);
        approvalMapper.updateById(approval);
        if (wasApproved) {
            clearUserGrant(approval.getApplicantId(), approval.getId());
        }
        audit(approval, AuditLogService.ACTION_REVOKE, "撤销临时授权");
        return toVO(approval, Map.of());
    }

    // ------------------------------------------------------------------

    private void sweepExpired() {
        List<SysApproval> expired = approvalMapper.selectList(Wrappers.<SysApproval>lambdaQuery()
                .eq(SysApproval::getApprovalType, TYPE)
                .eq(SysApproval::getStatus, APPROVED)
                .le(SysApproval::getExpireAt, LocalDateTime.now()));
        for (SysApproval approval : expired) {
            approval.setStatus(EXPIRED);
            approvalMapper.updateById(approval);
            clearUserGrant(approval.getApplicantId(), approval.getId());
            audit(approval, AuditLogService.ACTION_EXPIRE, "临时授权到期自动失效");
            log.info("临时授权到期 applicant={} approval={}", approval.getApplicantId(), approval.getId());
        }
    }

    private SysApproval activeOf(Long userId) {
        return approvalMapper.selectOne(Wrappers.<SysApproval>lambdaQuery()
                .eq(SysApproval::getApprovalType, TYPE)
                .eq(SysApproval::getApplicantId, userId)
                .eq(SysApproval::getStatus, APPROVED)
                .gt(SysApproval::getExpireAt, LocalDateTime.now())
                .orderByDesc(SysApproval::getId)
                .last("limit 1"));
    }

    private void clearUserGrant(Long userId, Long exceptApprovalId) {
        long other = approvalMapper.selectCount(Wrappers.<SysApproval>lambdaQuery()
                .eq(SysApproval::getApprovalType, TYPE)
                .eq(SysApproval::getApplicantId, userId)
                .eq(SysApproval::getStatus, APPROVED)
                .gt(SysApproval::getExpireAt, LocalDateTime.now())
                .ne(SysApproval::getId, exceptApprovalId));
        if (other == 0) {
            userMapper.update(null, Wrappers.<SysUser>lambdaUpdate()
                    .eq(SysUser::getId, userId)
                    .set(SysUser::getTempGrant, 0)
                    .set(SysUser::getTempGrantExpireAt, null));
        }
    }

    private CurrentUser requireAdmin() {
        CurrentUser user = UserContext.get();
        if (user == null || user.role() != UserRoleEnum.ADMIN) {
            throw new BusinessException(ResultCode.PERMISSION_DENIED, "仅管理员可申请/审批临时授权");
        }
        return user;
    }

    private SysApproval requirePending(Long id) {
        SysApproval approval = findOrThrow(id);
        if (!PENDING.equals(approval.getStatus())) {
            throw new BusinessException(ResultCode.STATE_CONFLICT, "申请已处理，不能重复审批");
        }
        return approval;
    }

    private SysApproval findOrThrow(Long id) {
        SysApproval approval = id == null ? null : approvalMapper.selectById(id);
        if (approval == null || !TYPE.equals(approval.getApprovalType())) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "审批单不存在：" + id);
        }
        return approval;
    }

    private int hoursOf(SysApproval approval) {
        try {
            JsonNode node = objectMapper.readTree(approval.getTargetJson());
            int hours = node.path("hours").asInt(1);
            return Math.max(1, Math.min(4, hours));
        } catch (Exception ex) {
            return 1;
        }
    }

    private List<TempGrantVO> toVOs(List<SysApproval> rows) {
        Set<Long> ids = rows.stream()
                .flatMap(row -> java.util.stream.Stream.of(row.getApplicantId(), row.getApproverId()))
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> names = ids.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(ids).stream().collect(Collectors.toMap(
                        SysUser::getId, user -> user.getDisplayName() == null ? user.getUsername() : user.getDisplayName()));
        List<TempGrantVO> result = new ArrayList<>(rows.size());
        for (SysApproval row : rows) {
            result.add(toVO(row, names));
        }
        return result;
    }

    private TempGrantVO toVO(SysApproval approval, Map<Long, String> names) {
        TempGrantVO vo = new TempGrantVO();
        vo.setId(approval.getId());
        vo.setApplicantId(approval.getApplicantId());
        vo.setApplicantName(names.getOrDefault(approval.getApplicantId(), null));
        vo.setHours(hoursOf(approval));
        vo.setReason(approval.getReason());
        vo.setStatus(approval.getStatus());
        vo.setApproverId(approval.getApproverId());
        vo.setApproverName(approval.getApproverId() == null ? null : names.get(approval.getApproverId()));
        vo.setApproverRemark(approval.getApproverRemark());
        vo.setApprovedAt(approval.getApprovedAt());
        vo.setExpireAt(approval.getExpireAt());
        vo.setCreatedAt(approval.getCreatedAt());
        vo.setActive(APPROVED.equals(approval.getStatus())
                && approval.getExpireAt() != null
                && approval.getExpireAt().isAfter(LocalDateTime.now()));
        return vo;
    }

    private void audit(SysApproval approval, String action, String detail) {
        auditLogService.record(AuditLogService.BIZ_GRANT, AuditLogService.TARGET_APPROVAL,
                approval.getId(), null, null, null, action, detail);
    }
}
