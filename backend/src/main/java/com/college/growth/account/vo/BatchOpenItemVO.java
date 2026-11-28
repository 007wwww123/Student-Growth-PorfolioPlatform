package com.college.growth.account.vo;

import lombok.Data;

/** 批量开通的单条结果：初始密码随首次下发返回。 */
@Data
public class BatchOpenItemVO {

    private Long studentId;

    private String studentNo;

    private String studentName;

    private String username;

    private String initialPassword;
}
