package com.college.growth.account.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** 重置密码入参；为空则生成随机密码并返回一次。 */
@Data
public class ResetPasswordDTO {

    @Size(min = 8, max = 20, message = "密码长度应为 8-20 位")
    private String password;
}
