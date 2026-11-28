package com.college.growth.change.vo;

import java.time.LocalDateTime;
import lombok.Data;

/** 申诉工单视图（F-18 / AC-070）。 */
@Data
public class AppealVO {

    private Long id;

    private Long changeId;

    private Long studentId;

    private String studentName;

    private String sectionCode;

    private String sectionName;

    private String fieldCode;

    private String fieldName;

    private String oldValue;

    private String newValue;

    private Long applicantId;

    private String applicantName;

    private String reason;

    private String status;

    private Long approverId;

    private String approverName;

    private String approverRemark;

    private LocalDateTime createdAt;

    private LocalDateTime approvedAt;
}
