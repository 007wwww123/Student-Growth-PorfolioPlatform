package com.college.growth.notification.vo;

import java.time.LocalDateTime;
import lombok.Data;

/** 通知视图。 */
@Data
public class NotificationVO {

    private Long id;

    private String type;

    private String title;

    private String content;

    private String bizType;

    private Long bizId;

    private boolean read;

    private LocalDateTime readAt;

    private LocalDateTime createdAt;
}
