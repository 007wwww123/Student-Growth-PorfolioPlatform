package com.college.growth.interest.util;

import com.college.growth.interest.common.InterestConstants;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.profile.FieldValueCodec;
import com.college.growth.common.result.FieldErrorDetail;
import com.college.growth.common.result.ResultCode;
import java.util.List;
import java.util.Map;

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
    public static boolean containsOtherOption(List<?> hobbies) {
        return hobbies != null && hobbies.stream().anyMatch(value -> value instanceof String text
                && ("OTHER".equalsIgnoreCase(text.strip()) || "其他".equals(text.strip())));
    }

    public static void validateFields(Map<String, Object> fields) {
        Object value = fields.get("hobbies");
        if (value == null) {
            return;
        }
        if (!(value instanceof List<?> hobbies) || hobbies.size() > 5
                || hobbies.stream().anyMatch(item -> !(item instanceof String text) || text.isBlank())) {
            throw invalid("hobbies", "FORMAT", "兴趣爱好最多 5 项，每项须为非空文本");
        }
        if (containsOtherOption(hobbies)
                && (!(fields.get("hobbyDesc") instanceof String text) || FieldValueCodec.toText(text).isBlank())) {
            throw invalid("hobbyDesc", "REQUIRED", "选择其他兴趣时请填写兴趣描述");
        }
    }

    private static BusinessException invalid(String field, String reason, String message) {
        return new BusinessException(ResultCode.FIELD_VALIDATION_FAILED, message,
                List.of(new FieldErrorDetail(InterestConstants.FIELD_PREFIX + field, reason, message)));
    }
}
