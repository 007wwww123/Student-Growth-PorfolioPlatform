package com.college.growth.archive.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.college.growth.archive.service.impl.UnfreezeServiceImpl;
import com.college.growth.auth.mapper.SysUserMapper;
import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.context.UserContext;
import com.college.growth.common.enums.ProfileStatusEnum;
import com.college.growth.common.enums.UserRoleEnum;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.log.service.AuditLogService;
import com.college.growth.common.log.service.ChangeLogService;
import com.college.growth.common.org.service.StudentClassService;
import com.college.growth.common.profile.entity.ArcProfile;
import com.college.growth.common.profile.mapper.ArcProfileMapper;
import com.college.growth.common.result.ResultCode;
import com.college.growth.common.student.mapper.StuStudentMapper;
import com.college.growth.grant.entity.SysApproval;
import com.college.growth.grant.mapper.SysApprovalMapper;
import com.college.growth.notification.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 归档解冻：只有全 LOCKED 可申请；审批通过回到 APPROVED。 */
@ExtendWith(MockitoExtension.class)
class UnfreezeServiceTest {

    static {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysApproval.class);
        TableInfoHelper.initTableInfo(assistant, ArcProfile.class);
    }

    @Mock private SysApprovalMapper approvalMapper;
    @Mock private ArcProfileMapper profileMapper;
    @Mock private StuStudentMapper studentMapper;
    @Mock private SysUserMapper userMapper;
    @Mock private StudentClassService studentClassService;
    @Mock private ChangeLogService changeLogService;
    @Mock private AuditLogService auditLogService;
    @Mock private NotificationService notificationService;

    private UnfreezeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UnfreezeServiceImpl(approvalMapper, profileMapper, studentMapper, userMapper,
                studentClassService, changeLogService, auditLogService, notificationService, new ObjectMapper());
        UserContext.set(new CurrentUser(9001L, UserRoleEnum.COU, false));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("cannot apply when one section is not locked")
    void notArchivedDenied() {
        when(profileMapper.selectList(any())).thenReturn(List.of(
                profile(ProfileStatusEnum.LOCKED), profile(ProfileStatusEnum.APPROVED),
                profile(ProfileStatusEnum.LOCKED), profile(ProfileStatusEnum.LOCKED),
                profile(ProfileStatusEnum.LOCKED), profile(ProfileStatusEnum.LOCKED)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.apply(1001L, "2025-2026-1", "reason should be long enough"));
        assertEquals(ResultCode.STATE_CONFLICT.getCode(), ex.getResultCode().getCode());
    }

    @Test
    @DisplayName("approve unlocks six sections and notifies student")
    void approveUnlocks() {
        UserContext.set(new CurrentUser(9003L, UserRoleEnum.ADMIN, false));
        SysApproval approval = new SysApproval();
        approval.setId(5L);
        approval.setApprovalType("UNFREEZE");
        approval.setApplicantId(9001L);
        approval.setStudentId(1001L);
        approval.setTargetJson("{\"year\":\"2025-2026-1\"}");
        approval.setReason("reason should be long enough");
        approval.setStatus("PENDING");
        when(approvalMapper.selectById(5L)).thenReturn(approval);
        when(profileMapper.selectList(any())).thenReturn(List.of(
                profile(ProfileStatusEnum.LOCKED), profile(ProfileStatusEnum.LOCKED),
                profile(ProfileStatusEnum.LOCKED), profile(ProfileStatusEnum.LOCKED),
                profile(ProfileStatusEnum.LOCKED), profile(ProfileStatusEnum.LOCKED)));

        var vo = service.approve(5L, "ok");

        assertEquals("APPROVED", vo.getStatus());
    }

    private ArcProfile profile(ProfileStatusEnum status) {
        ArcProfile profile = new ArcProfile();
        profile.setId(1L);
        profile.setStudentId(1001L);
        profile.setAcademicYear("2025-2026-1");
        profile.setSectionCode("BASIC");
        profile.setStatus(status.name());
        return profile;
    }
}
