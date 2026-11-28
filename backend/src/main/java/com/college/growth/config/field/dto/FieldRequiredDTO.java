package com.college.growth.config.field.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 必填开关入参（AC-007）。 */
@Data
public class FieldRequiredDTO {

    @NotNull(message = "必填标记不能为空")
    @Min(value = 0, message = "required 只能是 0 或 1")
    @Max(value = 1, message = "required 只能是 0 或 1")
    private Integer required;
}
