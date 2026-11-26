package com.college.growth.review.common;

/**
 * 审核模块常量。
 *
 * <p>六个板块共用同一套审核规则，任何板块都不得自带一份「退回理由长度」「待审状态」
 * 之类的参数；需要按板块差异化时，只能扩字段定义或状态机的公共配置。</p>
 */
public final class ReviewConstants {

    /** 接口基础路径：审核与六个板块解耦，路径上只带板块编码。 */
    public static final String API_BASE_PATH = "/review";

    /** 退回理由最小长度（BR-S04）。 */
    public static final int REASON_MIN_LENGTH = 10;

    /** 审核意见最大长度，与 arc_change_log.reason 列宽一致。 */
    public static final int COMMENT_MAX_LENGTH = 255;

    /** 待审列表默认状态：只有已提交的板块需要审核。 */
    public static final String DEFAULT_STATUS = "SUBMITTED";

    /** 待审列表默认页码。 */
    public static final long DEFAULT_PAGE_NUM = 1L;

    /** 待审列表默认每页条数。 */
    public static final long DEFAULT_PAGE_SIZE = 20L;

    /** 待审列表每页上限，防止一次拉全库。 */
    public static final long MAX_PAGE_SIZE = 100L;

    /** 审计动作：提交。 */
    public static final String ACTION_SUBMIT = "SUBMIT";

    /** 审计动作：撤回。 */
    public static final String ACTION_WITHDRAW = "WITHDRAW";

    /** 审计动作：审核通过。 */
    public static final String ACTION_APPROVE = "APPROVE";

    /** 审计动作：审核退回。 */
    public static final String ACTION_REJECT = "REJECT";

    private ReviewConstants() {
    }
}
