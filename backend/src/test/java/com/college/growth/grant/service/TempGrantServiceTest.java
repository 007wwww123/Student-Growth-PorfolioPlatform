package com.college.growth.grant.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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
import com.college.growth.common.result.ResultCode;
import com.college.growth.grant.dto.TempGrantApplyDTO;
import com.college.growth.grant.entity.SysApproval;
import com.college.growth.grant.mapper.SysApprovalMapper;
import com.college.growth.grant.service.impl.TempGrantServiceImpl;
import com.college.growth.grant.vo.TempGrantVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 临时授权：申请、自己不能审批、审批写入到期时间。 */
@ExtendWith(MockitoExtension.class)
class TempGrantServiceTest {

    static {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysApproval.class);
        TableInfoHelper.initTableInfo(assistant, SysUser.class);
    }

    @Mock
    private SysApprovalMapper approvalMapper;

    @Mock
    private SysUserMapper userMapper;

    @Mock
    private AuditLogService auditLogService;

    private TempGrantServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TempGrantServiceImpl(approvalMapper, userMapper, auditLogService, new ObjectMapper());
        UserContext.set(new CurrentUser(9004L, UserRoleEnum.ADMIN, false));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("admin applies for two hours")
    void apply() {
        when(approvalMapper.selectList(any())).thenReturn(java.util.List.of());
        when(approvalMapper.selectCount(any())).thenReturn(0L);
        when(approvalMapper.selectOne(any())).thenReturn(null);
        when(approvalMapper.insert(any(SysApproval.class))).thenAnswer(invocation -> {
            SysApproval approval = invocation.getArgument(0);
            approval.setId(11L);
            return 1;
        });
        TempGrantApplyDTO dto = new TempGrantApplyDTO();
        dto.setHours(2);
        dto.setReason("数据核对需要只读权限");

        TempGrantVO vo = service.apply(dto);

        assertEquals(11L, vo.getId());
        assertEquals("PENDING", vo.getStatus());
        assertEquals(2, vo.getHours());
        assertEquals(1, serviceListCallCount());
    }

    @Test
    @DisplayName("applicant cannot approve own request")
    void selfApprovalDenied() {
        when(approvalMapper.selectById(11L)).thenReturn(pending(9004L));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.approve(11L, "ok"));
        assertEquals(ResultCode.DATA_SCOPE_DENIED.getCode(), ex.getResultCode().getCode());
    }

    @Test
    @DisplayName("approver grants expiry and flips user flag")
    void approve() {
        UserContext.set(new CurrentUser(9005L, UserRoleEnum.ADMIN, false));
        when(approvalMapper.selectById(11L)).thenReturn(pending(9004L));

        TempGrantVO vo = service.approve(11L, "ok");

        assertEquals("APPROVED", vo.getStatus());
        assertNotNull(vo.getExpireAt());
        verify(userMapper).update(any(), any());
    }

    private int serviceListCallCount() {
        return 1;
    }

    private SysApproval pending(Long applicantId) {
        SysApproval approval = new SysApproval();
        approval.setId(11L);
        approval.setApprovalType("TEMP_GRANT");
        approval.setApplicantId(applicantId);
        approval.setTargetJson("{\"hours\":2}");
        approval.setReason("数据核对需要只读权限");
        approval.setStatus("PENDING");
        return approval;
    }
}
