package com.college.growth.interest.dto;

import java.util.List;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 板块保存入参：兴趣特长（INTEREST）。
 *
 * <p>字段校验以需求文档 4.4 为准，规则标识存放在 {@code arc_field_def.validator}，
 * 由校验器注册表按标识执行（BR-F02），此处仅保留接口层的长度与必填兜底。</p>
 */
@Data
public class InterestSectionSaveDTO {

    /** 学年，如 2025-2026-1。 */
    @Size(max = 16, message = "学年长度不能超过 16")
    private String academicYear;

    /** 兴趣爱好，1–5 项（interest.hobbies）。 */
    @NotNull(message = "兴趣爱好不能为空")
    @Size(min = 1, max = 5, message = "兴趣爱好为 1–5 项")
    private List<String> hobbies;

    /** 兴趣描述，不超过 300 字；选择"其他"时必填（interest.hobbyDesc）。 */
    @Size(max = 300, message = "兴趣描述不超过 300 字")
    private String hobbyDesc;

    /** 技能特长，不超过 300 字（interest.skills）。 */
    @Size(max = 300, message = "技能特长不超过 300 字")
    private String skills;

    /** 理想描述，不超过 300 字（interest.ideal）。 */
    @Size(max = 300, message = "理想描述不超过 300 字")
    private String ideal;
}
