package com.college.growth.auth.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.stereotype.Component;

/**
 * 密码派生与校验（PBKDF2WithHmacSHA256）。
 *
 * <p>存储格式 {@code pbkdf2$<迭代次数>$<盐 base64>$<哈希 base64>}，迭代次数与盐跟密文放在一起，
 * 以后调参不需要迁移数据。不引入额外密码库：JDK 自带 PBKDF2，避免为了一个哈希函数增加依赖。</p>
 *
 * <p>校验使用 {@link MessageDigest#isEqual} 做定长比较，避免按字节短路带来的时间侧信道。</p>
 */
@Component
public class PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

    private static final String PREFIX = "pbkdf2";

    /** OWASP 2023 对 PBKDF2-HMAC-SHA256 的建议下限为 60 万次；联调环境取 12 万次兼顾登录耗时。 */
    private static final int DEFAULT_ITERATIONS = 120_000;

    private static final int SALT_BYTES = 16;

    private static final int KEY_BITS = 256;

    private static final SecureRandom RANDOM = new SecureRandom();

    /** 生成密文串。 */
    public String hash(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] derived = derive(rawPassword, salt, DEFAULT_ITERATIONS);
        Base64.Encoder encoder = Base64.getEncoder();
        return PREFIX + "$" + DEFAULT_ITERATIONS + "$"
                + encoder.encodeToString(salt) + "$" + encoder.encodeToString(derived);
    }

    /**
     * 校验明文是否匹配密文串。
     *
     * <p>密文格式非法、算法不可用时一律返回 false，不抛异常——登录失败原因统一为
     * 「账号或密码错误」，避免把存储问题暴露给调用方。</p>
     */
    public boolean matches(String rawPassword, String encoded) {
        if (rawPassword == null || encoded == null) {
            return false;
        }
        String[] parts = encoded.split("\\$");
        if (parts.length != 4 || !PREFIX.equals(parts[0])) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = derive(rawPassword, salt, iterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private byte[] derive(String rawPassword, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(rawPassword.toCharArray(), salt, iterations, KEY_BITS);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            return factory.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new IllegalStateException("PBKDF2 不可用，无法校验密码", ex);
        } finally {
            spec.clearPassword();
        }
    }
}
