package com.college.growth.change.vo;

import java.time.LocalDateTime;
import lombok.Data;

/** 变更记录视图（F-18）。 */
@Data
public class ChangeLogVO {

    private Long id;

    private Long studentId;

    private String studentName;

    private String sectionCode;

    private String sectionName;

    private String bizType;

    private String fieldCode;

    private String fieldName;

    private String oldValue;

    private String newValue;

    private String fromStatus;

    private String toStatus;

    private Long operatorId;

    private String operatorName;

    private String operatorRole;

    private String source;

    private String reason;

    private LocalDateTime createdAt;

    /** 该变更最近一条申诉。 */
    private Long appealId;

    private String appealStatus;
}
