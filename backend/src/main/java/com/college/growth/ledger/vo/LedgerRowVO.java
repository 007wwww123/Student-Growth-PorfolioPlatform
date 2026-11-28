package com.college.growth.ledger.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 台账明细行：一个学生 × 一个板块档案。
 *
 * <p>未建档学生也会出现：{@code profileId} 为空、{@code sectionCode} 为空、
 * {@code status = UNARCHIVED}、{@code statusLabel = 未建档}，每个学生一行。
 * 前端据此既能按板块筛待办，也能看到「谁还没建档」，不必再调第二个接口。</p>
 */
@Data
public class LedgerRowVO {

    /** 板块实例 ID；未建档行为空。 */
    private Long profileId;

    private Long studentId;

    /** 学号，取自 stu_student.student_no。 */
    private String studentNo;

    /** 学生姓名，优先取学籍姓名，缺失时回退基本信息板块。 */
    private String studentName;

    /** 班级编码：stu_student.class_code → basic.classId → 未分班。 */
    private String classCode;

    /** 班级名称，取自 org_class。 */
    private String className;

    /** 板块编码，大写（BASIC / ACADEMIC …）；未建档行是 null。 */
    private String sectionCode;

    /** 板块中文名；未建档行是 null。 */
    private String sectionName;

    /** 状态：DRAFT / SUBMITTED / RETURNED / APPROVED / LOCKED / UNARCHIVED。 */
    private String status;

    /** 状态中文名；UNARCHIVED 为「未建档」。 */
    private String statusLabel;

    /** 完成度百分比 0–100；未建档行为空。 */
    private BigDecimal completeness;

    /** 提交次数；未建档行为 0。 */
    private Integer submitCount;

    /** 最近一次提交时间；未建档行为空。 */
    private LocalDateTime submittedAt;

    /** 最近一次审核时间；未建档行为空。 */
    private LocalDateTime reviewedAt;

    /** 最近一次审核人；未建档行为空。 */
    private Long reviewerId;

    /** 审核人展示名，取自 sys_user.display_name；查不到时为空。 */
    private String reviewerName;
}
