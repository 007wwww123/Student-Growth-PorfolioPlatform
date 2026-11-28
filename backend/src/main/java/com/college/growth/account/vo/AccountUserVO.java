package com.college.growth.account.vo;

import java.time.LocalDateTime;
import lombok.Data;

/** 账号列表行。 */
@Data
public class AccountUserVO {

    private Long id;

    private String username;

    private String displayName;

    private String role;

    private String roleLabel;

    private Long studentId;

    private String studentNo;

    private String classCode;

    private String className;

    private Integer status;

    private LocalDateTime lastLoginAt;
}
