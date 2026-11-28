package com.college.growth.auth.service;

import com.college.growth.auth.entity.SysUser;
import com.college.growth.common.enums.UserRoleEnum;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * JWT 签发与校验（HS256）。
 *
 * <p>没有引入 jjwt：只需要签名、过期与一个 jti，手写三个 base64url 段比多三个依赖更可控，
 * 也便于在这里集中解释「为什么拒绝」——过期与伪造必须给出不同业务码
 * （{@code EC-40102} 与 {@code EC-40101}），前端要靠它决定是静默续期还是跳登录页。</p>
 *
 * <p>载荷只放身份与展示需要的最小信息，不放手机号、身份证等业务数据：
 * token 会落在浏览器存储里，不是安全的保密容器。</p>
 */
@Component
public class JwtService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private static final String HEADER_JSON = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private final ObjectMapper objectMapper;

    private final byte[] secret;

    private final long ttlSeconds;

    public JwtService(ObjectMapper objectMapper,
                      @Value("${security.jwt.secret}") String secret,
                      @Value("${security.jwt.ttl-hours:8}") long ttlHours) {
        if (secret == null || secret.length() < 32) {
            // 密钥过短等于没有签名，宁可启动失败也不要带病运行
            throw new IllegalStateException("security.jwt.secret 至少 32 个字符");
        }
        this.objectMapper = objectMapper;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = ttlHours * 3600L;
    }

    /**
     * 令牌载荷：只含身份与展示信息。
     *
     * <p>{@code tokenVersion} 与 {@code mustChangePassword} 是 F-01 新增的两个判定位：
     * 前者让改密/改角色后的旧令牌在下一次请求即失效（BR-A06），后者让首次登录
     * 未改密的账号只能访问改密接口（BR-A04）。两者都由 {@code JwtAuthFilter}
     * 与数据库当前值比对后生效，令牌里的值本身不作为权限依据。</p>
     */
    public record Claims(Long userId, UserRoleEnum role, boolean tempGrant, int tokenVersion,
                         boolean mustChangePassword, String displayName, String jti,
                         Instant issuedAt, Instant expiresAt) {
    }

    /** 校验失败原因，决定返回 EC-40101 还是 EC-40102。 */
    public static class JwtException extends RuntimeException {

        private final boolean expired;

        public JwtException(String message, boolean expired) {
            super(message);
            this.expired = expired;
        }

        public boolean isExpired() {
            return expired;
        }
    }

    /** 为登录成功的账号签发令牌。 */
    public String issue(SysUser user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(ttlSeconds);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", String.valueOf(user.getId()));
        payload.put("role", user.getRole());
        payload.put("grant", user.getTempGrant() != null && user.getTempGrant() == 1);
        payload.put("ver", user.getTokenVersion() == null ? 0 : user.getTokenVersion());
        payload.put("mcp", user.getMustChangePassword() != null && user.getMustChangePassword() == 1);
        payload.put("name", user.getDisplayName());
        payload.put("jti", java.util.UUID.randomUUID().toString().replace("-", ""));
        payload.put("iat", now.getEpochSecond());
        payload.put("exp", expiresAt.getEpochSecond());

        try {
            String header = URL_ENCODER.encodeToString(HEADER_JSON.getBytes(StandardCharsets.UTF_8));
            String body = URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(payload));
            String signingInput = header + "." + body;
            return signingInput + "." + URL_ENCODER.encodeToString(sign(signingInput));
        } catch (Exception ex) {
            throw new IllegalStateException("签发令牌失败", ex);
        }
    }

    /** 令牌有效期（秒），登录接口回传给前端用于展示与提前续期。 */
    public long getTtlSeconds() {
        return ttlSeconds;
    }

    /**
     * 校验并解析令牌。
     *
     * @throws JwtException 签名不合法、格式错误（expired=false）或已过期（expired=true）
     */
    public Claims parse(String token) {
        if (token == null || token.isBlank()) {
            throw new JwtException("缺少令牌", false);
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new JwtException("令牌格式不正确", false);
        }

        String signingInput = parts[0] + "." + parts[1];
        byte[] actual;
        try {
            actual = URL_DECODER.decode(parts[2]);
        } catch (IllegalArgumentException ex) {
            // 非法 base64 与签名不符是同一类问题：令牌不可信
            throw new JwtException("令牌格式不正确", false);
        }
        byte[] expected = sign(signingInput);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new JwtException("令牌签名不合法", false);
        }

        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(URL_DECODER.decode(parts[1]),
                    new TypeReference<Map<String, Object>>() {
                    });
        } catch (Exception ex) {
            throw new JwtException("令牌内容无法解析", false);
        }

        long exp = asLong(payload.get("exp"));
        Instant expiresAt = Instant.ofEpochSecond(exp);
        if (Instant.now().isAfter(expiresAt)) {
            throw new JwtException("令牌已过期", true);
        }

        return new Claims(
                asLong(payload.get("sub")),
                parseRole(payload.get("role")),
                Boolean.TRUE.equals(payload.get("grant")),
                asInt(payload.get("ver")),
                Boolean.TRUE.equals(payload.get("mcp")),
                payload.get("name") == null ? null : String.valueOf(payload.get("name")),
                payload.get("jti") == null ? "" : String.valueOf(payload.get("jti")),
                Instant.ofEpochSecond(asLong(payload.get("iat"))),
                expiresAt);
    }

    /** 令牌剩余有效秒数，登出写黑名单时用作 TTL。 */
    public long remainingSeconds(Claims claims) {
        long remaining = claims.expiresAt().getEpochSecond() - Instant.now().getEpochSecond();
        return Math.max(remaining, 0);
    }

    private byte[] sign(String signingInput) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("令牌签名失败", ex);
        }
    }

    private long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            throw new JwtException("令牌缺少必要字段", false);
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new JwtException("令牌字段格式不正确", false);
        }
    }

    /** 版本号缺失按 0 处理：升级前签发的旧令牌仍然可用，不至于把人全部踢下线。 */
    private int asInt(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new JwtException("令牌字段格式不正确", false);
        }
    }

    private UserRoleEnum parseRole(Object value) {
        if (value == null) {
            throw new JwtException("令牌缺少角色", false);
        }
        try {
            return UserRoleEnum.valueOf(String.valueOf(value));
        } catch (IllegalArgumentException ex) {
            throw new JwtException("令牌角色不合法", false);
        }
    }
}
