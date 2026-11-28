package com.college.growth.grant.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 临时授权申请：时长 1–4 小时，理由必填。 */
@Data
public class TempGrantApplyDTO {

    @NotNull(message = "授权时长不能为空")
    @Min(value = 1, message = "授权时长最少 1 小时")
    @Max(value = 4, message = "授权时长最多 4 小时")
    private Integer hours;

    @NotBlank(message = "申请理由不能为空")
    @Size(min = 5, max = 200, message = "申请理由 5-200 字")
    private String reason;
}
