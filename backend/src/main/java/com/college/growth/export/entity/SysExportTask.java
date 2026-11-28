package com.college.growth.export.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 导出任务（{@code sys_export_task}，脚本 20）。 */
@Data
@TableName("sys_export_task")
public class SysExportTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** PROFILE_PDF */
    private String taskType;

    private Long studentId;

    private String academicYear;

    /** SUCCESS / FAILED */
    private String status;

    private String fileName;

    private String filePath;

    private LocalDateTime expireAt;

    private Long operatorId;

    private String errorMsg;

    private LocalDateTime createdAt;
}
