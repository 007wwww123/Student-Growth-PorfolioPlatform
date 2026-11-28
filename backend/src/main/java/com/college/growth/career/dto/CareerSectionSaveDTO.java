package com.college.growth.career.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * 板块保存入参：职业规划（CAREER）。
 *
 * <p>与其余板块共用「信封 + {@code fields}」契约：
 * {@code {"academicYear":"2025-2026-1","fields":{"intention":"POSTGRAD"}}}。</p>
 *
 * <p>字段类型、必填、枚举、长度、可编辑角色全部来自 {@code arc_field_def}，
 * 接口不逐个声明属性——新增或调整字段不需要改 DTO 与契约（BR-F02）。</p>
 *
 * <p>平铺写法（字段直接放在顶层）仍被接受，用于兼容早期骨架页与联调脚本。</p>
 */
@Getter
public class CareerSectionSaveDTO {

    /** 学年，如 2025-2026-1。 */
    @Setter
    @Size(max = 16, message = "学年长度不能超过 16")
    private String academicYear;

    /** 待保存的字段值，键为字段短名（如 {@code intention}）。 */
    @NotEmpty(message = "没有需要保存的字段")
    private final Map<String, Object> fields = new LinkedHashMap<>();

    /** 信封写法；显式传 null 视为「没有字段」，交给 @NotEmpty 拦截。 */
    public void setFields(Map<String, Object> values) {
        if (values != null) {
            fields.putAll(values);
        }
    }

    /** 平铺写法：未声明的属性一律视为字段值；传 null 表示清空该字段。 */
    @JsonAnySetter
    public void putField(String key, Object value) {
        fields.put(key, value);
    }
}
