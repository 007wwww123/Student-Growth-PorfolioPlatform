package com.college.growth.life.common;

/**
 * 生活心理板块常量。
 *
 * <p>板块编码、字段前缀、接口路径三者必须与需求文档保持一致，其他位置不得硬编码字符串。</p>
 */
public final class LifeConstants {

    /** 板块编码，取自需求文档 4.5，写入 arc_profile.section_code。 */
    public static final String SECTION_CODE = "LIFE";

    /** 板块中文名称。 */
    public static final String SECTION_NAME = "生活心理";

    /** 字段编码前缀，如 life.name。 */
    public static final String FIELD_PREFIX = "life.";

    /** 接口基础路径。 */
    public static final String API_BASE_PATH = "/sections/life";

    /** 本板块涉及的物理表。 */
    public static final String TABLE_PROFILE = "arc_profile";

    private LifeConstants() {
    }
}
