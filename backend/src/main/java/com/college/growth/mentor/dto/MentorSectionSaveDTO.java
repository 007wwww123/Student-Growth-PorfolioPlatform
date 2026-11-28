package com.college.growth.mentor.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * 板块保存入参：导师寄语（MENTOR）。
 *
 * <p>与其余五个板块共用「信封 + {@code fields}」契约，但语义不同：<b>本板块没有主表字段值</b>
 * （需求 4.7），谈话内容在 {@code arc_mentor_note} 子表里，通过
 * {@code POST /sections/mentor/{studentId}/notes} 追加。因此这里只保留端点形态，
 * 允许 {@code fields} 为空；一旦传入非空字段，Service 会返回 {@code EC-40003}
 * 并提示改用追加接口，而不是悄悄把子表内容写成主表字段。</p>
 *
 * <p>保持这个端点存在是为了六板块接口结构一致：前端公共外壳、权限配置、
 * 审核列表都按「每板块有 GET/PUT/submit/withdraw」建模，少一个端点就要到处特判。</p>
 *
 * <p>平铺写法同样被收进 {@code fields}，与其余板块一致；不做兼容的话，
 * 早期骨架页发的扁平结构会直接 400。</p>
 */
@Getter
public class MentorSectionSaveDTO {

    /** 学年，如 2025-2026-1。 */
    @Setter
    @Size(max = 16, message = "学年长度不能超过 16")
    private String academicYear;

    /** 待保存的主表字段值；本板块应为空。 */
    private final Map<String, Object> fields = new LinkedHashMap<>();

    public void setFields(Map<String, Object> values) {
        if (values != null) {
            fields.putAll(values);
        }
    }

    /** 平铺写法：未声明的属性一律视为字段值。 */
    @JsonAnySetter
    public void putField(String key, Object value) {
        fields.put(key, value);
    }
}
