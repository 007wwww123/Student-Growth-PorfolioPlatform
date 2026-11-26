package com.college.growth.basic.common;

import com.college.growth.common.field.validation.validator.DerivedFieldValidator;

/**
 * 基本信息板块常量。
 *
 * <p>板块编码、字段前缀、接口路径三者必须与需求文档保持一致，其他位置不得硬编码字符串。</p>
 */
public final class BasicConstants {

    /** 板块编码，取自需求文档 4.2，写入 arc_profile.section_code。 */
    public static final String SECTION_CODE = "BASIC";

    /** 板块中文名称。 */
    public static final String SECTION_NAME = "基本信息";

    /** 字段编码前缀，如 basic.name。 */
    public static final String FIELD_PREFIX = "basic.";

    /** 接口基础路径。 */
    public static final String API_BASE_PATH = "/sections/basic";

    /** 本板块涉及的物理表。 */
    public static final String TABLE_PROFILE = "arc_profile";

    /** 单次保存的字段数上限（BR-F07）。 */
    public static final int MAX_FIELDS_PER_SAVE = 60;

    /** 系统推导字段的校验器标识：不落库、不接受提交、不计必填。取值与校验器保持一致，避免两处各写一份。 */
    public static final String VALIDATOR_DERIVED = DerivedFieldValidator.CODE;

    /** 待确认状态：无待确认值。 */
    public static final String CONFIRM_NONE = "NONE";

    /** 待确认状态：等待辅导员确认。 */
    public static final String CONFIRM_PENDING = "PENDING";

    /** 待确认状态：辅导员已确认。 */
    public static final String CONFIRM_APPROVED = "APPROVED";

    /** 待确认状态：辅导员已驳回。 */
    public static final String CONFIRM_REJECTED = "REJECTED";

    /** 专业字段的短名，由班级推导。 */
    public static final String SHORT_MAJOR = "majorId";

    /** 班级字段的短名。 */
    public static final String SHORT_CLASS = "classId";

    private BasicConstants() {
    }
}
