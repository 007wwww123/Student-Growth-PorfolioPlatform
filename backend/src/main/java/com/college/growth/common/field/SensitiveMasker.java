package com.college.growth.common.field;

import com.college.growth.common.util.TextUtils;

/**
 * 敏感字段脱敏（BR-D05）。
 *
 * <p>六个板块共用同一套规则：身份证保留前 6 后 4（可核对籍贯与末位），
 * 其余敏感字段保留前 3 后 4。日志、列表、详情、导出都必须经本类，
 * 不允许各处自己写 substring。</p>
 */
public final class SensitiveMasker {

    /** 身份证号字段编码。 */
    public static final String ID_CARD_FIELD = "basic.idCard";

    private SensitiveMasker() {
    }

    /** 身份证号脱敏：保留前 6 位与后 4 位。 */
    public static String maskIdCard(String idCard) {
        return TextUtils.mask(idCard, 6, 4);
    }

    /**
     * 按字段脱敏；非文本值原样返回（数组类敏感值由调用方决定是否整体隐藏）。
     *
     * @param fieldCode 字段编码，如 basic.idCard
     * @param value     原始值
     * @return 脱敏后的值
     */
    public static Object maskValue(String fieldCode, Object value) {
        String text = FieldValues.asText(value);
        if (text == null) {
            return value;
        }
        if (ID_CARD_FIELD.equals(fieldCode)) {
            return maskIdCard(text);
        }
        return TextUtils.mask(text, 3, 4);
    }
}
