package com.college.growth.export.classic.vo;

import java.time.LocalDateTime;
import lombok.Data;

/** 敏感导出审批单视图。 */
@Data
public class SensitiveExportApprovalVO {

    private Long id;

    private Long applicantId;

    private String applicantName;

    private String classCode;

    private String reason;

    private String status;

    private Long approverId;

    private String approverName;

    private String approverRemark;

    private LocalDateTime approvedAt;

    private LocalDateTime expireAt;

    private LocalDateTime createdAt;

    private boolean active;
}
