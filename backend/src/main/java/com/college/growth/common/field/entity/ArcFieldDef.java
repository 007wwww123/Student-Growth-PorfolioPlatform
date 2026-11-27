package com.college.growth.common.field.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 字段定义（物理表 {@code arc_field_def}）。
 *
 * <p>六个板块共用一张定义表，用 {@code section_code} 区分。任何表单渲染、校验、权限、
 * 必填统计都必须读本表，禁止在代码里维护第二份字段清单（BR-F02/F03）。</p>
 */
@Data
@TableName("arc_field_def")
public class ArcFieldDef {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 板块编码，如 BASIC。 */
    private String sectionCode;

    /** 字段编码，如 basic.name。 */
    private String fieldCode;

    /** 界面显示名。 */
    private String fieldName;

    /** 类型：TEXT/TEXTAREA/NUMBER/DATE/SELECT/MULTI_SELECT/RADIO/CHECKBOX/FILE。 */
    private String fieldType;

    /** 提交时是否必填。 */
    private Integer required;

    /** 来源：SIS/COU/STU/STU_CFM/SYS/IMP。 */
    private String source;

    /**
     * SIS 同步来源列名：{@code stu_student} 的列，如 {@code name}、{@code class_code}。
     *
     * <p>只有 {@code source=SIS} 的字段需要填；为空表示不从学生主数据同步。
     * 有了这一列，「哪些字段由主数据维护」也是定义驱动，新增字段不用改同步代码。</p>
     */
    private String sourceField;

    /** 可见角色，逗号分隔（不含管理员）。 */
    private String visibleRoles;

    /** 可编辑角色，逗号分隔；空表示均不可直接填。管理员可在权限配置中修改。 */
    private String editableRoles;

    /**
     * 默认可编辑角色（初始化时的口径），用于权限配置页展示「已调整」与「恢复默认」。
     *
     * <p>由脚本 {@code 11_add_field_permission_columns.sql} 从 {@code editable_roles} 回填；
     * 管理员保存只写 {@code editable_roles}，不改本列，因此默认值始终可追溯。</p>
     */
    private String defaultEditableRoles;

    /** 校验器标识。 */
    private String validator;

    /** 校验器参数 JSON。 */
    private String ruleJson;

    /** 枚举选项 JSON。 */
    private String optionsJson;

    /** 敏感级别：0 普通 / 1 脱敏展示 / 2 仅辅导员明文（列名 sensitive_level，SENSITIVE 为 MySQL 9 保留字）。 */
    private Integer sensitiveLevel;

    /** 表单顺序。 */
    private Integer sortNo;

    /** 1 启用 / 0 停用。 */
    private Integer status;

    /** 口径备注。 */
    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** 是否必填。 */
    public boolean isRequired() {
        return required != null && required == 1;
    }

    /** 是否启用。 */
    public boolean isEnabled() {
        return status != null && status == 1;
    }
}
