package com.college.growth.academic.record.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 认定入参：PASSED / REJECTED。 */
@Data
public class VerifyDTO {

    @NotBlank(message = "认定结论不能为空")
    private String status;

    @Size(max = 255, message = "认定意见不超过 255 字")
    private String remark;
}
