package com.college.growth.ledger.common;

/**
 * 班级台账常量（F-07 / AC-020）。
 *
 * <p>台账是纯读能力：所有数字来自 {@code arc_profile} 与 {@code stu_student}，
 * 不写库、不重算 {@code arc_profile.completed}。完成口径直接读该标记，
 * 与首页看板、归档、完成标记保持同一来源，避免出现「台账说完成、归档说没完成」。</p>
 */
public final class LedgerConstants {

    /** 接口前缀。 */
    public static final String API_BASE_PATH = "/ledger";

    /**
     * 未分班学生的归属桶。
     *
     * <p>班级取值优先级与 dashboard 一致：{@code stu_student.class_code} →
     * {@code basic.classId} → {@code 未分班}。取不到班级的学生不能被统计漏掉，
     * 否则台账总数会小于学籍在册人数。</p>
     */
    public static final String UNASSIGNED_CLASS = "未分班";

    /** 状态筛选默认值：不按状态过滤。 */
    public static final String STATUS_ALL = "ALL";

    /**
     * 台账专有状态：本学年没有任何板块档案的学生。
     *
     * <p>它不是状态机的第六个状态——{@code arc_profile.status} 永远只有五态。
     * 这里只是在「学生 × 板块」行集里，为没有档案的学生补一行虚拟行，
     * 让辅导员能看到「谁还没开始」。</p>
     */
    public static final String STATUS_UNARCHIVED = "UNARCHIVED";

    /** 未建档状态的中文名，与完成标记页「未建档」用词一致。 */
    public static final String STATUS_UNARCHIVED_LABEL = "未建档";

    /** 默认页码。 */
    public static final long DEFAULT_PAGE_NUM = 1L;

    /** 默认每页条数，与审核工作台一致。 */
    public static final long DEFAULT_PAGE_SIZE = 20L;

    /** 每页上限，防止一次拉全库。 */
    public static final long MAX_PAGE_SIZE = 100L;

    /** 百分比小数位：保留一位，前端直接展示，不再二次取整。 */
    public static final int RATE_SCALE = 1;

    private LedgerConstants() {
    }
}
