package com.college.growth.ledger.vo;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 班级完成度汇总行（台账顶部「班级完成度」表）。
 *
 * <p>分母是在册学生数（{@code stu_student.status = 1} 且班级在数据范围内），
 * 不是「本学年有档案的学生数」。这是台账与首页看板的刻意差异：看板用「建档进度」口径，
 * 台账要回答「这个班还有几个人没开始」，因此未建档学生必须计入分母，
 * 否则一个班全员未建档时完成率会显示 100%（0/0 的假象）。</p>
 */
@Data
public class LedgerClassVO {

    /** 班级编码；未分班时为 {@code 未分班}。 */
    private String classCode;

    /** 班级名称；班级已删除或未分班时回落为编码。 */
    private String className;

    /** 在册学生数（分母）。 */
    private int studentTotal;

    /** 本学年至少有一个板块档案的学生数。 */
    private int archivedStudents;

    /** 本学年没有任何板块档案的学生数，等于 studentTotal - archivedStudents。 */
    private int unarchivedStudents;

    /** 六板块全部完成的学生数（读 arc_profile.completed）。 */
    private int completedStudents;

    /** 完成率 = completedStudents / studentTotal，百分比保留一位小数；分母为 0 时为 0.0。 */
    private BigDecimal completionRate;

    /** 待审核板块数（status = SUBMITTED）。 */
    private int pendingProfiles;

    /** 已退回板块数（status = RETURNED）。 */
    private int returnedProfiles;

    /** 草稿板块数（status = DRAFT）。 */
    private int draftProfiles;
}
