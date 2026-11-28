package com.college.growth.dashboard.vo;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 班级进度：完成率、待审量、退回率与平均审核时长，用于首页的班级排行与横向条形。
 *
 * <p>四项指标都出自同一条班级聚合行，前端不再二次推导；
 * 平均审核时长按本班已审板块的秒数之和 / 份数算，不是班级均值的均值。</p>
 */
@Data
public class ClassProgressVO {

    private String classCode;

    private String className;

    /** 本学年有档案的学生数。 */
    private int studentTotal;

    /** 六板块全部完成的学生数。 */
    private int completedStudents;

    /** 待审核板块数（status = SUBMITTED）。 */
    private int pendingReview;

    /** 完成率 = 完成学生 / 建档学生，百分比一位小数；无学生时为 0。 */
    private BigDecimal completionRate;

    /** 退回率 = 当前 RETURNED 板块数 / 已提交板块数，百分比一位小数；无已提交板块时为 0。 */
    private BigDecimal returnedRate;

    /** 已审板块数：平均审核时长的样本量，为 0 时前端应显示「—」而不是 0.0。 */
    private int reviewedProfiles;

    /** 平均审核时长（小时，一位小数）；样本为 0 时为 0，不代表「零小时审完」。 */
    private BigDecimal averageReviewHours;
}
