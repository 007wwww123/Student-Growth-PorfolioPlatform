package com.college.growth.basic.util;

import com.college.growth.basic.common.BasicConstants;
import com.college.growth.common.field.SensitiveMasker;

/**
 * 基本信息板块工具方法。
 */
public final class BasicSectionUtils {

    private BasicSectionUtils() {
    }

    /**
     * 生成板块实例唯一键：板块编码 + 学生 + 学年。
     *
     * <p>建档时用于判重（S-1-01：已存在则跳过，不重复创建）。</p>
     */
    public static String buildSectionKey(Long studentId, String academicYear) {
        return BasicConstants.SECTION_CODE + ":" + studentId + ":" + academicYear;
    }

    /** 取字段短名：{@code basic.studentNo} → {@code studentNo}，接口与前端统一用短名。 */
    public static String shortName(String fieldCode) {
        if (fieldCode == null) {
            return null;
        }
        int index = fieldCode.indexOf('.');
        return index < 0 ? fieldCode : fieldCode.substring(index + 1);
    }

    /**
     * 身份证号脱敏：保留前 6 位与后 4 位（BR-D05：导出时不得同时展示姓名学号与完整身份证号）。
     *
     * <p>规则已上移到 {@link SensitiveMasker}，六板块共用；本方法保留为板块内调用别名。</p>
     */
    public static String maskIdCard(String idCard) {
        return SensitiveMasker.maskIdCard(idCard);
    }

    /**
     * 按字段脱敏。
     *
     * <p>身份证保留前 6 后 4（可核对籍贯与末位），其余敏感字段保留前 3 后 4，
     * 既能让学生确认「是不是我的号」，又不至于泄露完整值。</p>
     */
    public static Object maskValue(String fieldCode, Object value) {
        return SensitiveMasker.maskValue(fieldCode, value);
    }
}
