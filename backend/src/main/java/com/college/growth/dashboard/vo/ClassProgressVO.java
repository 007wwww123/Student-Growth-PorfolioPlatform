package com.college.growth.dashboard.vo;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 班级完成率，用于首页的班级排行与横向条形。
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
}
