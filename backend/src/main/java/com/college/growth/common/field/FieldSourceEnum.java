package com.college.growth.common.field;

/**
 * 字段来源与可编辑性（需求文档 4.1.2）。
 *
 * <p>来源码写入 {@code arc_field_def.source}，是「谁能改这个字段」的默认依据；
 * 字段定义里的 {@code editable_roles} 可在其基础上收窄，不允许放宽。</p>
 */
public enum FieldSourceEnum {

    /** 学籍数据导入：仅管理员通过重新导入修正。 */
    SIS("学籍导入", false, false),
    /** 辅导员维护：学生只读。 */
    COU("辅导员维护", false, true),
    /** 学生自填：仅在草稿/退回状态可改。 */
    STU("学生自填", true, true),
    /** 学生提交、辅导员确认：修改进入待确认区。 */
    STU_CFM("学生提交辅导员确认", true, true),
    /** 系统生成：如专业由班级推导。 */
    SYS("系统生成", false, false),
    /** 批量导入覆盖：覆盖前留快照。 */
    IMP("批量导入", false, true);

    private final String label;

    private final boolean studentEditable;

    private final boolean counselorEditable;

    FieldSourceEnum(String label, boolean studentEditable, boolean counselorEditable) {
        this.label = label;
        this.studentEditable = studentEditable;
        this.counselorEditable = counselorEditable;
    }

    public String getLabel() {
        return label;
    }

    public boolean isStudentEditable() {
        return studentEditable;
    }

    public boolean isCounselorEditable() {
        return counselorEditable;
    }

    /** 是否需要辅导员确认（BR-F06）。 */
    public boolean requiresConfirmation() {
        return this == STU_CFM;
    }

    /** 按编码解析来源，未知编码回退学籍导入（最严格：任何角色都不可直接改）。 */
    public static FieldSourceEnum fromCode(String code) {
        if (code != null) {
            for (FieldSourceEnum source : values()) {
                if (source.name().equalsIgnoreCase(code.trim())) {
                    return source;
                }
            }
        }
        return SIS;
    }
}
