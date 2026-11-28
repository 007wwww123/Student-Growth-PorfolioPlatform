package com.college.growth.org.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 学院新增/修改入参。 */
@Data
public class CollegeSaveDTO {

    @NotBlank(message = "学院编码不能为空")
    @Size(max = 32, message = "学院编码不超过 32 字符")
    private String collegeCode;

    @NotBlank(message = "学院名称不能为空")
    @Size(max = 64, message = "学院名称不超过 64 字符")
    private String collegeName;
}
