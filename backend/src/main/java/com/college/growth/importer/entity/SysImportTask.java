package com.college.growth.importer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 导入任务（{@code sys_import_task}，脚本 17）。 */
@Data
@TableName("sys_import_task")
public class SysImportTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** STUDENT */
    private String taskType;

    private String fileName;

    private Integer totalRows;

    private Integer successRows;

    private Integer failedRows;

    /** SUCCESS / PARTIAL / FAILED */
    private String status;

    /** 失败行明细 JSON。 */
    private String errorJson;

    private Long operatorId;

    private LocalDateTime createdAt;
}
