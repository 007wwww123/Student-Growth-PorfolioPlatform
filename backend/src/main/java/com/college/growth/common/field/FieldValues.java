package com.college.growth.common.field;

import java.util.Collection;
import java.util.Map;

/**
 * 字段值工具：把前端传来的松散 JSON 值统一成文本或判空。
 *
 * <p>接口入参是 {@code Map<String,Object>}，数字、布尔、数组都可能出现；
 * 校验与落库必须走同一套转换，避免「校验时按字符串、落库时按对象」导致规则被绕过。</p>
 */
public final class FieldValues {

    private FieldValues() {
    }

    /** 转为文本；集合、对象返回 null（说明类型与字段定义不符）。 */
    public static String asText(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return text;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return null;
    }

    /** 是否为空值：null、空白串、空集合、空 Map。 */
    public static boolean isEmpty(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String text) {
            return text.trim().isEmpty();
        }
        if (value instanceof Collection<?> collection) {
            return collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        return false;
    }

    /** 按字符数截断，用于日志字段（避免超长值写爆列）。 */
    public static String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }
}
