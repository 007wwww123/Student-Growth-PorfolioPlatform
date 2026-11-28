package com.college.growth.archive.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 归档解冻申请。 */
@Data
public class UnfreezeApplyDTO {

    @NotNull(message = "学生 ID 不能为空")
    private Long studentId;

    private String academicYear;

    @NotBlank(message = "解冻原因不能为空")
    @Size(min = 10, max = 500, message = "解冻原因 10-500 字")
    private String reason;
}
