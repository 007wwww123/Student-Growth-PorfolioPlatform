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

    /** 可见角色，逗号分隔（不含管理员）。 */
    private String visibleRoles;

    /** 可编辑角色，逗号分隔；空表示均不可直接填。 */
    private String editableRoles;

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
