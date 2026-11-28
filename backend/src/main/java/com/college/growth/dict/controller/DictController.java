package com.college.growth.dict.controller;

import com.college.growth.common.result.Result;
import com.college.growth.dict.dto.DictItemSaveDTO;
import com.college.growth.dict.service.DictService;
import com.college.growth.dict.vo.DictVO;
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
 * 数据字典接口（F-15 / AC-008）。
 *
 * <p>读接口所有登录角色可用（表单需要）；管理接口仅 {@code config:dict:manage}。
 * 停用项保留返回，前端按 disabled 处理。</p>
 */
@RestController
public class DictController {

    private final DictService dictService;

    public DictController(DictService dictService) {
        this.dictService = dictService;
    }

    @GetMapping("/dict/{code}")
    @RequirePermission("config:dict:read")
    public Result<DictVO> get(@PathVariable String code) {
        return Result.success(dictService.get(code));
    }

    @GetMapping("/admin/dicts")
    @RequirePermission("config:dict:manage")
    public Result<List<DictVO>> list() {
        return Result.success(dictService.list());
    }

    @PostMapping("/admin/dicts/{code}/items")
    @RequirePermission("config:dict:manage")
    public Result<DictVO> add(@PathVariable String code, @Valid @RequestBody DictItemSaveDTO dto) {
        return Result.success(dictService.addItem(code, dto));
    }

    @PutMapping("/admin/dicts/{code}/items/{itemId}")
    @RequirePermission("config:dict:manage")
    public Result<DictVO> update(@PathVariable String code, @PathVariable Long itemId,
                                 @Valid @RequestBody DictItemSaveDTO dto) {
        return Result.success(dictService.updateItem(code, itemId, dto));
    }

    @PutMapping("/admin/dicts/{code}/items/{itemId}/status")
    @RequirePermission("config:dict:manage")
    public Result<DictVO> status(@PathVariable String code, @PathVariable Long itemId,
                                 @RequestParam Integer status) {
        return Result.success(dictService.setStatus(code, itemId, status));
    }
}
