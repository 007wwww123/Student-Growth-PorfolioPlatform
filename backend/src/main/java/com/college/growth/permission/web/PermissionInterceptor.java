package com.college.growth.permission.web;

import com.college.growth.permission.service.PermissionService;
import com.college.growth.permission.support.RequirePermission;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 权限点拦截器：只拦截标了 {@link RequirePermission} 的接口。
 *
 * <p>校验失败抛 {@code EC-40303}，由全局异常处理统一转为 Result，保持与业务异常一致的响应体。</p>
 */
@Component
public class PermissionInterceptor implements HandlerInterceptor {

    private final PermissionService permissionService;

    public PermissionInterceptor(PermissionService permissionService) {
        this.permissionService = permissionService;
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
            permissionService.require(annotation.value());
        }
        return true;
    }
}
