package com.college.growth.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 登录入参。
 *
 * <p>密码只做长度上限，不在这里套复杂度规则：复杂度在设置密码时校验，
 * 登录阶段应尽量少泄露「密码格式」信息。</p>
 */
@Data
public class LoginDTO {

    @NotBlank(message = "请输入登录名")
    @Size(max = 64, message = "登录名过长")
    private String username;

    @NotBlank(message = "请输入密码")
    @Size(max = 128, message = "密码过长")
    private String password;
}
