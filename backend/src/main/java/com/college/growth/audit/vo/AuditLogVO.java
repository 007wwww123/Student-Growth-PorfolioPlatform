package com.college.growth.audit.vo;

import java.time.LocalDateTime;
import lombok.Data;

/** 审计日志视图。 */
@Data
public class AuditLogVO {

    private Long id;

    private String bizType;

    private String targetType;

    private Long targetId;

    private Long studentId;

    private String sectionCode;

    private String fieldCode;

    private String action;

    private String detail;

    private Long operatorId;

    private String operatorRole;

    private String traceId;

    private LocalDateTime createdAt;
}
