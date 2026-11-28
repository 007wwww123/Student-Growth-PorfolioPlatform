package com.college.growth.grant.vo;

import java.time.LocalDateTime;
import lombok.Data;

/** 临时授权申请视图。 */
@Data
public class TempGrantVO {

    private Long id;

    private Long applicantId;

    private String applicantName;

    private Integer hours;

    private String reason;

    private String status;

    private Long approverId;

    private String approverName;

    private String approverRemark;

    private LocalDateTime approvedAt;

    private LocalDateTime expireAt;

    private LocalDateTime createdAt;

    /** 当前是否仍在生效（APPROVED 且未到期）。 */
    private boolean active;
}
