package com.college.growth.basic.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import com.college.growth.common.web.DraftInput;

/**
 * 板块保存入参：基本信息（BASIC）。
 *
 * <p>字段校验以需求文档 4.2 为准，规则标识存放在 {@code arc_field_def.validator}，
 * 由校验器注册表按标识执行（BR-F02），此处仅保留接口层的长度与必填兜底。</p>
 */
@Data
public class BasicSectionSaveDTO extends DraftInput {

    /** 学年，如 2025-2026-1。 */
    @Size(max = 16, message = "学年长度不能超过 16")
    private String academicYear;

    /** 姓名，2–32 字，不含数字（basic.name）。 */
    @Size(min = 2, max = 32, message = "姓名长度为 2–32 字")
    private String name;

    /** 学号，8–12 位数字，创建后任何角色不可改（basic.studentNo）。 */
    @Pattern(regexp = "^[0-9]{8,12}$", message = "学号必须为 8–12 位数字")
    private String studentNo;

    /** 性别：M / F / U（basic.gender）。 */
    private String gender;

    /** 身份证号，18 位，敏感字段，仅 COU 二次确认后可查看（basic.idCard）。 */
    private String idCard;

    /** 手机号，修改需辅导员确认（basic.phone）。 */
    @Pattern(regexp = "^$|^1[3-9][0-9]{9}$", message = "手机号格式不正确")
    private String phone;

    /** 邮箱（basic.email）。 */
    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不能超过 128")
    private String email;
}
