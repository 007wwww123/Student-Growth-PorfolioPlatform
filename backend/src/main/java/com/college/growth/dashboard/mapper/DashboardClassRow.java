package com.college.growth.dashboard.mapper;

import lombok.Data;

/**
 * 按班级聚合的一行统计，供看板计算总数与班级完成率。
 *
 * <p>{@code studentTotal} 是「本学年有档案的学生数」，不是学籍在册人数：
 * 未建档学生没有 {@code arc_profile} 记录，无从判断完成情况，看板用「建档进度」口径表达，
 * 需要学籍口径时应另接 `stu_student` 统计，不要在这里混算。</p>
 */
@Data
public class DashboardClassRow {

    /** 班级编码；未分班时为 {@code 未分班}。 */
    private String classCode;

    /** 班级名称，未分班或班级已删除时为空。 */
    private String className;

    private Integer studentTotal;

    private Integer completedStudents;

    /** 已提交待审核的板块数。 */
    private Integer submittedProfiles;

    private Integer returnedProfiles;

    private Integer draftProfiles;

    /** 已提交板块数（SUBMITTED/APPROVED/RETURNED/LOCKED），退回率的分母。 */
    private Integer submittedProfilesTotal;

    /** 已审板块数（reviewed_at 与 submitted_at 均非空），即平均审核时长的样本量。 */
    private Integer reviewedProfiles;

    /**
     * 已审板块的审核耗时之和（秒）。
     *
     * <p>只返回秒数之和、不返回班级平均值：整体平均必须用
     * {@code sum(秒) / sum(份数) / 3600} 算，对班级均值再求平均会把小班与大队列等权，
     * 得到一个谁都不是的「平均」。</p>
     */
    private Long reviewSeconds;
}
