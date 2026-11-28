package com.college.growth.mentor.common;

/**
 * 导师寄语板块常量。
 *
 * <p>板块编码、字段前缀、接口路径三者必须与需求文档保持一致，其他位置不得硬编码字符串。</p>
 */
public final class MentorConstants {

    /** 板块编码，取自需求文档 4.7，写入 arc_profile.section_code。 */
    public static final String SECTION_CODE = "MENTOR";

    /** 板块中文名称。 */
    public static final String SECTION_NAME = "导师寄语";

    /** 字段编码前缀，如 mentor.name。 */
    public static final String FIELD_PREFIX = "mentor.";

    /** 接口基础路径。 */
    public static final String API_BASE_PATH = "/sections/mentor";

    /** 本板块涉及的物理表：板块实例仍落 arc_profile，承载状态机与完成度。 */
    public static final String TABLE_PROFILE = "arc_profile";

    /** 谈话记录子表：一行一次谈话，只追加（BR-S07）。 */
    public static final String TABLE_NOTE = "arc_mentor_note";

    /** 追加/反馈日志里的逻辑字段编码：子表没有单字段编码，用一条记录级标识。 */
    public static final String FIELD_NOTE = "mentor.note";

    /** 日志业务类型：谈话记录追加。 */
    public static final String BIZ_MENTOR_APPEND = "MENTOR_APPEND";

    /** 日志业务类型：学生反馈。 */
    public static final String BIZ_MENTOR_FEEDBACK = "MENTOR_FEEDBACK";

    /** 审计动作：追加谈话记录。 */
    public static final String ACTION_APPEND = "APPEND";

    /** 审计动作：提交学生反馈。 */
    public static final String ACTION_FEEDBACK = "FEEDBACK";

    /**
     * 学生端作者显示名。
     *
     * <p>需求 4.7：不展示 counselor_id 的姓名，只显示「辅导员」。学生端视图连
     * counselorId 列都不下发，避免「前端隐藏」被当成权限控制。</p>
     */
    public static final String AUTHOR_LABEL = "辅导员";

    private MentorConstants() {
    }
}
