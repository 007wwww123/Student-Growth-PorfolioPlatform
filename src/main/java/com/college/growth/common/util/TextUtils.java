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
}
