package com.college.growth.academic.util;

import com.college.growth.academic.common.AcademicConstants;

/**
 * 学业发展板块工具方法。
 */
public final class AcademicSectionUtils {

    private AcademicSectionUtils() {
    }

    /**
     * 生成板块实例唯一键：板块编码 + 学生 + 学年。
     *
     * <p>建档时用于判重（S-1-01：已存在则跳过，不重复创建）。</p>
     */
    public static String buildSectionKey(Long studentId, String academicYear) {
        return AcademicConstants.SECTION_CODE + ":" + studentId + ":" + academicYear;
    }

    /**
     * 短期目标相似度判定占位（academic.goalShort 不得与上学期重复，相似度上限 90%）。
     *
     * @return 相似度，0–1；具体算法待评审确认后实现
     */
    public static double goalSimilarity(String current, String previous) {
        throw new UnsupportedOperationException("目标相似度算法待确认（DC 决策项）");
    }
}
