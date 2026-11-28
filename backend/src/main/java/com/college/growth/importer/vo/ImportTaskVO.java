package com.college.growth.importer.vo;

import java.time.LocalDateTime;
import lombok.Data;

/** 导入任务列表行。 */
@Data
public class ImportTaskVO {

    private Long id;

    private String fileName;

    private int totalRows;

    private int successRows;

    private int failedRows;

    private String status;

    private Long operatorId;

    private LocalDateTime createdAt;
}
