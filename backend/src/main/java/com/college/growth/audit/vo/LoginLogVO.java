package com.college.growth.audit.vo;

import java.time.LocalDateTime;
import lombok.Data;

/** 登录日志视图。 */
@Data
public class LoginLogVO {

    private Long id;

    private Long userId;

    private String username;

    private Integer success;

    private String reason;

    private String ip;

    private String userAgent;

    private LocalDateTime createdAt;
}
