package com.college.growth.auth.service;

import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.result.ResultCode;

/**
 * 密码强度规则（BR-A02 / EC-42204）。
 *
 * <p>规则只有一份：账号开通、管理员重置、本人改密三处都调用这里。
 * 之前规则写在 {@code AccountServiceImpl} 里，改密接口再抄一遍就会出现
 * 「管理员设得进去、用户改不进去」这类不一致。</p>
 *
 * <p>规则：长度 8–20，且大写字母、小写字母、数字、符号四类中至少满足三类。
 * 不做字典校验（需求未要求），也不在日志里输出密码。</p>
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;

    public static final int MAX_LENGTH = 20;

    /** 四类字符中至少满足的类数。 */
    public static final int REQUIRED_KINDS = 3;

    private PasswordPolicy() {
    }

    /**
     * 校验密码强度，返回原值便于链式使用。
     *
     * @throws BusinessException {@link ResultCode#PASSWORD_WEAK}，消息列出未满足项
     */
    public static String validate(String rawPassword) {
        String value = rawPassword == null ? "" : rawPassword;
        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            throw new BusinessException(ResultCode.PASSWORD_WEAK, "密码长度应为 8–20 位");
        }
        int kinds = kinds(value);
        if (kinds < REQUIRED_KINDS) {
            throw new BusinessException(ResultCode.PASSWORD_WEAK,
                    "密码需包含大写字母、小写字母、数字、符号中的至少三类（当前满足 " + kinds + " 类）");
        }
        return value;
    }

    /** 是否满足强度要求；用于登录页提示与配置校验，不抛异常。 */
    public static boolean isStrong(String rawPassword) {
        if (rawPassword == null) {
            return false;
        }
        return rawPassword.length() >= MIN_LENGTH && rawPassword.length() <= MAX_LENGTH
                && kinds(rawPassword) >= REQUIRED_KINDS;
    }

    /** 统计命中的字符类别数（0–4），用于生成「还差哪类」的提示。 */
    public static int kinds(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        int kinds = 0;
        if (value.chars().anyMatch(Character::isUpperCase)) {
            kinds++;
        }
        if (value.chars().anyMatch(Character::isLowerCase)) {
            kinds++;
        }
        if (value.chars().anyMatch(Character::isDigit)) {
            kinds++;
        }
        if (value.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch))) {
            kinds++;
        }
        return kinds;
    }
}
