package com.college.growth.export.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.college.growth.academic.record.mapper.ArcCertificateMapper;
import com.college.growth.academic.record.mapper.ArcEventMapper;
import com.college.growth.auth.mapper.SysUserMapper;
import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.context.UserContext;
import com.college.growth.common.enums.ProfileStatusEnum;
import com.college.growth.common.enums.UserRoleEnum;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.field.mapper.ArcFieldDefMapper;
import com.college.growth.common.log.service.AuditLogService;
import com.college.growth.common.org.mapper.OrgClassMapper;
import com.college.growth.common.org.service.StudentClassService;
import com.college.growth.common.profile.entity.ArcProfile;
import com.college.growth.common.profile.mapper.ArcProfileItemMapper;
import com.college.growth.common.profile.mapper.ArcProfileMapper;
import com.college.growth.common.result.ResultCode;
import com.college.growth.common.student.mapper.StuStudentMapper;
import com.college.growth.export.entity.SysExportTask;
import com.college.growth.export.mapper.SysExportTaskMapper;
import com.college.growth.export.service.impl.ProfileExportServiceImpl;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 导出权限与有效期。 */
@ExtendWith(MockitoExtension.class)
class ProfileExportServiceTest {

    static {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ArcProfile.class);
        TableInfoHelper.initTableInfo(assistant, SysExportTask.class);
    }

    @Mock private StuStudentMapper studentMapper;
    @Mock private OrgClassMapper classMapper;
    @Mock private ArcProfileMapper profileMapper;
    @Mock private ArcProfileItemMapper itemMapper;
    @Mock private ArcFieldDefMapper fieldDefMapper;
    @Mock private ArcCertificateMapper certificateMapper;
    @Mock private ArcEventMapper eventMapper;
    @Mock private SysUserMapper userMapper;
    @Mock private SysExportTaskMapper taskMapper;
    @Mock private StudentClassService studentClassService;
    @Mock private AuditLogService auditLogService;
    @Mock private ProfilePdfRenderer renderer;

    private ProfileExportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ProfileExportServiceImpl(studentMapper, classMapper, profileMapper, itemMapper,
                fieldDefMapper, certificateMapper, eventMapper, userMapper, taskMapper,
                studentClassService, auditLogService, renderer,
                System.getProperty("java.io.tmpdir") + "/student-growth-export-test");
        UserContext.set(new CurrentUser(9001L, UserRoleEnum.COU, false));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("student export requires all sections approved")
    void studentExportRequiresApproved() {
        UserContext.set(new CurrentUser(1001L, UserRoleEnum.STU, false));
        when(profileMapper.selectList(any())).thenReturn(profilesWithDraft());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(1001L, "2025-2026-1"));
        assertEquals(ResultCode.EXPORT_NOT_READY.getCode(), ex.getResultCode().getCode());
    }

    @Test
    @DisplayName("expired task cannot be downloaded")
    void expiredTaskRejected() {
        SysExportTask task = task(9001L, LocalDateTime.now().minusHours(1));
        when(taskMapper.selectById(5L)).thenReturn(task);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.download(5L));
        assertEquals(ResultCode.EXPORT_EXPIRED.getCode(), ex.getResultCode().getCode());
    }

    @Test
    @DisplayName("cannot read another user's export task")
    void otherUserTaskDenied() {
        when(taskMapper.selectById(5L)).thenReturn(task(9002L, LocalDateTime.now().plusHours(1)));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.status(5L));
        assertEquals(ResultCode.DATA_SCOPE_DENIED.getCode(), ex.getResultCode().getCode());
    }

    private List<ArcProfile> profilesWithDraft() {
        List<ArcProfile> profiles = new ArrayList<>();
        for (ProfileStatusEnum status : ProfileStatusEnum.values()) {
            ArcProfile profile = new ArcProfile();
            profile.setSectionCode(status.name());
            profile.setStatus(status == ProfileStatusEnum.DRAFT ? "DRAFT" : "APPROVED");
            profiles.add(profile);
        }
        return profiles;
    }

    private SysExportTask task(Long operatorId, LocalDateTime expireAt) {
        SysExportTask task = new SysExportTask();
        task.setId(5L);
        task.setOperatorId(operatorId);
        task.setStatus("SUCCESS");
        task.setExpireAt(expireAt);
        task.setFilePath("nope.pdf");
        return task;
    }
}
