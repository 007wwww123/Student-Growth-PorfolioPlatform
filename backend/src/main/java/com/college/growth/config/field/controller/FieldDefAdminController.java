package com.college.growth.config.field.controller;

import com.college.growth.common.dto.StatusUpdateDTO;
import com.college.growth.common.result.Result;
import com.college.growth.config.field.dto.FieldDefSaveDTO;
import com.college.growth.config.field.dto.FieldRequiredDTO;
import com.college.growth.config.field.service.FieldDefAdminService;
import com.college.growth.config.field.vo.FieldConfigMetaVO;
import com.college.growth.config.field.vo.FieldDefVO;
import com.college.growth.permission.support.RequirePermission;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 字段定义配置接口（F-04 / AC-007）。
 *
 * <p>只维护 {@code arc_field_def}，不接触学生档案值；保存后下一次表单请求即生效。</p>
 */
@RestController
@RequestMapping("/admin/fields")
@RequirePermission("config:field:manage")
public class FieldDefAdminController {

    private final FieldDefAdminService fieldDefAdminService;

    public FieldDefAdminController(FieldDefAdminService fieldDefAdminService) {
        this.fieldDefAdminService = fieldDefAdminService;
    }

    @GetMapping
    public Result<List<FieldDefVO>> list(@RequestParam(required = false) String sectionCode) {
        return Result.success(fieldDefAdminService.list(sectionCode));
    }

    @GetMapping("/meta")
    public Result<FieldConfigMetaVO> meta() {
        return Result.success(fieldDefAdminService.meta());
    }

    @PostMapping
    public Result<FieldDefVO> create(@Valid @RequestBody FieldDefSaveDTO dto) {
        return Result.success(fieldDefAdminService.create(dto));
    }

    @PutMapping("/{id}")
    public Result<FieldDefVO> update(@PathVariable Long id,
                                     @Valid @RequestBody FieldDefSaveDTO dto) {
        return Result.success(fieldDefAdminService.update(id, dto));
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id,
                                     @Valid @RequestBody StatusUpdateDTO dto) {
        fieldDefAdminService.updateStatus(id, dto.getStatus());
        return Result.success();
    }

    @PutMapping("/{id}/required")
    public Result<Void> updateRequired(@PathVariable Long id,
                                       @Valid @RequestBody FieldRequiredDTO dto) {
        fieldDefAdminService.updateRequired(id, dto.getRequired());
        return Result.success();
    }
}
