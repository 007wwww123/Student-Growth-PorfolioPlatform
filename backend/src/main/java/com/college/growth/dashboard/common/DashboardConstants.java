package com.college.growth.dashboard.common;

/**
 * 首页进度聚合常量。
 *
 * <p>本模块只做「读」：所有数字来自 {@code arc_profile}，不写任何表，
 * 也不在这里重算完成标记——标记的真源是 {@code arc_profile.completed}（由 S-1-10 维护），
 * 看板读它而不是自己再数一遍，否则看板与归档、完成标记会出现三套口径。</p>
 */
public final class DashboardConstants {

    /** 接口前缀。 */
    public static final String API_BASE_PATH = "/dashboard";

    /** 未分班学生的归属桶：主数据与基本信息都没有班级时落到这里，不能被统计漏掉。 */
    public static final String UNASSIGNED_CLASS = "未分班";

    /** 百分比小数位：保留一位，前端直接展示，不再二次取整。 */
    public static final int RATE_SCALE = 1;

    /** 数据范围文案。 */
    public static final String SCOPE_SCHOOL = "全校";

    public static final String SCOPE_MY_CLASSES = "我的班级";

    private DashboardConstants() {
    }
}
