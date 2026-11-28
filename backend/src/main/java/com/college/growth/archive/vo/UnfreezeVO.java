package com.college.growth.archive.vo;

import java.time.LocalDateTime;
import lombok.Data;

/** 归档解冻申请视图。 */
@Data
public class UnfreezeVO {

    private Long id;

    private Long studentId;

    private String studentName;

    private String academicYear;

    private Long applicantId;

    private String applicantName;

    private String reason;

    private String status;

    private Long approverId;

    private String approverName;

    private String approverRemark;

    private LocalDateTime approvedAt;

    private LocalDateTime createdAt;
}
