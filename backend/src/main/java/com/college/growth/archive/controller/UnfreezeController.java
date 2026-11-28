package com.college.growth.archive.controller;

import com.college.growth.archive.dto.UnfreezeApplyDTO;
import com.college.growth.archive.service.UnfreezeService;
import com.college.growth.archive.vo.UnfreezeVO;
import com.college.growth.common.result.Result;
import com.college.growth.permission.support.RequirePermission;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 归档解冻接口（B-6）：COU 申请、ADMIN 审批。 */
@RestController
@RequestMapping("/archive/unfreeze")
public class UnfreezeController {

    private final UnfreezeService unfreezeService;

    public UnfreezeController(UnfreezeService unfreezeService) {
        this.unfreezeService = unfreezeService;
    }

    @PostMapping
    @RequirePermission("archive:profile:archive")
    public Result<UnfreezeVO> apply(@Valid @RequestBody UnfreezeApplyDTO dto) {
        return Result.success(unfreezeService.apply(dto.getStudentId(), dto.getAcademicYear(), dto.getReason()));
    }

    @GetMapping
    public Result<List<UnfreezeVO>> list() {
        return Result.success(unfreezeService.list());
    }

    @PostMapping("/{id}/approve")
    @RequirePermission("archive:profile:unlock")
    public Result<UnfreezeVO> approve(@PathVariable Long id,
                                      @RequestBody(required = false) Map<String, String> body) {
        return Result.success(unfreezeService.approve(id, body == null ? null : body.get("remark")));
    }

    @PostMapping("/{id}/reject")
    @RequirePermission("archive:profile:unlock")
    public Result<UnfreezeVO> reject(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return Result.success(unfreezeService.reject(id, body.get("remark")));
    }
}
