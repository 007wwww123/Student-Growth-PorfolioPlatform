package com.college.growth.life.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * 板块保存入参：生活心理（LIFE）。
 *
 * <p>与学业、兴趣共用「信封 + {@code fields}」契约：
 * {@code {"academicYear":"2025-2026-1","fields":{"orgRole":"班级学习委员"}}}。</p>
 *
 * <p>字段类型、必填、枚举、长度、可编辑角色全部来自 {@code arc_field_def}，
 * 接口不再逐个声明属性——新增或调整字段不需要改 DTO，也不需要改契约（BR-F02）。</p>
 *
 * <p>同时接受平铺写法（{@code {"orgRole":"..."}}）：早期骨架页发的是扁平结构，
 * 保留兼容可以避免旧页面与联调脚本立刻 400。平铺键并入 {@code fields} 后，
 * 与显式 {@code fields} 走同一套校验，不会出现两套规则。</p>
 */
@Getter
public class LifeSectionSaveDTO {

    /** 学年，如 2025-2026-1。 */
    @Setter
    @Size(max = 16, message = "学年长度不能超过 16")
    private String academicYear;

    /**
     * 待保存的字段值，键为字段短名（如 {@code stressScore}）。
     *
     * <p>用 {@code LinkedHashMap} 保留提交顺序：字段级错误按顺序返回，
     * 前端就地提示时位置稳定。</p>
     */
    @NotEmpty(message = "没有需要保存的字段")
    private final Map<String, Object> fields = new LinkedHashMap<>();

    /** 信封写法：{@code {"fields":{...}}}；显式传 null 视为「没有字段」，交给 @NotEmpty 拦截。 */
    public void setFields(Map<String, Object> values) {
        if (values != null) {
            fields.putAll(values);
        }
    }

    /** 平铺写法：未声明的属性一律视为字段值；传 null 表示清空该字段（非必填项允许清空）。 */
    @JsonAnySetter
    public void putField(String key, Object value) {
        fields.put(key, value);
    }
}
