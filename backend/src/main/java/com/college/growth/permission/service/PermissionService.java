package com.college.growth.permission.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 权限点判定（需求 7.2 / IT-1-04）。
 *
 * <p>权限点与数据范围分两层：本服务只回答「这个角色有没有这个动作」，
 * 「本班 / 本人」的范围仍由各业务 Service 用 {@code StudentClassService} 判定。
 * 这样权限表可以集中维护，数据范围逻辑不会散落到权限码里。</p>
 *
 * <p>管理员临时授权（BR-S03）不写入角色权限表，而是在判定时叠加一条只读权限，
 * 授权到期只需把 {@code temp_grant} 置 0，不需要改权限配置。</p>
 */
@Service
public class PermissionService {

    private static final Logger log = LoggerFactory.getLogger(PermissionService.class);

    /** 管理员临时授权额外获得的只读权限。 */
    public static final String TEMP_GRANT_READ = "archive:profile:read";

    private final SysPermissionMapper permissionMapper;

    private final SysRolePermissionMapper rolePermissionMapper;

    public PermissionService(SysPermissionMapper permissionMapper,
                             SysRolePermissionMapper rolePermissionMapper) {
        this.permissionMapper = permissionMapper;
        this.rolePermissionMapper = rolePermissionMapper;
    }

    /** 当前用户是否具备某权限点。 */
    public boolean has(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        CurrentUser user = UserContext.get();
        if (user.userId() == null || user.userId() <= 0 || user.role() == null) {
            return false;
        }
        return scopesOf(user.role(), user.tempGrant()).containsKey(code);
    }

    /** 没有权限时抛 EC-40303。 */
    public void require(String code) {
        if (!has(code)) {
            log.warn("权限不足 code={} userId={} role={}", code,
                    UserContext.get().userId(), UserContext.get().role());
            throw new BusinessException(ResultCode.PERMISSION_DENIED, "缺少权限：" + code);
        }
    }

    /** 返回某角色的权限点及数据范围（不含临时授权叠加）。 */
    public Map<String, String> scopesOf(UserRoleEnum role, boolean tempGrant) {
        Map<String, String> scopes = new HashMap<>();
        if (role == null) {
            return scopes;
        }
        List<SysRolePermission> grants = rolePermissionMapper.selectList(
                Wrappers.<SysRolePermission>lambdaQuery()
                        .eq(SysRolePermission::getRole, role.name()));
        for (SysRolePermission grant : grants) {
            scopes.put(grant.getPermissionCode(), grant.getScope());
        }
        if (role == UserRoleEnum.ADMIN && tempGrant) {
            scopes.putIfAbsent(TEMP_GRANT_READ, "ALL");
        }
        return scopes;
    }

    /** 当前用户的权限点清单（供前端按权限显示入口）。 */
    public List<PermissionVO> currentPermissions() {
        CurrentUser user = UserContext.get();
        Map<String, String> scopes = scopesOf(user.role(), user.tempGrant());
        if (scopes.isEmpty()) {
            return List.of();
        }
        List<SysPermission> permissions = permissionMapper.selectList(
                Wrappers.<SysPermission>lambdaQuery()
                        .eq(SysPermission::getStatus, 1)
                        .in(SysPermission::getCode, scopes.keySet())
                        .orderByAsc(SysPermission::getModule)
                        .orderByAsc(SysPermission::getCode));
        List<PermissionVO> result = new ArrayList<>(permissions.size());
        for (SysPermission permission : permissions) {
            PermissionVO vo = toVO(permission);
            vo.setScope(scopes.get(permission.getCode()));
            result.add(vo);
        }
        return result;
    }

    /** 权限点字典（管理员维护授权时使用）。 */
    public List<PermissionVO> catalog() {
        return permissionMapper.selectList(Wrappers.<SysPermission>lambdaQuery()
                        .eq(SysPermission::getStatus, 1)
                        .orderByAsc(SysPermission::getModule)
                        .orderByAsc(SysPermission::getCode))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    private PermissionVO toVO(SysPermission permission) {
        PermissionVO vo = new PermissionVO();
        vo.setCode(permission.getCode());
        vo.setName(permission.getName());
        vo.setModule(permission.getModule());
        vo.setDescription(permission.getDescription());
        return vo;
    }
}
