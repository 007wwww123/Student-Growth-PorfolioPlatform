package com.college.growth.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 附件元数据（{@code sys_file}，脚本 29）。 */
@Data
@TableName("sys_file")
public class SysFile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long studentId;

    private String bizType;

    private Long bizId;

    private String originalName;

    private String storedName;

    private String contentType;

    private Long sizeBytes;

    private String sha256;

    private Long uploadedBy;

    private LocalDateTime createdAt;
}
