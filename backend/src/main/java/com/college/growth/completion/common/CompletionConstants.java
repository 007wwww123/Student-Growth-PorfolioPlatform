package com.college.growth.completion.common;

/**
 * 完成标记域常量（BR-S10）。
 *
 * <p>「六板块是否全部完成」是一个派生标记，写入 {@code arc_profile.completed}。
 * 判定口径、触发时机、审计动作三项都收口在本包，其他模块只允许调用
 * {@code CompletionService}，不得自己数板块数量或自己判断状态。</p>
 */
public final class CompletionConstants {

    /** 接口前缀：完成标记是跨板块能力，不挂在某个板块路径下。 */
    public static final String API_BASE_PATH = "/completion";

    /** 审计动作：重算完成标记。 */
    public static final String ACTION_REFRESH = "REFRESH_COMPLETED";

    /** 审计目标类型：学生（标记是「学生 × 学年」级别的，不是板块级别）。 */
    public static final String TARGET_STUDENT = "STUDENT";

    /** 触发方式：管理端手动重算。 */
    public static final String TRIGGER_MANUAL = "MANUAL";

    /** 触发方式：定时任务重算。 */
    public static final String TRIGGER_SCHEDULED = "SCHEDULED";

    /** 触发方式：状态流转后随事务重算（提交 / 撤回 / 审核）。 */
    public static final String TRIGGER_WORKFLOW = "STATUS_CHANGE";

    /** 本学期尚未建档的板块在完成情况里的状态文案。 */
    public static final String STATUS_NOT_CREATED = "未建档";

    private CompletionConstants() {
    }
}
