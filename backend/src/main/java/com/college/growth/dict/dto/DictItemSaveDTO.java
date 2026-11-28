package com.college.growth.dict.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 字典项新增/修改。 */
@Data
public class DictItemSaveDTO {

    @NotBlank(message = "字典值不能为空")
    @Size(max = 128, message = "字典值最多 128 字")
    private String value;

    @NotBlank(message = "字典标签不能为空")
    @Size(max = 128, message = "字典标签最多 128 字")
    private String label;

    @Min(value = 0, message = "排序不能为负")
    @Max(value = 9999, message = "排序过大")
    private Integer sortNo = 0;
}
