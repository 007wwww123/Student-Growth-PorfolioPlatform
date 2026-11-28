package com.college.growth.config.field.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/** 字段定义新增/修改入参（管理员）。 */
@Data
public class FieldDefSaveDTO {

    /** 板块编码，创建时必填；修改时忽略。 */
    private String sectionCode;

    /** 字段编码，创建时必填；修改时不可变更。 */
    @Size(max = 64, message = "字段编码不超过 64 字符")
    private String fieldCode;

    @NotBlank(message = "字段名称不能为空")
    @Size(max = 64, message = "字段名称不超过 64 字符")
    private String fieldName;

    @NotBlank(message = "字段类型不能为空")
    private String fieldType;

    /** 0 选填 / 1 必填。 */
    private Integer required;

    @NotBlank(message = "字段来源不能为空")
    private String source;

    /** 可见角色：STU / COU。 */
    private List<String> visibleRoles;

    /** 可编辑角色，必须是可见角色的子集。 */
    private List<String> editableRoles;

    /** 校验器标识，留空时按类型给默认值。 */
    private String validator;

    private String ruleJson;

    private String optionsJson;

    /** 0 普通 / 1 脱敏 / 2 仅辅导员明文。 */
    private Integer sensitiveLevel;

    private Integer sortNo;

    @Size(max = 255, message = "备注不超过 255 字符")
    private String remark;
}
