package com.college.growth.permission.controller;

import com.college.growth.common.context.UserContext;
import com.college.growth.common.enums.UserRoleEnum;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.result.Result;
import com.college.growth.common.result.ResultCode;
import com.college.growth.permission.service.PermissionService;
import com.college.growth.permission.vo.PermissionVO;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 权限点查询：当前用户授权与全量字典。 */
@RestController
@RequestMapping("/permissions")
public class PermissionController {

    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    /** 当前登录用户的权限点（含数据范围），前端据此显示/隐藏入口。 */
    @GetMapping("/me")
    public Result<List<PermissionVO>> me() {
        return Result.success(permissionService.currentPermissions());
    }

    /** 全量权限字典，仅管理员可读。 */
    @GetMapping("/catalog")
    public Result<List<PermissionVO>> catalog() {
        if (UserContext.get().role() != UserRoleEnum.ADMIN) {
            throw new BusinessException(ResultCode.PERMISSION_DENIED, "仅管理员可以查看权限字典");
        }
        return Result.success(permissionService.catalog());
    }
}
