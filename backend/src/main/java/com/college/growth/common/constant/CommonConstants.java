package com.college.growth.common.constant;

/**
 * 全平台通用常量。
 */
public final class CommonConstants {

    /** 链路追踪号在 MDC 与响应头中的键名。 */
    public static final String TRACE_ID = "traceId";

    /** 链路追踪号响应头。 */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /** 档案板块数量，固定为六块（需求文档 2.2：六板块全部 APPROVED 才算完成）。 */
    public static final int SECTION_COUNT = 6;

    /** 逻辑删除标记：未删除。 */
    public static final int NOT_DELETED = 0;

    /** 逻辑删除标记：已删除。 */
    public static final int DELETED = 1;

    private CommonConstants() {
    }
}
