package com.college.growth.permission.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.Data;

/**
 * 单个字段的权限配置项。
 *
 * <p>用角色列表而不是「仅学生/仅老师」两个布尔位，是为了保持与
 * {@code arc_field_def.editable_roles} 的同一口径：空列表即只读，
 * 增加第三种角色时不需要改表结构。</p>
 */
@Data
public class FieldPermissionDTO {

    /** 字段编码，如 basic.name。 */
    @NotBlank(message = "字段编码不能为空")
    private String fieldCode;

    /** 可编辑角色，只能包含 STU / COU；空列表表示任何角色都不可编辑。 */
    private List<String> editableRoles;
}
