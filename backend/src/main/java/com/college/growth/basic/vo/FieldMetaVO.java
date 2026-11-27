package com.college.growth.basic.vo;

import com.college.growth.common.field.FieldOption;
import java.util.List;
import com.college.growth.common.field.entity.ArcFieldDef;
import lombok.Data;

/**
 * 字段渲染与权限位。
 *
 * <p>前端按本结构决定控件类型、是否必填、是否只读、是否展示「查看完整」，
 * 不要在前端另写一份字段表——字段定义的唯一来源是 {@code arc_field_def}。</p>
 */
@Data
public class FieldMetaVO {

    public static FieldMetaVO from(ArcFieldDef def, boolean editable, boolean masked) {
        FieldMetaVO meta = new FieldMetaVO();
        meta.setKey(def.getFieldCode().substring(def.getFieldCode().indexOf('.') + 1));
        meta.setFieldCode(def.getFieldCode());
        meta.setFieldName(def.getFieldName());
        meta.setFieldType(def.getFieldType());
        meta.setRequired(def.isRequired());
        meta.setSource(def.getSource());
        List<FieldOption> options = FieldOption.parse(def.getOptionsJson());
        meta.setOptions(options.isEmpty() ? null : options);
        meta.setSensitive(def.getSensitiveLevel());
        meta.setEditable(editable);
        meta.setMasked(masked);
        return meta;
    }

    /** 字段短名，与 {@link BasicSectionVO#getFields()} 的键一致。 */
    private String key;

    /** 字段编码，如 basic.studentNo。 */
    private String fieldCode;

    /** 界面显示名。 */
    private String fieldName;

    /** 控件类型：TEXT/TEXTAREA/NUMBER/DATE/SELECT/MULTI_SELECT/RADIO/CHECKBOX/FILE。 */
    private String fieldType;

    /** 提交时是否必填。 */
    private Boolean required;

    /** 来源：SIS/COU/STU/STU_CFM/SYS/IMP，前端据此展示「由学校维护」等提示。 */
    private String source;

    /** 枚举选项；非枚举字段为空。 */
    private List<FieldOption> options;

    /** 敏感级别：0 普通 / 1 脱敏 / 2 仅辅导员可查看完整值。 */
    private Integer sensitive;

    /** 当前角色在当次请求下是否可写。 */
    private Boolean editable;

    /** 本次返回的值是否已脱敏。 */
    private Boolean masked;

    /** 是否存在待辅导员确认的值。 */
    private Boolean pendingConfirm;

    /** 待确认值（如有），已按角色脱敏。 */
    private Object pendingValue;
}
