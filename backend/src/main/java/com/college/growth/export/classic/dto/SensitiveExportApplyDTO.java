package com.college.growth.export.classic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 敏感列导出审批申请。 */
@Data
public class SensitiveExportApplyDTO {

    @NotBlank(message = "班级编码不能为空")
    private String classCode;

    @NotBlank(message = "申请理由不能为空")
    @Size(min = 5, max = 200, message = "申请理由 5-200 字")
    private String reason;
}
