package com.college.growth.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 登录日志（{@code sys_login_log}，脚本 21）。 */
@Data
@TableName("sys_login_log")
public class SysLoginLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String username;

    /** 1 成功 / 0 失败。 */
    private Integer success;

    /** 失败原因或动作。 */
    private String reason;

    private String ip;

    private String userAgent;

    private LocalDateTime createdAt;
}
