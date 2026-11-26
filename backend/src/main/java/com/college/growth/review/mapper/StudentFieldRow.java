package com.college.growth.review.mapper;

import lombok.Data;

/**
 * 列表补全用的字段行：学生 ID + 字段编码 + 文本值。
 *
 * <p>只用于批量取 {@code basic.name} / {@code basic.classId} 这类短文本字段，
 * 不承载 JSON 字段，避免审核列表被大字段拖慢。</p>
 */
@Data
public class StudentFieldRow {

    private Long studentId;

    private String fieldCode;

    private String valueText;
}
