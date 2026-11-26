package com.college.growth.interest.util;

import com.college.growth.interest.common.InterestConstants;

/**
 * 兴趣特长板块工具方法。
 */
public final class InterestSectionUtils {

    private InterestSectionUtils() {
    }

    /**
     * 生成板块实例唯一键：板块编码 + 学生 + 学年。
     *
     * <p>建档时用于判重（S-1-01：已存在则跳过，不重复创建）。</p>
     */
    public static String buildSectionKey(Long studentId, String academicYear) {
        return InterestConstants.SECTION_CODE + ":" + studentId + ":" + academicYear;
    }

    /**
     * 判断是否选择了"其他"兴趣项，选择时必须填写描述（interest.hobbyDesc）。
     */
    public static boolean containsOtherOption(java.util.List<String> hobbies) {
        return hobbies != null && hobbies.contains("OTHER");
    }
}
