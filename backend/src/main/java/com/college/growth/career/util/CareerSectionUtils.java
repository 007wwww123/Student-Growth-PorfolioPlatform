package com.college.growth.career.util;

import com.college.growth.career.common.CareerConstants;

/**
 * 职业规划板块工具方法。
 */
public final class CareerSectionUtils {

    private CareerSectionUtils() {
    }

    /**
     * 生成板块实例唯一键：板块编码 + 学生 + 学年。
     *
     * <p>建档时用于判重（S-1-01：已存在则跳过，不重复创建）。</p>
     */
    public static String buildSectionKey(Long studentId, String academicYear) {
        return CareerConstants.SECTION_CODE + ":" + studentId + ":" + academicYear;
    }

    /**
     * 判断是否为毕业学年，用于 career 板块的一次性整体更新校验（BR-S08）。
     *
     * @param grade 入学年级，如 2023
     * @param currentAcademicYear 当前学年，如 2026-2027-1
     */
    public static boolean isGraduationYear(int grade, String currentAcademicYear) {
        return currentAcademicYear != null && currentAcademicYear.startsWith(String.valueOf(grade + 4));
    }
}
