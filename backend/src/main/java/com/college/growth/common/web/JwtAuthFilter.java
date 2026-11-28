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
import java.util.Set;
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
 * {@code Authorization: Bearer <JWT>}，签名、过期、吊销、令牌版本四关都过才写入
 * {@link UserContext}，否则直接返回 401，不进入 Controller。</p>
 *
 * <p>401 的两种原因必须区分：令牌过期返回 {@code EC-40102}，其余（缺失、伪造、已登出、
 * 版本失效）返回 {@code EC-40101}；前端据此决定是重新登录还是静默处理。</p>
 *
 * <p><b>为什么每个请求都要读一次 sys_user：</b>BR-A06 要求权限变更「在下一次请求生效，
 * 不要求重新登录」。只信任令牌里的角色与授权位做不到这一点，所以令牌只负责证明「你是谁」，
 * 角色、停用、令牌版本、临时授权都以数据库当前值为准。代价是一次主键查询，
 * 换来的是停用/改密/改角色立即生效，这笔账在 200 并发下仍然划算。</p>
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

    /**
     * 首次登录未改密时仍可访问的路径（BR-A04）。
     *
     * <p>必须放行改密、会话信息与登出：否则用户进不去改密页，也退不出来。
     * 其余业务接口一律 {@code EC-40305}。</p>
     */
    private static final Set<String> PASSWORD_CHANGE_ALLOWED = Set.of(
            "/auth/password", "/auth/me", "/auth/logout");

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
            writeError(response, ResultCode.UNAUTHORIZED, "未登录或缺少令牌");
            return;
        }

        try {
            JwtService.Claims claims = jwtService.parse(token);
            if (blacklistService.isRevoked(claims.jti())) {
                writeError(response, ResultCode.UNAUTHORIZED, "登录已失效，请重新登录");
                return;
            }

            SysUser user = userMapper.selectById(claims.userId());
            if (user == null || user.getStatus() == null || user.getStatus() != 1) {
                // BR-A07：停用账号的令牌立即失效，不等自然过期
                writeError(response, ResultCode.UNAUTHORIZED, "账号已停用，请重新登录");
                return;
            }
            if (versionOf(user) != claims.tokenVersion()) {
                // BR-A06：改密、重置密码、调整角色后版本号递增，旧令牌下一次请求即失效
                log.debug("令牌版本已失效 userId={} tokenVer={} dbVer={}",
                        user.getId(), claims.tokenVersion(), versionOf(user));
                writeError(response, ResultCode.UNAUTHORIZED, "账号权限已变更，请重新登录");
                return;
            }
            if (mustChange(user) && !PASSWORD_CHANGE_ALLOWED.contains(pathOf(request))) {
                writeError(response, ResultCode.PASSWORD_CHANGE_REQUIRED, "请先修改初始密码");
                return;
            }

            UserContext.set(new CurrentUser(user.getId(), UserRoleEnum.fromCode(user.getRole()),
                    effectiveTempGrant(user)));
            try {
                filterChain.doFilter(request, response);
            } finally {
                UserContext.clear();
            }
        } catch (JwtService.JwtException ex) {
            ResultCode code = ex.isExpired() ? ResultCode.TOKEN_EXPIRED : ResultCode.UNAUTHORIZED;
            log.debug("鉴权失败 path={} reason={}", pathOf(request), ex.getMessage());
            writeError(response, code, ex.getMessage());
        }
    }

    /**
     * 临时授权以数据库为准：审批通过后旧令牌立即生效，到期/撤销后立即失效。
     * 非管理员一律 false，避免令牌里的旧 claim 越权。
     */
    private boolean effectiveTempGrant(SysUser user) {
        if (!UserRoleEnum.ADMIN.name().equalsIgnoreCase(user.getRole())) {
            return false;
        }
        if (user.getTempGrant() == null || user.getTempGrant() != 1) {
            return false;
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            return false;
        }
        LocalDateTime expireAt = user.getTempGrantExpireAt();
        return expireAt == null || expireAt.isAfter(LocalDateTime.now());
    }

    private int versionOf(SysUser user) {
        return user.getTokenVersion() == null ? 0 : user.getTokenVersion();
    }

    private boolean mustChange(SysUser user) {
        return user.getMustChangePassword() != null && user.getMustChangePassword() == 1;
    }

    /** 去掉 context-path（/api），只留业务路径。 */
    private String pathOf(HttpServletRequest request) {
        String uri = request.getRequestURI();
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

    private void writeError(HttpServletResponse response, ResultCode code, String message)
            throws IOException {
        response.setStatus(code.getHttpStatus());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Result.failure(code, message));
    }
}
