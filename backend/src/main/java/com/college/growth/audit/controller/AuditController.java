package com.college.growth.audit.controller;

import com.college.growth.audit.service.AuditQueryService;
import com.college.growth.audit.vo.AuditLogVO;
import com.college.growth.audit.vo.LoginLogVO;
import com.college.growth.common.result.PageResult;
import com.college.growth.common.result.Result;
import com.college.growth.permission.support.RequirePermission;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审计与登录日志查询接口（F-14 / AC-050–052）。
 *
 * <p>管理员查全部；辅导员/学生只查本人操作的审计记录。登录日志仅管理员。</p>
 */
@RestController
@RequestMapping("/audit")
public class AuditController {

    private final AuditQueryService auditQueryService;

    public AuditController(AuditQueryService auditQueryService) {
        this.auditQueryService = auditQueryService;
    }

    @GetMapping("/logs")
    @RequirePermission("audit:log:read")
    public Result<PageResult<AuditLogVO>> logs(
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return Result.success(auditQueryService.logs(bizType, action, studentId, operatorId,
                from, to, pageNum, pageSize));
    }

    @GetMapping("/login-logs")
    @RequirePermission("audit:login:read")
    public Result<PageResult<LoginLogVO>> loginLogs(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Integer success,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return Result.success(auditQueryService.loginLogs(username, success, from, to, pageNum, pageSize));
    }
}
