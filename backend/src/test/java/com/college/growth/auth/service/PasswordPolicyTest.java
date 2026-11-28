package com.college.growth.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.result.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 密码强度规则（BR-A02 / EC-42204）：长度、类别数、错误提示。 */
class PasswordPolicyTest {

    @Test
    @DisplayName("8–20 位且满足三类字符时通过，并原样返回")
    void acceptsStrongPassword() {
        assertThat(PasswordPolicy.validate("Passw0rd!")).isEqualTo("Passw0rd!");
        assertThat(PasswordPolicy.validate("Abc12345")).isEqualTo("Abc12345");
        assertThat(PasswordPolicy.isStrong("Abc12345")).isTrue();
    }

    @Test
    @DisplayName("长度不足或超过 20 位拒绝")
    void rejectsBadLength() {
        assertThatThrownBy(() -> PasswordPolicy.validate("Ab1!"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getResultCode())
                        .isEqualTo(ResultCode.PASSWORD_WEAK));

        String tooLong = "Aa1!" + "x".repeat(17);
        assertThatThrownBy(() -> PasswordPolicy.validate(tooLong))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getResultCode())
                        .isEqualTo(ResultCode.PASSWORD_WEAK));
        assertThat(PasswordPolicy.isStrong(null)).isFalse();
    }

    @Test
    @DisplayName("类别不足三类时拒绝，并在消息里给出当前满足的类数")
    void rejectsTooFewKinds() {
        assertThatThrownBy(() -> PasswordPolicy.validate("abc12345"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    assertThat(((BusinessException) ex).getResultCode())
                            .isEqualTo(ResultCode.PASSWORD_WEAK);
                    assertThat(ex.getMessage()).contains("当前满足 2 类");
                });
    }

    @Test
    @DisplayName("类别统计区分大小写、数字与符号")
    void countsKinds() {
        assertThat(PasswordPolicy.kinds("abcd1234")).isEqualTo(2);
        assertThat(PasswordPolicy.kinds("ABCD1234")).isEqualTo(2);
        assertThat(PasswordPolicy.kinds("Abcd1234")).isEqualTo(3);
        assertThat(PasswordPolicy.kinds("Abcd123!")).isEqualTo(4);
        assertThat(PasswordPolicy.kinds("")).isZero();
    }
}
