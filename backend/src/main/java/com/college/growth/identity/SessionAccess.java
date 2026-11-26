package com.college.growth.identity;

import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.result.Result;
import com.college.growth.common.result.ResultCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@RestController
public class SessionAccess implements WebMvcConfigurer, HandlerInterceptor {
    private static final Pattern BEARER = Pattern.compile("Bearer ([A-Za-z0-9_-]{43,128})", Pattern.CASE_INSENSITIVE);
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public SessionAccess(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/sections/**", "/workflow/**", "/auth/**");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String hash = tokenHash(request.getHeader("Authorization"));
        ArchiveIdentity identity = jdbc.query("""
                SELECT actor_id, role_code, student_ids, academic_years, expires_at <= CURRENT_TIMESTAMP AS expired
                FROM arc_session WHERE token_hash = ?
                """, rs -> {
            if (!rs.next()) throw new BusinessException(ResultCode.UNAUTHORIZED);
            if (rs.getBoolean("expired")) throw new BusinessException(ResultCode.TOKEN_EXPIRED);
            try {
                return new ArchiveIdentity(rs.getLong("actor_id"), ArchiveIdentity.Role.valueOf(rs.getString("role_code")),
                        json.readValue(rs.getString("student_ids"), new TypeReference<Set<Long>>() {}),
                        json.readValue(rs.getString("academic_years"), new TypeReference<Set<String>>() {}));
            } catch (java.io.IOException | IllegalArgumentException e) {
                throw new IllegalStateException("会话授权配置无效", e);
            }
        }, hash);
        request.setAttribute(ArchiveIdentity.class.getName(), identity);
        String route = String.valueOf(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));
        if (route.startsWith("/sections/")) {
            Map<?, ?> variables = (Map<?, ?>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
            long studentId;
            try {
                studentId = Long.parseLong(String.valueOf(variables.get("studentId")));
            } catch (NumberFormatException e) {
                throw new BusinessException(ResultCode.PARAM_INVALID);
            }
            String section = route.split("/")[2];
            if (variables.containsKey("code")) section = variables.get("code").toString();
            identity.requireRead(studentId);
            if (identity.role() == ArchiveIdentity.Role.MENTOR && !"mentor".equals(section)) {
                throw new BusinessException(ResultCode.DATA_SCOPE_DENIED);
            }
            if (!"GET".equals(request.getMethod()) && !"HEAD".equals(request.getMethod())) {
                identity.requireWrite(studentId, section);
            }
            String year = request.getParameter("academicYear");
            if (year != null) identity.requireYear(year);
        }
        response.setHeader("Cache-Control", "no-store");
        return true;
    }

    @GetMapping("/auth/me")
    public Result<ArchiveIdentity> me() { return Result.success(ArchiveIdentity.current()); }

    @PostMapping("/auth/logout")
    public Result<Void> logout(HttpServletRequest request) {
        jdbc.update("DELETE FROM arc_session WHERE token_hash = ?", tokenHash(request.getHeader("Authorization")));
        return Result.success();
    }

    static String tokenHash(String header) {
        var matcher = BEARER.matcher(header == null ? "" : header);
        if (!matcher.matches()) throw new BusinessException(ResultCode.UNAUTHORIZED);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(matcher.group(1).getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
