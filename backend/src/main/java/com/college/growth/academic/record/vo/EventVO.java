package com.college.growth.academic.record.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/** 竞赛科研视图。 */
@Data
public class EventVO {

    private Long id;

    private Long studentId;

    private String eventType;

    private String title;

    private String roleName;

    private String level;

    private LocalDate startDate;

    private LocalDate endDate;

    private String description;

    private Long evidenceFileId;

    private String verifyStatus;

    private Long verifyUserId;

    private LocalDateTime verifyAt;

    private String verifyRemark;
}
