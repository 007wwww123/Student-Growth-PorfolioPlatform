package com.college.growth.common.field;

/**
 * 字段类型与存储映射（需求文档 4.1.1）。
 *
 * <p>类型决定落库列：文本类写 {@code value_text}，JSON 类写 {@code value_json}。
 * 新增类型必须同时更新本枚举与前端控件映射，禁止在 Service 里用字符串判断类型。</p>
 */
public enum FieldTypeEnum {

    TEXT("单行文本", false),
    TEXTAREA("多行文本", false),
    NUMBER("数字", false),
    DATE("日期", false),
    SELECT("下拉单选", false),
    MULTI_SELECT("多选", true),
    RADIO("单选组", false),
    CHECKBOX("复选", true),
    FILE("附件", true),
    RANK_MATRIX("矩阵（落子表）", true);

    private final String label;

    private final boolean jsonStored;

    FieldTypeEnum(String label, boolean jsonStored) {
        this.label = label;
        this.jsonStored = jsonStored;
    }

    public String getLabel() {
        return label;
    }

    /** 是否以 JSON 形式存 {@code value_json}。 */
    public boolean isJsonStored() {
        return jsonStored;
    }

    /** 解析存储类型，未知类型回退文本，避免历史脏数据导致整表不可读。 */
    public static FieldTypeEnum fromCode(String code) {
        if (code != null) {
            for (FieldTypeEnum type : values()) {
                if (type.name().equalsIgnoreCase(code.trim())) {
                    return type;
                }
            }
        }
        return TEXT;
    }
}
