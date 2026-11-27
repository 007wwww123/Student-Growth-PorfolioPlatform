package com.college.growth.permission.vo;

import java.util.List;
import lombok.Data;

/**
 * 字段权限视图：既描述当前配置，也描述该字段是否允许配置。
 *
 * <p>{@code permissionMode} 是给界面用的四选一编号（NONE/STU/COU/BOTH），
 * 由 {@code editableRoles} 推导，不是第二个数据源；保存时仍以角色列表为准。</p>
 */
@Data
public class FieldPermissionVO {

    /** 字段编码，如 basic.name。 */
    private String fieldCode;

    /** 界面显示名。 */
    private String fieldName;

    /** 控件类型。 */
    private String fieldType;

    /** 值来源：SIS/COU/STU/STU_CFM/SYS/IMP。 */
    private String source;

    /** 是否必填。 */
    private Boolean required;

    /** 敏感级别：0 普通 / 1 脱敏 / 2 仅辅导员明文。 */
    private Integer sensitive;

    /** 当前生效的可编辑角色，按 STU、COU 的固定顺序返回。 */
    private List<String> editableRoles;

    /** 初始化时的默认可编辑角色，用于「恢复默认」。 */
    private List<String> defaultEditableRoles;

    /** 四选一权限模式：NONE / STU / COU / BOTH。 */
    private String permissionMode;

    /** 当前配置是否已偏离默认值。 */
    private Boolean customized;

    /** 学生是否可见该字段；不可见时不允许配置为「学生可编辑」。 */
    private Boolean studentVisible;

    /** 老师是否可见该字段；不可见时不允许配置为「老师可编辑」。 */
    private Boolean counselorVisible;

    /** 是否固定只读（系统推导、主键类字段），这类字段不允许配置。 */
    private Boolean locked;

    /** 固定只读原因，{@code locked=false} 时为空。 */
    private String lockedReason;
}
