package com.college.growth.org.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 班级新增/修改入参。 */
@Data
public class ClassSaveDTO {

    @NotBlank(message = "班级编码不能为空")
    @Size(max = 32, message = "班级编码不超过 32 字符")
    private String classCode;

    @NotBlank(message = "班级名称不能为空")
    @Size(max = 64, message = "班级名称不超过 64 字符")
    private String className;

    @NotNull(message = "请选择所属专业")
    private Long majorId;

    @NotNull(message = "请填写年级")
    @Min(value = 2000, message = "年级应为四位年份")
    @Max(value = 2100, message = "年级应为四位年份")
    private Integer gradeYear;

    /** 现任辅导员（sys_user.id，角色须为 COU），可为空。 */
    private Long counselorUserId;
}
