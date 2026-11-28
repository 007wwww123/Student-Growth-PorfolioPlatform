package com.college.growth.academic.record.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/** 竞赛科研（{@code arc_event}）：学生新增，COU 认定。 */
@Data
@TableName("arc_event")
public class ArcEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long studentId;

    /** COMPETITION / PROJECT / RESEARCH。 */
    private String eventType;

    private String title;

    private String roleName;

    /** SCHOOL / PROVINCE / NATIONAL / INTERNATIONAL。 */
    private String level;

    private LocalDate startDate;

    private LocalDate endDate;

    private String description;

    private Long evidenceFileId;

    /** PENDING / PASSED / REJECTED。 */
    private String verifyStatus;

    private Long verifyUserId;

    private LocalDateTime verifyAt;

    private String verifyRemark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
