package com.college.growth.common.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 字段级错误明细。
 *
 * <p>对应需求文档 6.1 的 {@code details} 数组，仅在字段级错误时返回，
 * 单次最多 20 条，超出部分由提示文案说明"另有 N 项"。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FieldErrorDetail {

    /** 字段编码，与需求文档字段表一致，如 {@code academic.goalShort}。 */
    private String field;

    /** 失败原因枚举，如 REQUIRED / FORMAT / ENUM_MISS / LENGTH。 */
    private String reason;

    /** 面向学生的提示文案。 */
    private String message;
}
