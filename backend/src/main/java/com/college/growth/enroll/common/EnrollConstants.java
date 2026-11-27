package com.college.growth.enroll.common;

/**
 * 批量建档（S-1-01）模块常量。
 *
 * <p>建档是跨板块能力：一名学生一次生成六条板块实例，因此接口不挂在
 * {@code /sections/{code}} 之下，而是收在 {@code /profiles} 前缀里，
 * 与审核模块「路径上带板块参数」的做法区分开。</p>
 */
public final class EnrollConstants {

    /** 接口基础路径。 */
    public static final String API_BASE_PATH = "/profiles";

    /** 单次最多处理的班级数，防止一次误选全院。 */
    public static final int MAX_CLASS_COUNT = 20;

    /** 单次最多建档的学生数，超出应分班分批处理。 */
    public static final int MAX_STUDENT_COUNT = 500;

    /** 审计目标类型：学生。建档是「学生级」动作，不按板块逐条留痕。 */
    public static final String TARGET_STUDENT = "STUDENT";

    /** 审计动作：批量建档。 */
    public static final String ACTION_BATCH_CREATE = "BATCH_CREATE";

    /** 学生建档结果：本次新建了至少一个板块。 */
    public static final String STATUS_CREATED = "CREATED";

    /** 学生建档结果：六个板块都已存在，整人跳过。 */
    public static final String STATUS_SKIPPED = "SKIPPED";

    private EnrollConstants() {
    }
}
