package com.college.growth.notification.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.context.UserContext;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.org.entity.OrgClass;
import com.college.growth.common.org.service.StudentClassService;
import com.college.growth.common.result.PageResult;
import com.college.growth.common.result.ResultCode;
import com.college.growth.notification.entity.SysNotification;
import com.college.growth.notification.mapper.SysNotificationMapper;
import com.college.growth.notification.service.NotificationService;
import com.college.growth.notification.vo.NotificationVO;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 站内通知实现：业务事务内写入，失败只记日志不阻断主流程。 */
@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private final SysNotificationMapper mapper;

    private final StudentClassService studentClassService;

    public NotificationServiceImpl(SysNotificationMapper mapper, StudentClassService studentClassService) {
        this.mapper = mapper;
        this.studentClassService = studentClassService;
    }

    @Override
    public void notify(Long userId, String type, String title, String content, String bizType, Long bizId) {
        if (userId == null || userId <= 0) {
            return;
        }
        try {
            SysNotification notification = new SysNotification();
            notification.setUserId(userId);
            notification.setType(type);
            notification.setTitle(title);
            notification.setContent(content);
            notification.setBizType(bizType);
            notification.setBizId(bizId);
            mapper.insert(notification);
        } catch (Exception ex) {
            log.warn("写通知失败 userId={} type={}", userId, type, ex);
        }
    }

    @Override
    public void notifyCounselor(Long studentId, String academicYear, String type, String title,
                                String content, String bizType, Long bizId) {
        try {
            OrgClass orgClass = studentClassService.findClass(studentId, academicYear);
            if (orgClass != null && orgClass.getCounselorUserId() != null) {
                notify(orgClass.getCounselorUserId(), type, title, content, bizType, bizId);
            }
        } catch (Exception ex) {
            log.warn("找辅导员写通知失败 studentId={}", studentId, ex);
        }
    }

    @Override
    public PageResult<NotificationVO> list(boolean unreadOnly, long pageNum, long pageSize) {
        CurrentUser user = requireUser();
        IPage<SysNotification> page = mapper.selectPage(
                new Page<>(Math.max(1, pageNum), Math.min(Math.max(1, pageSize), 100)),
                Wrappers.<SysNotification>lambdaQuery()
                        .eq(SysNotification::getUserId, user.userId())
                        .isNull(unreadOnly, SysNotification::getReadAt)
                        .orderByDesc(SysNotification::getId));
        List<NotificationVO> rows = page.getRecords().stream().map(this::toVO).toList();
        return new PageResult<>(rows, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public long unreadCount() {
        CurrentUser user = requireUser();
        return mapper.selectCount(Wrappers.<SysNotification>lambdaQuery()
                .eq(SysNotification::getUserId, user.userId())
                .isNull(SysNotification::getReadAt));
    }

    @Override
    @Transactional
    public void markRead(Long id) {
        CurrentUser user = requireUser();
        SysNotification notification = id == null ? null : mapper.selectById(id);
        if (notification == null || !user.userId().equals(notification.getUserId())) {
            throw new BusinessException(ResultCode.DATA_SCOPE_DENIED, "通知不存在或无权操作");
        }
        if (notification.getReadAt() == null) {
            notification.setReadAt(LocalDateTime.now());
            mapper.updateById(notification);
        }
    }

    @Override
    @Transactional
    public void markAllRead() {
        CurrentUser user = requireUser();
        mapper.update(null, Wrappers.<SysNotification>lambdaUpdate()
                .eq(SysNotification::getUserId, user.userId())
                .isNull(SysNotification::getReadAt)
                .set(SysNotification::getReadAt, LocalDateTime.now()));
    }

    private CurrentUser requireUser() {
        CurrentUser user = UserContext.get();
        if (user == null || user.userId() == null || user.userId() <= 0) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return user;
    }

    private NotificationVO toVO(SysNotification notification) {
        NotificationVO vo = new NotificationVO();
        vo.setId(notification.getId());
        vo.setType(notification.getType());
        vo.setTitle(notification.getTitle());
        vo.setContent(notification.getContent());
        vo.setBizType(notification.getBizType());
        vo.setBizId(notification.getBizId());
        vo.setRead(notification.getReadAt() != null);
        vo.setReadAt(notification.getReadAt());
        vo.setCreatedAt(notification.getCreatedAt());
        return vo;
    }
}
