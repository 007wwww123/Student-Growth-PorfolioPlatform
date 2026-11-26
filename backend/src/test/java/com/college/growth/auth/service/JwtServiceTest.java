package com.college.growth.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.college.growth.auth.entity.SysUser;
import com.college.growth.common.enums.UserRoleEnum;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 令牌签发与校验：载荷还原、篡改、过期、弱密钥。 */
class JwtServiceTest {

    private static final String SECRET = "test-secret-with-at-least-32-characters";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private SysUser user() {
        SysUser user = new SysUser();
        user.setId(1001L);
        user.setRole("STU");
        user.setDisplayName("李明");
        user.setTempGrant(0);
        return user;
    }

    @Test
    @DisplayName("签发后可解析出身份信息")
    void issueThenParse() {
        JwtService jwt = new JwtService(objectMapper, SECRET, 8);
        String token = jwt.issue(user());

        JwtService.Claims claims = jwt.parse(token);
        assertThat(claims.userId()).isEqualTo(1001L);
        assertThat(claims.role()).isEqualTo(UserRoleEnum.STU);
        assertThat(claims.tempGrant()).isFalse();
        assertThat(claims.displayName()).isEqualTo("李明");
        assertThat(claims.jti()).isNotBlank();
        assertThat(claims.expiresAt()).isAfter(claims.issuedAt());
        assertThat(jwt.remainingSeconds(claims)).isGreaterThan(0);
    }

    @Test
    @DisplayName("管理员临时授权随令牌传递")
    void tempGrantIncluded() {
        JwtService jwt = new JwtService(objectMapper, SECRET, 8);
        SysUser admin = new SysUser();
        admin.setId(9003L);
        admin.setRole("ADMIN");
        admin.setDisplayName("系统管理员");
        admin.setTempGrant(1);

        assertThat(jwt.parse(jwt.issue(admin)).tempGrant()).isTrue();
    }

    @Test
    @DisplayName("篡改载荷后签名不匹配")
    void tamperedPayloadRejected() {
        JwtService jwt = new JwtService(objectMapper, SECRET, 8);
        String token = jwt.issue(user());
        String[] parts = token.split("\\.");

        String forgedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"9003\",\"role\":\"ADMIN\",\"exp\":9999999999}"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String forged = parts[0] + "." + forgedPayload + "." + parts[2];

        assertThatThrownBy(() -> jwt.parse(forged))
                .isInstanceOf(JwtService.JwtException.class)
                .satisfies(ex -> assertThat(((JwtService.JwtException) ex).isExpired()).isFalse());
    }

    @Test
    @DisplayName("过期令牌标记为 expired")
    void expiredTokenFlagged() {
        JwtService expired = new JwtService(objectMapper, SECRET, -1);
        String token = expired.issue(user());

        assertThatThrownBy(() -> expired.parse(token))
                .isInstanceOf(JwtService.JwtException.class)
                .satisfies(ex -> assertThat(((JwtService.JwtException) ex).isExpired()).isTrue());
    }

    @Test
    @DisplayName("换密钥后无法校验旧令牌")
    void otherSecretRejected() {
        String token = new JwtService(objectMapper, SECRET, 8).issue(user());
        JwtService other = new JwtService(objectMapper, "another-secret-with-32-characters-xx", 8);

        assertThatThrownBy(() -> other.parse(token)).isInstanceOf(JwtService.JwtException.class);
    }

    @Test
    @DisplayName("缺少或格式错误的令牌直接拒绝")
    void malformedTokenRejected() {
        JwtService jwt = new JwtService(objectMapper, SECRET, 8);
        assertThatThrownBy(() -> jwt.parse(null)).isInstanceOf(JwtService.JwtException.class);
        assertThatThrownBy(() -> jwt.parse("a.b")).isInstanceOf(JwtService.JwtException.class);
        assertThatThrownBy(() -> jwt.parse("a.b.c")).isInstanceOf(JwtService.JwtException.class);
    }

    @Test
    @DisplayName("密钥过短时拒绝启动")
    void shortSecretRejected() {
        assertThatThrownBy(() -> new JwtService(objectMapper, "short", 8))
                .isInstanceOf(IllegalStateException.class);
    }
}
