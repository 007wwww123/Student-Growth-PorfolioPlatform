package com.college.growth.academic.util;

import com.college.growth.academic.common.AcademicConstants;

import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.result.FieldErrorDetail;
import com.college.growth.common.result.ResultCode;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    public static void validateFields(Map<String, Object> fields) {
        Set<String> selected = new HashSet<>();
        for (String key : List.of("strongSubjects", "weakSubjects")) {
            Object value = fields.get(key);
            if (value == null) {
                continue;
            }
            if (!(value instanceof List<?> subjects) || subjects.size() > 3) {
                throw invalidSubjects(key, "科目必须是最多 3 项的列表");
            }
            for (Object subject : subjects) {
                if (!(subject instanceof String text) || text.isBlank()) {
                    throw invalidSubjects(key, "科目名称不能为空");
                }
                if (!selected.add(text.strip())) {
                    throw invalidSubjects(key, "优势与待提升科目中不能重复选择同一科目");
                }
            }
        }
    }

    private static BusinessException invalidSubjects(String key, String message) {
        return new BusinessException(ResultCode.FIELD_VALIDATION_FAILED, message,
                List.of(new FieldErrorDetail(AcademicConstants.FIELD_PREFIX + key, "FORMAT", message)));
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
