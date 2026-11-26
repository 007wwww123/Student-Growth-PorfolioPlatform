package com.college.growth.career.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;
import com.college.growth.common.web.DraftInput;

/**
 * 板块保存入参：职业规划（CAREER）。
 *
 * <p>字段校验以需求文档 4.6 为准，规则标识存放在 {@code arc_field_def.validator}，
 * 由校验器注册表按标识执行（BR-F02），此处仅保留接口层的长度与必填兜底。</p>
 */
@Data
public class CareerSectionSaveDTO extends DraftInput {

    /** 学年，如 2025-2026-1。 */
    @Size(max = 16, message = "学年长度不能超过 16")
    private String academicYear;

    /** 发展意向：EMPLOY / POSTGRAD / OVERSEAS / STARTUP / UNDECIDED（career.intention）。 */
    private String intention;

    /** 心仪行业，不超过 64 字（career.industry）。 */
    @Size(max = 64, message = "心仪行业不超过 64 字")
    private String industry;

    /** 心仪岗位，不超过 64 字（career.position）。 */
    @Size(max = 64, message = "心仪岗位不超过 64 字")
    private String position;

    /** 已做准备，不超过 300 字（career.plan）。 */
    @Size(max = 300, message = "已做准备不超过 300 字")
    private String plan;

    /** 所需支持，不超过 300 字（career.support）。 */
    @Size(max = 300, message = "所需支持不超过 300 字")
    private String support;
}
