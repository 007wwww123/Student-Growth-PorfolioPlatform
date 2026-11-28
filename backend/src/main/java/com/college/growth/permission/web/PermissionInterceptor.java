package com.college.growth.permission.web;

import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.log.service.AuditLogService;
import com.college.growth.permission.service.PermissionService;
import com.college.growth.permission.support.RequirePermission;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 权限点拦截器：只拦截标了 {@link RequirePermission} 的接口。
 *
 * <p>校验失败抛 {@code EC-40303}，由全局异常处理统一转为 Result；同时写一条
 * {@code SECURITY/DENY} 审计，满足 7.4「越权尝试」事件要求。</p>
 */
@Component
public class PermissionInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(PermissionInterceptor.class);

    private final PermissionService permissionService;

    private final AuditLogService auditLogService;

    public PermissionInterceptor(PermissionService permissionService, AuditLogService auditLogService) {
        this.permissionService = permissionService;
        this.auditLogService = auditLogService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        RequirePermission annotation = method.getMethodAnnotation(RequirePermission.class);
        if (annotation == null) {
            annotation = method.getBeanType().getAnnotation(RequirePermission.class);
        }
        if (annotation != null) {
            try {
                permissionService.require(annotation.value());
            } catch (BusinessException ex) {
                auditDeny(request, annotation.value(), ex);
                throw ex;
            }
        }
        return true;
    }

    private void auditDeny(HttpServletRequest request, String code, BusinessException ex) {
        try {
            auditLogService.record(AuditLogService.BIZ_SECURITY, AuditLogService.TARGET_API, null,
                    null, null, null, AuditLogService.ACTION_DENY,
                    "越权尝试：" + request.getMethod() + " " + request.getRequestURI()
                            + "，缺少权限 " + code + "（" + ex.getResultCode().getCode() + "）");
        } catch (Exception auditError) {
            log.warn("写越权审计失败 code={}", code, auditError);
        }
    }
}
