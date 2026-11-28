package com.college.growth.importer.vo;

import java.util.List;
import lombok.Data;

/** 导入行（预览或回执）。 */
@Data
public class ImportRowVO {

    /** Excel 行号，从 2 开始（第 1 行是表头）。 */
    private int rowNo;

    private String studentNo;

    private String name;

    private boolean valid;

    private List<String> errors;
}
