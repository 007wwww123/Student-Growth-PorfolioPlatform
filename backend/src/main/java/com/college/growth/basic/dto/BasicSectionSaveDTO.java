package com.college.growth.basic.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.Map;
import lombok.Data;

/**
 * 板块保存入参：基本信息（BASIC）。
 *
 * <p>字段值放在 {@code fields} 里，键为字段短名（如 {@code studentNo}），值为原始 JSON 值。
 * 这样做不是为了「灵活」，而是为了只保留一份字段清单：字段类型、必填、校验、可编辑角色全部读
 * {@code arc_field_def}，不在 DTO 上再用注解写一遍——两处规则一旦不一致，绕过的一定是注解那一处。</p>
 *
 * <p>接口层只做「必须有字段值」的兜底，逐字段校验交由 {@code FieldValidatorRegistry}（BR-F02）。</p>
 */
@Data
public class BasicSectionSaveDTO {

    /** 学年，如 2025-2026-1；为空取当前学年。 */
    private String academicYear;

    /** 字段值，键为字段短名，值为原始 JSON 值；未出现的字段按「不修改」处理。 */
    @NotEmpty(message = "没有需要保存的字段")
    private Map<String, Object> fields;
}
