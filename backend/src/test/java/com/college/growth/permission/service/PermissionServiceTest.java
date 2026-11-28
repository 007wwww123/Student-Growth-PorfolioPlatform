package com.college.growth.permission.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.context.UserContext;
import com.college.growth.common.enums.UserRoleEnum;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.result.ResultCode;
import com.college.growth.permission.entity.SysPermission;
import com.college.growth.permission.entity.SysRolePermission;
import com.college.growth.permission.mapper.SysPermissionMapper;
import com.college.growth.permission.mapper.SysRolePermissionMapper;
import com.college.growth.permission.vo.PermissionVO;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 权限点判定单测：角色矩阵、临时授权叠加、范围透出。 */
@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    static {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysPermission.class);
        TableInfoHelper.initTableInfo(assistant, SysRolePermission.class);
    }

    @Mock
    private SysPermissionMapper permissionMapper;

    @Mock
    private SysRolePermissionMapper rolePermissionMapper;

    private PermissionService service;

    @BeforeEach
    void setUp() {
        service = new PermissionService(permissionMapper, rolePermissionMapper);
        UserContext.set(new CurrentUser(9001L, UserRoleEnum.COU, false));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("辅导员有审核权限点，没有组织维护权限点")
    void counselorHasReviewButNotOrgManage() {
        when(rolePermissionMapper.selectList(any())).thenReturn(List.of(
                grant("archive:profile:review", "CLASS"),
                grant("archive:profile:read", "CLASS")));

        assertTrue(service.has("archive:profile:review"));
        assertFalse(service.has("org:class:manage"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.require("org:class:manage"));
        assertEquals(ResultCode.PERMISSION_DENIED.getCode(), ex.getResultCode().getCode());
    }

    @Test
    @DisplayName("管理员持临时授权时叠加档案只读权限")
    void adminTempGrantAddsReadPermission() {
        UserContext.set(new CurrentUser(9003L, UserRoleEnum.ADMIN, true));
        when(rolePermissionMapper.selectList(any())).thenReturn(List.of(
                grant("account:user:manage", "ALL")));

        assertTrue(service.has("archive:profile:read"));
        assertTrue(service.has("account:user:manage"));
    }

    @Test
    @DisplayName("权限清单带出数据范围")
    void currentPermissionsCarryScope() {
        when(rolePermissionMapper.selectList(any())).thenReturn(List.of(
                grant("archive:profile:read", "CLASS"),
                grant("archive:profile:review", "CLASS")));
        SysPermission read = permission("archive:profile:read", "档案读取");
        SysPermission review = permission("archive:profile:review", "审核");
        when(permissionMapper.selectList(any())).thenReturn(List.of(read, review));

        List<PermissionVO> permissions = service.currentPermissions();

        assertEquals(2, permissions.size());
        assertEquals("CLASS", permissions.get(0).getScope());
        assertEquals("archive:profile:read", permissions.get(0).getCode());
    }

    @Test
    @DisplayName("匿名上下文没有权限")
    void anonymousHasNoPermission() {
        UserContext.clear();
        assertFalse(service.has("archive:profile:read"));
    }

    private SysRolePermission grant(String code, String scope) {
        SysRolePermission grant = new SysRolePermission();
        grant.setRole(UserRoleEnum.COU.name());
        grant.setPermissionCode(code);
        grant.setScope(scope);
        return grant;
    }

    private SysPermission permission(String code, String name) {
        SysPermission permission = new SysPermission();
        permission.setCode(code);
        permission.setName(name);
        permission.setModule("archive");
        return permission;
    }
}
