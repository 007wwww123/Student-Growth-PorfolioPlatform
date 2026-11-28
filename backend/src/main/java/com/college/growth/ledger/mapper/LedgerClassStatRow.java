package com.college.growth.ledger.mapper;

import lombok.Data;

/**
 * 按班级聚合的一行统计，供台账顶部汇总表与完成率计算。
 *
 * <p>一行里的 {@code studentTotal} 是在册学生数，{@code archivedStudents} 是本学年
 * 至少有一个板块档案的学生数；两者之差即未建档人数，不需要服务层再去数一遍。</p>
 */
@Data
public class LedgerClassStatRow {

    /** 班级编码；未分班时为 {@code 未分班}。 */
    private String classCode;

    /** 班级名称，未分班或班级已删除时为空。 */
    private String className;

    /** 在册学生数（stu_student.status = 1）。 */
    private Integer studentTotal;

    /** 本学年至少有一个板块档案的学生数。 */
    private Integer archivedStudents;

    /** 六板块全部完成的学生数（arc_profile.completed = 1）。 */
    private Integer completedStudents;

    /** 待审核板块数（SUBMITTED）。 */
    private Integer pendingProfiles;

    /** 已退回板块数（RETURNED）。 */
    private Integer returnedProfiles;

    /** 草稿板块数（DRAFT）。 */
    private Integer draftProfiles;
}
