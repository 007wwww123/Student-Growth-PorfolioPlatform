package com.college.growth.mentor.util;

import com.college.growth.mentor.common.MentorConstants;

/**
 * 导师寄语板块工具方法。
 */
public final class MentorSectionUtils {

    private MentorSectionUtils() {
    }

    /**
     * 生成板块实例唯一键：板块编码 + 学生 + 学年。
     *
     * <p>建档时用于判重（S-1-01：已存在则跳过，不重复创建）。</p>
     */
    public static String buildSectionKey(Long studentId, String academicYear) {
        return MentorConstants.SECTION_CODE + ":" + studentId + ":" + academicYear;
    }

    /**
     * 校验谈话日期区间：不晚于今天，且不早于入学日期（mentor.talkDate）。
     *
     * @param talkDate   谈话日期
     * @param enrollDate 入学日期
     */
    public static boolean isValidTalkDate(java.time.LocalDate talkDate, java.time.LocalDate enrollDate) {
        if (talkDate == null) {
            return false;
        }
        return !talkDate.isAfter(java.time.LocalDate.now()) && (enrollDate == null || !talkDate.isBefore(enrollDate));
    }
}
