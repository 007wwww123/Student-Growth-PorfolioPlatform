package com.college.growth.basic.util;

import com.college.growth.basic.common.BasicConstants;

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

    /**
     * 身份证号脱敏：保留前 6 位与后 4 位（BR-D05：导出时不得同时展示姓名学号与完整身份证号）。
     */
    public static String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 10) {
            return idCard;
        }
        return idCard.substring(0, 6) + "********" + idCard.substring(idCard.length() - 4);
    }
}
