package com.college.growth.account.vo;

import lombok.Data;

/** 新建账号结果：初始密码只在此处返回一次。 */
@Data
public class CreateAccountResultVO {

    private Long id;

    private String username;

    private String displayName;

    private String role;

    private String roleLabel;

    /** 初始密码，仅本次响应返回。 */
    private String initialPassword;
}
