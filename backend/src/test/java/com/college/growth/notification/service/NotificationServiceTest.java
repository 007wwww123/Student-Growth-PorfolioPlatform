package com.college.growth.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.context.UserContext;
import com.college.growth.common.enums.UserRoleEnum;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.org.service.StudentClassService;
import com.college.growth.common.result.ResultCode;
import com.college.growth.notification.entity.SysNotification;
import com.college.growth.notification.mapper.SysNotificationMapper;
import com.college.growth.notification.service.impl.NotificationServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 通知：写入、未读数、越权标记已读。 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    static {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                SysNotification.class);
    }

    @Mock private SysNotificationMapper mapper;
    @Mock private StudentClassService studentClassService;

    private NotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NotificationServiceImpl(mapper, studentClassService);
        UserContext.set(new CurrentUser(1001L, UserRoleEnum.STU, false));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("notify inserts one row")
    void notifyInserts() {
        service.notify(1001L, "REVIEW_APPROVED", "通过", "BASIC 已通过", "PROFILE", 7L);
        org.mockito.Mockito.verify(mapper).insert(any(SysNotification.class));
    }

    @Test
    @DisplayName("unread count scoped to current user")
    void unreadCount() {
        when(mapper.selectCount(any())).thenReturn(3L);
        assertEquals(3L, service.unreadCount());
    }

    @Test
    @DisplayName("cannot mark another user's notification")
    void markOtherDenied() {
        SysNotification row = new SysNotification();
        row.setId(5L);
        row.setUserId(1002L);
        when(mapper.selectById(5L)).thenReturn(row);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.markRead(5L));
        assertEquals(ResultCode.DATA_SCOPE_DENIED.getCode(), ex.getResultCode().getCode());
    }
}
