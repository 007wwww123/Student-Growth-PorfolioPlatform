package com.college.growth.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 身份证号的静态加密与哈希（需求 7.3）。
 *
 * <p>存储格式 {@code enc:v1:<base64(iv+cipher)>}，AES-256-GCM，随机 12 字节 IV；
 * 另外提供 SHA-256 十六进制哈希，用于去重/比对，不可反推。
 * 密钥通过环境变量 {@code ID_CARD_KEY}（base64，解码后 32 字节）注入；
 * 未配置时使用仅供本地开发的固定密钥，生产必须覆盖。</p>
 */
public final class IdCardCrypto {

    private static final Logger log = LoggerFactory.getLogger(IdCardCrypto.class);

    private static final String PREFIX = "enc:v1:";

    private static final String DEV_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final SecretKeySpec KEY = loadKey();

    private IdCardCrypto() {
    }

    /** 是否已经是密文格式。 */
    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    /** 加密；空值原样返回。 */
    public static String encrypt(String plain) {
        if (plain == null || plain.isEmpty()) {
            return plain;
        }
        if (isEncrypted(plain)) {
            return plain;
        }
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, KEY, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("身份证加密失败", ex);
        }
    }

    /** 解密；非密文原样返回，兼容迁移前的历史值。 */
    public static String decrypt(String stored) {
        if (stored == null || stored.isEmpty() || !isEncrypted(stored)) {
            return stored;
        }
        try {
            byte[] payload = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] iv = new byte[12];
            byte[] cipherBytes = new byte[payload.length - 12];
            System.arraycopy(payload, 0, iv, 0, 12);
            System.arraycopy(payload, 12, cipherBytes, 0, cipherBytes.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, KEY, new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("身份证解密失败", ex);
        }
    }

    /** 取明文：优先明文列，否则解密密文列。 */
    public static String plain(String plainValue, String cipherValue) {
        if (plainValue != null && !plainValue.isBlank()) {
            return plainValue;
        }
        return decrypt(cipherValue);
    }

    /** SHA-256 十六进制哈希，用于唯一性比对。 */
    public static String hash(String plain) {
        if (plain == null || plain.isEmpty()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(plain.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("身份证哈希失败", ex);
        }
    }

    private static SecretKeySpec loadKey() {
        String configured = System.getenv("ID_CARD_KEY");
        String base64 = configured == null || configured.isBlank() ? DEV_KEY : configured;
        if (configured == null || configured.isBlank()) {
            log.warn("未配置 ID_CARD_KEY，使用开发默认密钥；生产环境必须通过环境变量覆盖");
        }
        byte[] key = Base64.getDecoder().decode(base64);
        if (key.length != 32) {
            throw new IllegalStateException("ID_CARD_KEY 必须是 base64 编码的 32 字节密钥");
        }
        return new SecretKeySpec(key, "AES");
    }
}
