package com.college.growth.account.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 角色分配入参。 */
@Data
public class RoleAssignDTO {

    @NotBlank(message = "角色不能为空")
    private String role;
}
