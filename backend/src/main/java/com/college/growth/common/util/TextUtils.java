package com.college.growth.common.util;

/**
 * 文本处理工具。
 */
public final class TextUtils {

    private static final String BLANK_PATTERN = "[\\s\\u00A0\\u3000]+";

    private TextUtils() {
    }

    /**
     * 按需求文档 4.1.3 通用校验第 1 条处理文本：去除首尾空格，连续空格压缩为一个。
     *
     * @param text 原始文本
     * @return 规范化后的文本；入参为 null 时返回 null
     */
    public static String normalize(String text) {
        if (text == null) {
            return null;
        }
        return text.replaceAll(BLANK_PATTERN, " ").trim();
    }

    /** 判断字符串是否为空白（null、空串、纯空格）。 */
    public static boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }

    /**
     * 写入前清洗：去标签、去控制字符，保留换行（需求文档 4.1.3 第 2、6 条）。
     *
     * <p>长文本按纯文本存储，HTML 标签一律剥离而不是转义——转义会把 {@code &lt;b&gt;}
     * 原样留在库里，导出时再转义一次变成双重转义。</p>
     */
    public static String sanitize(String text) {
        if (text == null) {
            return null;
        }
        String cleaned = text.replaceAll("(?i)<script[^>]*>.*?</script>", "")
                .replaceAll("<[^>]*>", "")
                .replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "");
        return cleaned;
    }

    /** 脱敏：保留前 {@code prefix} 位与后 {@code suffix} 位，中间用 * 填充。 */
    public static String mask(String text, int prefix, int suffix) {
        if (text == null) {
            return null;
        }
        if (text.length() <= prefix + suffix) {
            return "*".repeat(text.length());
        }
        int stars = text.length() - prefix - suffix;
        return text.substring(0, prefix) + "*".repeat(stars) + text.substring(text.length() - suffix);
    }
}
