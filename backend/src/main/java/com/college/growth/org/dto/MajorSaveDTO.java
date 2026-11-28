package com.college.growth.org.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 专业新增/修改入参。 */
@Data
public class MajorSaveDTO {

    @NotNull(message = "请选择所属学院")
    private Long collegeId;

    @NotBlank(message = "专业编码不能为空")
    @Size(max = 32, message = "专业编码不超过 32 字符")
    private String majorCode;

    @NotBlank(message = "专业名称不能为空")
    @Size(max = 64, message = "专业名称不超过 64 字符")
    private String majorName;
}
