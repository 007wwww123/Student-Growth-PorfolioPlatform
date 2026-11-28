package com.college.growth.life.util;

import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.result.FieldErrorDetail;
import com.college.growth.common.result.ResultCode;
import com.college.growth.life.common.LifeConstants;
import java.util.List;
import java.util.Map;

/**
 * 生活心理板块工具方法。
 *
 * <p>这里只放「跨字段」的板块规则：单字段的长度、枚举、正则由 {@code arc_field_def.validator}
 * 对应的校验器执行（BR-F02）。两条链路分工明确，同一规则不写两遍。</p>
 */
public final class LifeSectionUtils {

    /** 压力来源最多 3 项（需求 4.5）。 */
    private static final int STRESS_SOURCE_MAX = 3;

    /** 压力自评取值区间，闭区间 1–5（需求 4.5）。 */
    private static final int STRESS_SCORE_MIN = 1;

    private static final int STRESS_SCORE_MAX = 5;

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
     * <p>规则：mood 为 LOW 且压力自评不低于 4 时置 1；关注标记只作台账筛选项，不触发自动通知
     * （需求 4.5：避免形成「系统给学生贴标签」的效果）。</p>
     */
    public static int deriveCareFlag(Integer stressScore, String mood) {
        if ("LOW".equals(mood) && stressScore != null && stressScore >= 4) {
            return 1;
        }
        return 0;
    }

    /**
     * 板块级跨字段校验，在保存与提交两处都会执行。
     *
     * @param fields 字段短名 → 值，已包含本次提交后的完整快照
     */
    public static void validateFields(Map<String, Object> fields) {
        validateStressScore(fields.get("stressScore"));
        validateStringList("stressSource", fields.get("stressSource"), STRESS_SOURCE_MAX);
        // 资助来源由辅导员维护（学生只读），不限制条数，但仍须是非空文本组成的数组
        validateStringList("aidSource", fields.get("aidSource"), 0);
    }

    /**
     * 压力自评：整数 1–5。
     *
     * <p>字段定义的 TEXT_LEN 只保证「最多 1 个字符」，管不住 {@code 0}、{@code 7} 这类越界值，
     * 也管不住 {@code 3.5}，因此在板块层补一条语义校验。</p>
     */
    private static void validateStressScore(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return;
        }
        Integer score = parseStressScore(value);
        if (score == null || score < STRESS_SCORE_MIN || score > STRESS_SCORE_MAX) {
            throw invalid("stressScore", "FORMAT",
                    "压力自评须为 " + STRESS_SCORE_MIN + "–" + STRESS_SCORE_MAX + " 的整数");
        }
    }

    /** 把库里或请求里的压力自评转成整数；无法解析时返回 null，由调用方决定是报错还是忽略。 */
    public static Integer parseStressScore(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        if (value instanceof Number number) {
            double raw = number.doubleValue();
            return raw == Math.rint(raw) ? (int) raw : null;
        }
        try {
            String text = String.valueOf(value).trim();
            return text.matches("-?\\d+") ? Integer.valueOf(text) : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 多选字段：必须是字符串数组，元素非空，且不超过上限。
     *
     * @param max 上限；传 0 表示不限制
     */
    private static void validateStringList(String key, Object value, int max) {
        if (value == null) {
            return;
        }
        if (!(value instanceof List<?> items)) {
            throw invalid(key, "FORMAT", "该字段须为多选数组");
        }
        if (max > 0 && items.size() > max) {
            throw invalid(key, "FORMAT", "最多选择 " + max + " 项");
        }
        if (items.stream().anyMatch(item -> !(item instanceof String text) || text.isBlank())) {
            throw invalid(key, "FORMAT", "每一项都必须是非空文本");
        }
    }

    private static BusinessException invalid(String field, String reason, String message) {
        return new BusinessException(ResultCode.FIELD_VALIDATION_FAILED, message,
                List.of(new FieldErrorDetail(LifeConstants.FIELD_PREFIX + field, reason, message)));
    }
}
