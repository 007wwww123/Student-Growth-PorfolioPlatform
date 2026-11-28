package com.college.growth.change.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.college.growth.auth.mapper.SysUserMapper;
import com.college.growth.change.service.impl.ChangeAppealServiceImpl;
import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.context.UserContext;
import com.college.growth.common.enums.UserRoleEnum;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.field.mapper.ArcFieldDefMapper;
import com.college.growth.common.log.entity.ArcChangeLog;
import com.college.growth.common.log.mapper.ArcChangeLogMapper;
import com.college.growth.common.log.service.AuditLogService;
import com.college.growth.common.org.mapper.OrgClassMapper;
import com.college.growth.common.org.service.StudentClassService;
import com.college.growth.common.result.ResultCode;
import com.college.growth.common.student.mapper.StuStudentMapper;
import com.college.growth.grant.entity.SysApproval;
import com.college.growth.grant.mapper.SysApprovalMapper;
import com.college.growth.notification.service.NotificationService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 申诉：本人限制、重复申诉、COU 本班处理。 */
@ExtendWith(MockitoExtension.class)
class ChangeAppealServiceTest {

    static {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ArcChangeLog.class);
        TableInfoHelper.initTableInfo(assistant, SysApproval.class);
    }

    @Mock private ArcChangeLogMapper changeLogMapper;
    @Mock private ArcFieldDefMapper fieldDefMapper;
    @Mock private StuStudentMapper studentMapper;
    @Mock private SysUserMapper userMapper;
    @Mock private SysApprovalMapper approvalMapper;
    @Mock private OrgClassMapper orgClassMapper;
    @Mock private StudentClassService studentClassService;
    @Mock private AuditLogService auditLogService;
    @Mock private NotificationService notificationService;

    private ChangeAppealServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ChangeAppealServiceImpl(changeLogMapper, fieldDefMapper, studentMapper, userMapper,
                approvalMapper, orgClassMapper, studentClassService, auditLogService, notificationService);
        UserContext.set(new CurrentUser(1001L, UserRoleEnum.STU, false));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("student appeals own change")
    void appealOwnChange() {
        when(changeLogMapper.selectById(7L)).thenReturn(change(7L, 1001L));
        when(approvalMapper.selectCount(any())).thenReturn(0L);
        when(approvalMapper.insert(any(SysApproval.class))).thenAnswer(invocation -> {
            SysApproval appeal = invocation.getArgument(0);
            appeal.setId(21L);
            return 1;
        });

        var vo = service.appeal(7L, "该字段不是本人填写");

        assertEquals(21L, vo.getId());
        assertEquals("PENDING", vo.getStatus());
        assertEquals("basic.phone", vo.getFieldCode());
    }

    @Test
    @DisplayName("student cannot appeal another student's change")
    void appealOtherStudentDenied() {
        when(changeLogMapper.selectById(7L)).thenReturn(change(7L, 1002L));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.appeal(7L, "该字段不是本人填写"));
        assertEquals(ResultCode.DATA_SCOPE_DENIED.getCode(), ex.getResultCode().getCode());
    }

    @Test
    @DisplayName("duplicate open appeal rejected")
    void duplicateAppealRejected() {
        when(changeLogMapper.selectById(7L)).thenReturn(change(7L, 1001L));
        when(approvalMapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.appeal(7L, "该字段不是本人填写"));
        assertEquals(ResultCode.STATE_CONFLICT.getCode(), ex.getResultCode().getCode());
    }

    @Test
    @DisplayName("counselor approves class student appeal")
    void counselorApproves() {
        UserContext.set(new CurrentUser(9001L, UserRoleEnum.COU, false));
        when(approvalMapper.selectById(21L)).thenReturn(pending());
        when(studentClassService.isCounselorOf(any(), any(), any())).thenReturn(true);
        when(changeLogMapper.selectById(7L)).thenReturn(change(7L, 1001L));

        var vo = service.approve(21L, "已核对，允许更正");

        assertEquals("APPROVED", vo.getStatus());
        assertEquals("已核对，允许更正", vo.getApproverRemark());
    }

    private ArcChangeLog change(Long id, Long studentId) {
        ArcChangeLog change = new ArcChangeLog();
        change.setId(id);
        change.setStudentId(studentId);
        change.setSectionCode("BASIC");
        change.setFieldCode("basic.phone");
        change.setBizType("FIELD_EDIT");
        change.setOldValue("138****0000");
        change.setNewValue("139****1111");
        return change;
    }

    private SysApproval pending() {
        SysApproval appeal = new SysApproval();
        appeal.setId(21L);
        appeal.setApprovalType("APPEAL");
        appeal.setApplicantId(1001L);
        appeal.setStudentId(1001L);
        appeal.setBizId(7L);
        appeal.setReason("该字段不是本人填写");
        appeal.setStatus("PENDING");
        return appeal;
    }
}
