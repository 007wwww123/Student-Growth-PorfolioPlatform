package com.college.growth.audit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.college.growth.audit.entity.SysLoginLog;
import com.college.growth.audit.mapper.SysLoginLogMapper;
import com.college.growth.audit.vo.LoginLogVO;
import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.context.UserContext;
import com.college.growth.common.enums.UserRoleEnum;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.log.entity.SysAuditLog;
import com.college.growth.common.log.mapper.SysAuditLogMapper;
import com.college.growth.common.result.PageResult;
import com.college.growth.common.result.ResultCode;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 审计查询：登录日志仅管理员、匿名拒绝、分页映射。 */
@ExtendWith(MockitoExtension.class)
class AuditQueryServiceTest {

    static {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysAuditLog.class);
        TableInfoHelper.initTableInfo(assistant, SysLoginLog.class);
    }

    @Mock
    private SysAuditLogMapper auditLogMapper;

    @Mock
    private SysLoginLogMapper loginLogMapper;

    private AuditQueryService service;

    @BeforeEach
    void setUp() {
        service = new AuditQueryService(auditLogMapper, loginLogMapper);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("anonymous cannot query audit logs")
    void anonymousDenied() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.logs(null, null, null, null, null, null, 1, 20));
        assertEquals(ResultCode.UNAUTHORIZED.getCode(), ex.getResultCode().getCode());
    }

    @Test
    @DisplayName("counselor cannot query login logs")
    void counselorCannotQueryLoginLogs() {
        UserContext.set(new CurrentUser(9001L, UserRoleEnum.COU, false));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.loginLogs(null, null, null, null, 1, 20));
        assertEquals(ResultCode.PERMISSION_DENIED.getCode(), ex.getResultCode().getCode());
    }

    @Test
    @DisplayName("admin login log page maps rows")
    void adminLoginLogPage() {
        UserContext.set(new CurrentUser(9003L, UserRoleEnum.ADMIN, false));
        SysLoginLog row = new SysLoginLog();
        row.setId(1L);
        row.setUsername("2024010101");
        row.setSuccess(0);
        row.setReason("EC-40104");
        Page<SysLoginLog> page = new Page<>(1, 20);
        page.setRecords(List.of(row));
        page.setTotal(1);
        when(loginLogMapper.selectPage(any(), any())).thenReturn(page);

        PageResult<LoginLogVO> result = service.loginLogs(null, null, null, null, 1, 20);

        assertEquals(1, result.getTotal());
        assertEquals("2024010101", result.getRecords().get(0).getUsername());
        assertEquals(0, result.getRecords().get(0).getSuccess());
    }
}
