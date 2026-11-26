package com.college.growth.life.util;

import com.college.growth.life.common.LifeConstants;

/**
 * 生活心理板块工具方法。
 */
public final class LifeSectionUtils {

    private LifeSectionUtils() {
    }

    /**
     * 生成板块实例唯一键：板块编码 + 学生 + 学年。
     *
     * <p>建档时用于判重（S-1-01：已存在则跳过，不重复创建）。</p>
     */
    public static String buildSectionKey(Long studentId, String academicYear) {
        return LifeConstants.SECTION_CODE + ":" + studentId + ":" + academicYear;
    }

    /**
     * 由压力自评与情绪派生关注标记（life.careFlag，0 无需关注 / 1 需关注）。
     *
     * <p>规则：mood 为 LOW 且压力自评不低于 4 时置 1；关注标记只作台账筛选项，不触发自动通知。</p>
     */
    public static int deriveCareFlag(Integer stressScore, String mood) {
        if ("LOW".equals(mood) && stressScore != null && stressScore >= 4) {
            return 1;
        }
        return 0;
    }
}
