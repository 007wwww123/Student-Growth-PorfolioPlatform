package com.college.growth.common.web;

import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.context.UserContext;
import com.college.growth.common.enums.UserRoleEnum;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 请求用户上下文过滤器（骨架期占位实现）。
 *
 * <p>从请求头读取 {@code X-User-Id} / {@code X-User-Role} / {@code X-Temp-Grant}，
 * 写入 {@link UserContext}，请求结束后清理。这只是让字段级权限可以先跑起来，
 * <b>不能当作鉴权</b>：任何调用方都能伪造请求头。接入统一认证后，本类改为从会话读取用户，
 * 并删除对请求头的信任。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class UserContextFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(UserContextFilter.class);

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_ROLE = "X-User-Role";
    private static final String HEADER_TEMP_GRANT = "X-Temp-Grant";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        UserContext.set(resolve(request));
        try {
            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }

    private CurrentUser resolve(HttpServletRequest request) {
        long userId = parseUserId(request.getHeader(HEADER_USER_ID));
        UserRoleEnum role = UserRoleEnum.fromHeader(request.getHeader(HEADER_USER_ROLE));
        boolean tempGrant = Boolean.parseBoolean(request.getHeader(HEADER_TEMP_GRANT));
        return new CurrentUser(userId, role, tempGrant);
    }

    private long parseUserId(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            log.warn("忽略非法的 {} 请求头: {}", HEADER_USER_ID, value);
            return 0L;
        }
    }
}
