package com.college.growth.common.web;

import com.college.growth.auth.entity.SysUser;
import com.college.growth.auth.mapper.SysUserMapper;
import com.college.growth.auth.service.JwtService;
import com.college.growth.auth.service.TokenBlacklistService;
import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.context.UserContext;
import com.college.growth.common.enums.UserRoleEnum;
import com.college.growth.common.result.Result;
import com.college.growth.common.result.ResultCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 登录鉴权过滤器。
 *
 * <p>取代原来的「信任 X-User-Id / X-User-Role 请求头」实现：身份只来自
 * {@code Authorization: Bearer <JWT>}，签名、过期、吊销三关都过才写入 {@link UserContext}，
 * 否则直接返回 401，不进入 Controller。请求头方式自此关闭，任何调用方都不能再自封角色。</p>
 *
 * <p>401 的两种原因必须区分：令牌过期返回 {@code EC-40102}，其余（缺失、伪造、已登出）
 * 返回 {@code EC-40101}；前端据此决定是重新登录还是静默处理。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private static final String BEARER_PREFIX = "Bearer ";

    /** 匿名可访问的路径：登录接口与健康检查。 */
    private static final String[] PUBLIC_PATHS = {
            "/auth/login",
            "/actuator",
            "/error",
    };

    private final JwtService jwtService;

    private final TokenBlacklistService blacklistService;

    private final ObjectMapper objectMapper;

    private final SysUserMapper userMapper;

    public JwtAuthFilter(JwtService jwtService, TokenBlacklistService blacklistService,
                         ObjectMapper objectMapper, SysUserMapper userMapper) {
        this.jwtService = jwtService;
        this.blacklistService = blacklistService;
        this.objectMapper = objectMapper;
        this.userMapper = userMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // CORS 预检不带凭证，必须放行，否则浏览器所有跨域请求都会失败
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = pathOf(request);
        for (String prefix : PUBLIC_PATHS) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = bearerOf(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (token == null) {
            writeUnauthorized(response, ResultCode.UNAUTHORIZED, "未登录或缺少令牌");
            return;
        }

        try {
            JwtService.Claims claims = jwtService.parse(token);
            if (blacklistService.isRevoked(claims.jti())) {
                writeUnauthorized(response, ResultCode.UNAUTHORIZED, "登录已失效，请重新登录");
                return;
            }
            UserContext.set(new CurrentUser(claims.userId(), claims.role(), effectiveTempGrant(claims)));
            try {
                filterChain.doFilter(request, response);
            } finally {
                UserContext.clear();
            }
        } catch (JwtService.JwtException ex) {
            ResultCode code = ex.isExpired() ? ResultCode.TOKEN_EXPIRED : ResultCode.UNAUTHORIZED;
            log.debug("鉴权失败 path={} reason={}", pathOf(request), ex.getMessage());
            writeUnauthorized(response, code, ex.getMessage());
        }
    }

    /**
     * 临时授权以数据库为准：审批通过后旧令牌立即生效，到期/撤销后立即失效。
     * 非管理员一律 false，避免令牌里的旧 claim 越权。
     */
    private boolean effectiveTempGrant(JwtService.Claims claims) {
        if (claims.role() != UserRoleEnum.ADMIN) {
            return false;
        }
        SysUser user = userMapper.selectById(claims.userId());
        if (user == null || user.getStatus() == null || user.getStatus() != 1
                || user.getTempGrant() == null || user.getTempGrant() != 1) {
            return false;
        }
        LocalDateTime expireAt = user.getTempGrantExpireAt();
        return expireAt == null || expireAt.isAfter(LocalDateTime.now());
    }

    /** 去掉 context-path（/api），只留业务路径。 */
    private String pathOf(HttpServletRequest request) {        String uri = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && uri.startsWith(context)) {
            return uri.substring(context.length());
        }
        return uri;
    }

    private String bearerOf(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        String value = authorization.trim();
        if (!value.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        String token = value.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    private void writeUnauthorized(HttpServletResponse response, ResultCode code, String message)
            throws IOException {
        response.setStatus(code.getHttpStatus());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Result.failure(code, message));
    }
}
