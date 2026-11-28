package com.college.growth.permission.controller;

import com.college.growth.common.result.Result;
import com.college.growth.permission.common.PermissionConstants;
import com.college.growth.permission.dto.PermissionSaveDTO;
import com.college.growth.permission.service.FieldPermissionService;
import com.college.growth.permission.support.RequirePermission;
import com.college.growth.permission.vo.PermissionMatrixVO;
import com.college.growth.permission.vo.PermissionSaveResultVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 字段编辑权限配置接口（管理员模式）。
 *
 * <p>路径刻意与六个板块接口分离：管理员的职责是配置权限，不进入
 * {@code /sections/**} 的内容读写链路。权限校验在 Service 层完成，
 * 非 ADMIN 一律 EC-40303，Controller 不做角色判断。</p>
 */
@RestController
@RequestMapping(PermissionConstants.API_BASE_PATH)
@RequirePermission("config:field:manage")
public class FieldPermissionController {

    private final FieldPermissionService fieldPermissionService;

    public FieldPermissionController(FieldPermissionService fieldPermissionService) {
        this.fieldPermissionService = fieldPermissionService;
    }

    /** 读取六大板块的字段权限矩阵（每次实时读库，保证配置与生效一致）。 */
    @GetMapping
    public Result<PermissionMatrixVO> matrix() {
        return Result.success(fieldPermissionService.matrix());
    }

    /** 保存某板块的字段权限，保存即生效。 */
    @PutMapping("/{sectionCode}")
    public Result<PermissionSaveResultVO> save(@PathVariable String sectionCode,
                                               @Valid @RequestBody PermissionSaveDTO dto) {
        return Result.success(fieldPermissionService.save(sectionCode, dto));
    }

    /** 恢复某板块的默认权限。 */
    @PostMapping("/{sectionCode}/reset")
    public Result<PermissionSaveResultVO> reset(@PathVariable String sectionCode) {
        return Result.success(fieldPermissionService.reset(sectionCode));
    }
}
