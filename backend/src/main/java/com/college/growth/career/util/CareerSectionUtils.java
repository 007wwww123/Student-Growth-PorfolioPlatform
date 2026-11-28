package com.college.growth.career.util;

import com.college.growth.career.common.CareerConstants;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.result.FieldErrorDetail;
import com.college.growth.common.result.ResultCode;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 职业规划板块工具方法。
 *
 * <p>单字段的长度与枚举由 {@code arc_field_def.validator} 对应校验器执行（BR-F02）；
 * 这里只补「配置缺失时兜底」的跨字段判断。</p>
 */
public final class CareerSectionUtils {

    /** 发展意向字典值（需求 4.6）。 */
    private static final Set<String> INTENTION_CODES =
            Set.of("EMPLOY", "POSTGRAD", "OVERSEAS", "STARTUP", "UNDECIDED");

    /** 本平台按四年制本科计算毕业学年。 */
    private static final int ACADEMIC_YEARS = 4;

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
     * <p>学年串取「起始年」判定：4 年制入学 2022 级，第 4 学年是 {@code 2025-2026-1/2}，
     * 因此起始年 = 入学年 + 3。原实现用 +4 取到的是第 5 学年，会把毕业学年判成非毕业学年，
     * 学生申请整体更新时被误拒。</p>
     *
     * <p>学制按 4 年制固定：本平台服务的是四年制本科；出现五年制专业时应把学制改成参数，
     * 而不是在这里再写一个 +4 的分支。</p>
     *
     * @param grade 入学年级，如 2023
     * @param currentAcademicYear 学年，如 2026-2027-1
     */
    public static boolean isGraduationYear(int grade, String currentAcademicYear) {
        // 学年格式固定为 yyyy-yyyy-n；只截前 4 位会把 "2025" 这类残缺值也判成命中
        if (currentAcademicYear == null || !currentAcademicYear.matches("\\d{4}-\\d{4}.*")) {
            return false;
        }
        String startYear = currentAcademicYear.substring(0, 4);
        return startYear.equals(String.valueOf(grade + ACADEMIC_YEARS - 1));
    }

    /**
     * 板块级校验。
     *
     * <p>目前只有一条：发展意向必须落在字典范围内。运行期主校验是 {@code ENUM} 校验器
     * （读 {@code options_json}），这里再拦一次的原因很具体——字典由管理员维护，
     * {@code options_json} 被清空或误改时 {@code ENUM} 会直接放行任意值，
     * 而 {@code career.intention} 是必填的统计口径字段，写进脏值后导出与统计都要返工。</p>
     *
     * @param fields 字段短名 → 值（本次提交后的完整快照）
     */
    public static void validateFields(Map<String, Object> fields) {
        Object intention = fields.get("intention");
        if (intention == null || String.valueOf(intention).isBlank()) {
            return;
        }
        String code = String.valueOf(intention).trim().toUpperCase();
        if (!INTENTION_CODES.contains(code)) {
            throw invalid("intention", "发展意向取值不在字典范围内：" + intention);
        }
    }

    private static BusinessException invalid(String field, String message) {
        return new BusinessException(ResultCode.FIELD_VALIDATION_FAILED, message,
                List.of(new FieldErrorDetail(CareerConstants.FIELD_PREFIX + field, "ENUM", message)));
    }
}
