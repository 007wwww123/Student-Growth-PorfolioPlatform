package com.college.growth.export.vo;

import java.time.LocalDateTime;
import lombok.Data;

/** 导出任务视图。 */
@Data
public class ExportTaskVO {

    private Long id;

    private Long studentId;

    private String academicYear;

    private String status;

    private String fileName;

    private LocalDateTime expireAt;

    private LocalDateTime createdAt;
}
