package com.college.growth.file.vo;

import java.time.LocalDateTime;
import lombok.Data;

/** 附件视图。 */
@Data
public class FileVO {

    private Long id;

    private Long studentId;

    private String bizType;

    private Long bizId;

    private String originalName;

    private String contentType;

    private Long sizeBytes;

    private LocalDateTime createdAt;
}
