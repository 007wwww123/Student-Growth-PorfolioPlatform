package com.college.growth.common.enums;

/**
 * 六大档案板块。
 *
 * <p>编码与需求文档 4.2–4.7 的板块代号一致，同时作为 {@code arc_profile.section_code}
 * 的取值、字段编码前缀与接口路径段使用，全平台不得出现第二套叫法。</p>
 */
public enum SectionCodeEnum {

    BASIC("basic", "基本信息", 1),
    ACADEMIC("academic", "学业发展", 2),
    INTEREST("interest", "兴趣特长", 3),
    LIFE("life", "生活心理", 4),
    CAREER("career", "职业规划", 5),
    MENTOR("mentor", "导师寄语", 6);

    private final String code;
    private final String label;
    private final int order;

    SectionCodeEnum(String code, String label, int order) {
        this.code = code;
        this.label = label;
        this.order = order;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public int getOrder() {
        return order;
    }

    /** 字段编码前缀，如 {@code basic.}、{@code academic.}。 */
    public String getFieldPrefix() {
        return code + ".";
    }

    /** 接口路径段，如 {@code /sections/basic}。 */
    public String getApiBasePath() {
        return "/sections/" + code;
    }

    public static SectionCodeEnum fromCode(String code) {
        for (SectionCodeEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("未知板块编码: " + code);
    }
}
