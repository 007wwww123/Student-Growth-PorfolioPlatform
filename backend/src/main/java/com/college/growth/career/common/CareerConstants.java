package com.college.growth.career.common;

/**
 * 职业规划板块常量。
 *
 * <p>板块编码、字段前缀、接口路径三者必须与需求文档保持一致，其他位置不得硬编码字符串。</p>
 */
public final class CareerConstants {

    /** 板块编码，取自需求文档 4.6，写入 arc_profile.section_code。 */
    public static final String SECTION_CODE = "CAREER";

    /** 板块中文名称。 */
    public static final String SECTION_NAME = "职业规划";

    /** 字段编码前缀，如 career.name。 */
    public static final String FIELD_PREFIX = "career.";

    /** 接口基础路径。 */
    public static final String API_BASE_PATH = "/sections/career";

    /** 本板块涉及的物理表。 */
    public static final String TABLE_PROFILE = "arc_profile";

    private CareerConstants() {
    }
}
