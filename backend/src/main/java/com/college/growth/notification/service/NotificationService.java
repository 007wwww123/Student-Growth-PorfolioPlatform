package com.college.growth.notification.service;

import com.college.growth.common.result.PageResult;
import com.college.growth.notification.vo.NotificationVO;

/** 站内通知（S-1-05/07 / B-5）。 */
public interface NotificationService {

    void notify(Long userId, String type, String title, String content, String bizType, Long bizId);

    void notifyCounselor(Long studentId, String academicYear, String type, String title,
                         String content, String bizType, Long bizId);

    PageResult<NotificationVO> list(boolean unreadOnly, long pageNum, long pageSize);

    long unreadCount();

    void markRead(Long id);

    void markAllRead();
}
