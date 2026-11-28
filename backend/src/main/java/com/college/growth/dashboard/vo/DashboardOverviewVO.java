package com.college.growth.dashboard.vo;

import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/**
 * 首页进度聚合（需求未编号，按「首页进度聚合」实现）。
 *
 * <p>一个请求返回首页需要的全部数字，避免前端并发打十几个接口再自己拼——
 * 拼出来的数字容易互相矛盾（例如「已建档 12 人」却「完成 15 人」）。</p>
 *
 * <p>口径统一为「本学年已建档」：未建档学生没有 {@code arc_profile} 记录，
 * 不计入分母；需要学籍在册口径的统计属于另一件事，不要在首页混用。</p>
 */
@Data
public class DashboardOverviewVO {

    private String academicYear;

    /** 数据范围文案：全校 / 我的班级 / 指定班级名。 */
    private String scopeLabel;

    /** 本学年已建档学生数。 */
    private int studentTotal;

    /** 六板块全部完成的学生数，取自 {@code arc_profile.completed}。 */
    private int completedStudents;

    /** 完成率 = 完成学生 / 建档学生，百分比一位小数。 */
    private BigDecimal completionRate;

    /** 待审核板块数（SUBMITTED）：辅导员的待办量。 */
    private int pendingReview;

    /** 被退回板块数：学生端待修改量。 */
    private int returnedProfiles;

    /**
     * 退回率 = 当前 RETURNED 板块数 / 已提交板块数（SUBMITTED+APPROVED+RETURNED+LOCKED）。
     *
     * <p>分子是「此刻被退回」的存量，分母是「曾经提交过」的存量，两者都来自同一批班级行，
     * 所以整体退回率按班级分子分母分别求和后再算，不是各班退回率的平均。</p>
     */
    private BigDecimal returnedRate;

    /** 已审板块数（reviewed_at 与 submitted_at 均非空）：平均审核时长的样本量。 */
    private int reviewedProfiles;

    /**
     * 平均审核时长（小时，一位小数）= 已审板块的审核秒数之和 / 已审份数 / 3600。
     *
     * <p>按秒数加权：班级行只回传秒数之和，这里再统一除以总份数，
     * 避免「各班平均再平均」把不同规模的班级等权。
     * 样本为 0 时返回 0，前端必须显示「—」，否则会被读成「零小时审完」。</p>
     */
    private BigDecimal averageReviewHours;

    /** 草稿板块数：尚未提交的量。 */
    private int draftProfiles;

    /** 六板块状态分布，固定六项，没有数据的板块也会出现（各项为 0）。 */
    private List<SectionProgressVO> sections;

    /** 班级完成率，按班级名排序；单班筛选时只有一条。 */
    private List<ClassProgressVO> classes;
}
