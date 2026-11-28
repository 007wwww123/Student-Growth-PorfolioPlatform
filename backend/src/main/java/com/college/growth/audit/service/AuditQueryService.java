package com.college.growth.audit.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.college.growth.audit.entity.SysLoginLog;
import com.college.growth.audit.mapper.SysLoginLogMapper;
import com.college.growth.audit.vo.AuditLogVO;
import com.college.growth.audit.vo.LoginLogVO;
import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.context.UserContext;
import com.college.growth.common.enums.UserRoleEnum;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.log.entity.SysAuditLog;
import com.college.growth.common.log.mapper.SysAuditLogMapper;
import com.college.growth.common.result.PageResult;
import com.college.growth.common.result.ResultCode;
import com.college.growth.common.util.TextUtils;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 审计与登录日志查询（F-14 / AC-050–052）。
 *
 * <p>管理员可查全部；辅导员/学生只能查「本人操作」的审计记录（7.2 audit:log:read 的 SELF 范围）。
 * 登录日志只给管理员。</p>
 */
@Service
public class AuditQueryService {

    private final SysAuditLogMapper auditLogMapper;

    private final SysLoginLogMapper loginLogMapper;

    public AuditQueryService(SysAuditLogMapper auditLogMapper, SysLoginLogMapper loginLogMapper) {
        this.auditLogMapper = auditLogMapper;
        this.loginLogMapper = loginLogMapper;
    }

    public PageResult<AuditLogVO> logs(String bizType, String action, Long studentId, Long operatorId,
                                       LocalDate from, LocalDate to, long pageNum, long pageSize) {
        CurrentUser user = UserContext.get();
        if (user.userId() == null || user.userId() <= 0) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        Long scopedOperator = user.role() == UserRoleEnum.ADMIN ? operatorId : user.userId();
        IPage<SysAuditLog> page = auditLogMapper.selectPage(page(pageNum, pageSize),
                Wrappers.<SysAuditLog>lambdaQuery()
                        .eq(!TextUtils.isBlank(bizType), SysAuditLog::getBizType, normalize(bizType))
                        .eq(!TextUtils.isBlank(action), SysAuditLog::getAction, normalize(action))
                        .eq(studentId != null, SysAuditLog::getStudentId, studentId)
                        .eq(scopedOperator != null, SysAuditLog::getOperatorId, scopedOperator)
                        .ge(from != null, SysAuditLog::getCreatedAt, from == null ? null : from.atStartOfDay())
                        .lt(to != null, SysAuditLog::getCreatedAt, to == null ? null : to.plusDays(1).atStartOfDay())
                        .orderByDesc(SysAuditLog::getId));
        List<AuditLogVO> rows = page.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        return new PageResult<>(rows, page.getTotal(), page.getCurrent(), page.getSize());
    }

    public PageResult<LoginLogVO> loginLogs(String username, Integer success, LocalDate from, LocalDate to,
                                            long pageNum, long pageSize) {
        CurrentUser user = UserContext.get();
        if (user.role() != UserRoleEnum.ADMIN) {
            throw new BusinessException(ResultCode.PERMISSION_DENIED, "仅管理员可以查看登录日志");
        }
        IPage<SysLoginLog> page = loginLogMapper.selectPage(page(pageNum, pageSize),
                Wrappers.<SysLoginLog>lambdaQuery()
                        .like(!TextUtils.isBlank(username), SysLoginLog::getUsername,
                                username == null ? null : username.trim())
                        .eq(success != null, SysLoginLog::getSuccess, success)
                        .ge(from != null, SysLoginLog::getCreatedAt, from == null ? null : from.atStartOfDay())
                        .lt(to != null, SysLoginLog::getCreatedAt, to == null ? null : to.plusDays(1).atStartOfDay())
                        .orderByDesc(SysLoginLog::getId));
        List<LoginLogVO> rows = page.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        return new PageResult<>(rows, page.getTotal(), page.getCurrent(), page.getSize());
    }

    private <T> Page<T> page(long pageNum, long pageSize) {
        return new Page<>(Math.max(1, pageNum), Math.min(Math.max(1, pageSize), 100));
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }

    private AuditLogVO toVO(SysAuditLog entity) {
        AuditLogVO vo = new AuditLogVO();
        vo.setId(entity.getId());
        vo.setBizType(entity.getBizType());
        vo.setTargetType(entity.getTargetType());
        vo.setTargetId(entity.getTargetId());
        vo.setStudentId(entity.getStudentId());
        vo.setSectionCode(entity.getSectionCode());
        vo.setFieldCode(entity.getFieldCode());
        vo.setAction(entity.getAction());
        vo.setDetail(entity.getDetail());
        vo.setOperatorId(entity.getOperatorId());
        vo.setOperatorRole(entity.getOperatorRole());
        vo.setTraceId(entity.getTraceId());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }

    private LoginLogVO toVO(SysLoginLog entity) {
        LoginLogVO vo = new LoginLogVO();
        vo.setId(entity.getId());
        vo.setUserId(entity.getUserId());
        vo.setUsername(entity.getUsername());
        vo.setSuccess(entity.getSuccess());
        vo.setReason(entity.getReason());
        vo.setIp(entity.getIp());
        vo.setUserAgent(entity.getUserAgent());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }
}
