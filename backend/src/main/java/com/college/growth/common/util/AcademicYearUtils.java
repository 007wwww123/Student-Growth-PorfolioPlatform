package com.college.growth.common.util;

import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.result.ResultCode;
import java.time.LocalDate;
import java.util.regex.Pattern;

/**
 * 学年工具。
 *
 * <p>学年统一写作 {@code 2025-2026-1}（起止年份 + 学期），是 {@code arc_profile} 唯一键的一部分。
 * 学年口径若改成「四年一份」，唯一键与统计口径都要同步调整，因此不允许各处自行拼串。</p>
 */
public final class AcademicYearUtils {

    private static final Pattern YEAR_PATTERN = Pattern.compile("^\\d{4}-\\d{4}-[12]$");

    private AcademicYearUtils() {
    }

    /** 空值取当前学年，非空校验格式后返回。 */
    public static String resolve(String academicYear) {
        if (TextUtils.isBlank(academicYear)) {
            return current();
        }
        String value = academicYear.trim();
        if (!YEAR_PATTERN.matcher(value).matches()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "学年格式应为 2025-2026-1");
        }
        String[] parts = value.split("-");
        int start = Integer.parseInt(parts[0]);
        if (Integer.parseInt(parts[1]) != start + 1) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "学年起止年份必须连续，如 2025-2026-1");
        }
        return value;
    }

    /**
     * 当前学年：9 月至次年 1 月为第一学期，2 月至 8 月为第二学期。
     *
     * <p>8 月按暑期处理，仍归属上一学年第二学期。</p>
     */
    public static String current() {
        LocalDate today = LocalDate.now();
        int year = today.getYear();
        int month = today.getMonthValue();
        if (month >= 9) {
            return year + "-" + (year + 1) + "-1";
        }
        if (month == 1) {
            return (year - 1) + "-" + year + "-1";
        }
        return (year - 1) + "-" + year + "-2";
    }
}
