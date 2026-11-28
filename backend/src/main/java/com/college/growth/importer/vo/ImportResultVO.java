package com.college.growth.importer.vo;

import java.util.List;
import lombok.Data;

/** 导入执行结果。 */
@Data
public class ImportResultVO {

    private Long taskId;

    private int totalRows;

    private int successRows;

    private int failedRows;

    /** SUCCESS / PARTIAL / FAILED */
    private String status;

    /** 前 20 条失败明细。 */
    private List<ImportRowVO> errors;
}
