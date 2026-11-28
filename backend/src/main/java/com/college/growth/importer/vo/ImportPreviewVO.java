package com.college.growth.importer.vo;

import java.util.List;
import lombok.Data;

/** 导入预览：表头、总数与前 20 行校验结果。 */
@Data
public class ImportPreviewVO {

    private String fileName;

    private List<String> headers;

    private List<String> expectedHeaders;

    private boolean headerValid;

    private int totalRows;

    private int validRows;

    private int invalidRows;

    /** 前 20 行（含错误原因），供预览页展示。 */
    private List<ImportRowVO> rows;
}
