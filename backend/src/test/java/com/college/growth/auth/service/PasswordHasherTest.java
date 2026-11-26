package com.college.growth.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 密码派生与校验：格式、正确性、非法密文、盐随机性。 */
class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    @DisplayName("哈希后可以用原密码校验通过")
    void hashThenMatch() {
        String encoded = hasher.hash("Passw0rd!");
        assertThat(encoded).startsWith("pbkdf2$120000$");
        assertThat(encoded.split("\\$")).hasSize(4);
        assertThat(hasher.matches("Passw0rd!", encoded)).isTrue();
    }

    @Test
    @DisplayName("错误密码校验失败")
    void wrongPasswordFails() {
        String encoded = hasher.hash("Passw0rd!");
        assertThat(hasher.matches("passw0rd!", encoded)).isFalse();
        assertThat(hasher.matches("", encoded)).isFalse();
    }

    @Test
    @DisplayName("同一密码两次派生结果不同（盐随机）")
    void saltIsRandom() {
        assertThat(hasher.hash("Passw0rd!")).isNotEqualTo(hasher.hash("Passw0rd!"));
    }

    @Test
    @DisplayName("密文格式非法时返回 false 而不是抛异常")
    void malformedHashIsRejected() {
        assertThat(hasher.matches("Passw0rd!", "not-a-hash")).isFalse();
        assertThat(hasher.matches("Passw0rd!", "pbkdf2$abc$x$y")).isFalse();
        assertThat(hasher.matches("Passw0rd!", "bcrypt$120000$x$y")).isFalse();
        assertThat(hasher.matches(null, null)).isFalse();
    }

    @Test
    @DisplayName("空密码不允许派生")
    void emptyPasswordRejected() {
        assertThatThrownBy(() -> hasher.hash("")).isInstanceOf(IllegalArgumentException.class);
    }
}
