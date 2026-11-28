package com.college.growth.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改本人密码入参（F-01 / 7.2 {@code auth:password:self}）。
 *
 * <p>原密码必填：令牌可能被别人短暂拿到，不能只凭令牌改密。新密码长度在 DTO
 * 先拦一道，字符类别由 {@code PasswordPolicy} 校验并返回未满足项（BR-A02）。</p>
 */
@Data
public class ChangePasswordDTO {

    @NotBlank(message = "原密码不能为空")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 20, message = "密码长度应为 8–20 位")
    private String newPassword;
}
