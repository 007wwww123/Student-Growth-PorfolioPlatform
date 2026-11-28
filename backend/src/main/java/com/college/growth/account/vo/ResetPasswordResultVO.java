package com.college.growth.account.vo;

import lombok.Data;

/** 重置密码结果。 */
@Data
public class ResetPasswordResultVO {

    private Long userId;

    private String username;

    /** 新密码，仅本次响应返回。 */
    private String newPassword;
}
