package com.college.growth.export.classic.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.college.growth.auth.entity.SysUser;
import com.college.growth.auth.mapper.SysUserMapper;
import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.context.UserContext;
import com.college.growth.common.enums.UserRoleEnum;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.log.service.AuditLogService;
import com.college.growth.common.org.entity.OrgClass;
import com.college.growth.common.org.mapper.OrgClassMapper;
import com.college.growth.common.profile.mapper.ArcProfileItemMapper;
import com.college.growth.common.profile.mapper.ArcProfileMapper;
import com.college.growth.common.result.ResultCode;
import com.college.growth.common.student.mapper.StuStudentMapper;
import com.college.growth.export.classic.dto.ClassExportDTO;
import com.college.growth.export.classic.service.impl.ClassExportServiceImpl;
import com.college.growth.export.entity.SysExportTask;
import com.college.growth.export.mapper.SysExportTaskMapper;
import com.college.growth.grant.entity.SysApproval;
import com.college.growth.grant.mapper.SysApprovalMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 班级导出：本班限制、敏感列审批。 */
@ExtendWith(MockitoExtension.class)
class ClassExportServiceTest {

    static {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, OrgClass.class);
        TableInfoHelper.initTableInfo(assistant, SysApproval.class);
    }

    @Mock private OrgClassMapper orgClassMapper;
    @Mock private StuStudentMapper studentMapper;
    @Mock private ArcProfileMapper profileMapper;
    @Mock private ArcProfileItemMapper itemMapper;
    @Mock private SysApprovalMapper approvalMapper;
    @Mock private SysExportTaskMapper taskMapper;
    @Mock private SysUserMapper userMapper;
    @Mock private AuditLogService auditLogService;

    @TempDir
    Path tempDir;

    private ClassExportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ClassExportServiceImpl(orgClassMapper, studentMapper, profileMapper, itemMapper,
                approvalMapper, taskMapper, userMapper, auditLogService, new ObjectMapper(), tempDir.toString());
        UserContext.set(new CurrentUser(9001L, UserRoleEnum.COU, false));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("counselor cannot export another class")
    void otherClassDenied() {
        when(orgClassMapper.selectOne(any())).thenReturn(orgClass(9002L));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(dto(List.of("studentNo"), null)));
        assertEquals(ResultCode.DATA_SCOPE_DENIED.getCode(), ex.getResultCode().getCode());
    }

    @Test
    @DisplayName("sensitive column without approval denied")
    void sensitiveWithoutApprovalDenied() {
        when(orgClassMapper.selectOne(any())).thenReturn(orgClass(9001L));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(dto(List.of("studentNo", "idCard"), null)));
        assertEquals(ResultCode.SENSITIVE_EXPORT_DENIED.getCode(), ex.getResultCode().getCode());
    }

    @Test
    @DisplayName("approved sensitive export writes task")
    void approvedSensitiveExport() {
        when(orgClassMapper.selectOne(any())).thenReturn(orgClass(9001L));
        when(approvalMapper.selectById(5L)).thenReturn(approval());
        when(studentMapper.selectList(any())).thenReturn(List.of());
        when(userMapper.selectById(9001L)).thenReturn(user());
        when(taskMapper.insert(any(SysExportTask.class))).thenAnswer(invocation -> {
            SysExportTask task = invocation.getArgument(0);
            task.setId(31L);
            return 1;
        });

        var vo = service.create(dto(List.of("studentNo", "idCard", "phone"), 5L));

        assertEquals(31L, vo.getId());
        assertEquals("SUCCESS", vo.getStatus());
        assertEquals("class-RJ2401.xlsx", vo.getFileName());
    }

    @Test
    @DisplayName("approver grants four-hour window")
    void approve() {
        UserContext.set(new CurrentUser(9003L, UserRoleEnum.ADMIN, false));
        when(approvalMapper.selectById(5L)).thenReturn(pendingApproval());

        var vo = service.approve(5L, "ok");

        assertEquals("APPROVED", vo.getStatus());
        assertEquals(true, vo.isActive());
    }

    private ClassExportDTO dto(List<String> columns, Long approvalId) {
        ClassExportDTO dto = new ClassExportDTO();
        dto.setClassCode("RJ2401");
        dto.setAcademicYear("2025-2026-1");
        dto.setColumns(columns);
        dto.setApprovalId(approvalId);
        return dto;
    }

    private OrgClass orgClass(Long counselorId) {
        OrgClass orgClass = new OrgClass();
        orgClass.setClassCode("RJ2401");
        orgClass.setClassName("软件2401");
        orgClass.setCounselorUserId(counselorId);
        orgClass.setStatus(1);
        return orgClass;
    }

    private SysApproval approval() {
        SysApproval approval = new SysApproval();
        approval.setId(5L);
        approval.setApprovalType("EXPORT_SENSITIVE");
        approval.setApplicantId(9001L);
        approval.setTargetJson("{\"classCode\":\"RJ2401\"}");
        approval.setReason("class export check");
        approval.setStatus("APPROVED");
        approval.setExpireAt(LocalDateTime.now().plusHours(2));
        return approval;
    }

    private SysApproval pendingApproval() {
        SysApproval approval = approval();
        approval.setStatus("PENDING");
        approval.setExpireAt(null);
        return approval;
    }

    private SysUser user() {
        SysUser user = new SysUser();
        user.setId(9001L);
        user.setDisplayName("Counselor");
        return user;
    }
}
