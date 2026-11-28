package com.college.growth.export.classic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/** 班级 Excel 导出请求。 */
@Data
public class ClassExportDTO {

    @NotBlank(message = "班级编码不能为空")
    private String classCode;

    private String academicYear;

    /** 列 key；为空时用默认列（不含敏感列）。 */
    private List<String> columns;

    /** 敏感列导出审批单 ID；包含手机号/身份证号时必填。 */
    private Long approvalId;
}
