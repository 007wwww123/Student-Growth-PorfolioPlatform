package com.college.growth.export.classic.controller;

import com.college.growth.common.result.Result;
import com.college.growth.export.classic.dto.ClassExportDTO;
import com.college.growth.export.classic.dto.SensitiveExportApplyDTO;
import com.college.growth.export.classic.service.ClassExportService;
import com.college.growth.export.classic.vo.SensitiveExportApprovalVO;
import com.college.growth.export.vo.ExportTaskVO;
import com.college.growth.permission.support.RequirePermission;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 班级 Excel 与敏感列审批接口（F-13 / AC-044）。
 *
 * <p>COU 导出本班；含手机号/身份证号时必须先申请并获批。审批人需
 * {@code export:sensitive:approve}，不能审批本人申请。</p>
 */
@RestController
@RequestMapping("/export/class")
public class ClassExportController {

    private final ClassExportService classExportService;

    public ClassExportController(ClassExportService classExportService) {
        this.classExportService = classExportService;
    }

    @PostMapping
    @RequirePermission("export:class:excel")
    public Result<ExportTaskVO> create(@Valid @RequestBody ClassExportDTO dto) {
        return Result.success(classExportService.create(dto));
    }

    @PostMapping("/approvals")
    @RequirePermission("export:class:excel")
    public Result<SensitiveExportApprovalVO> apply(@Valid @RequestBody SensitiveExportApplyDTO dto) {
        return Result.success(classExportService.apply(dto));
    }

    @GetMapping("/approvals")
    public Result<List<SensitiveExportApprovalVO>> approvals(@RequestParam(required = false) String status) {
        return Result.success(classExportService.approvals(status));
    }

    @PostMapping("/approvals/{id}/approve")
    @RequirePermission("export:sensitive:approve")
    public Result<SensitiveExportApprovalVO> approve(@PathVariable Long id,
                                                     @RequestBody(required = false) Map<String, String> body) {
        return Result.success(classExportService.approve(id, body == null ? null : body.get("remark")));
    }

    @PostMapping("/approvals/{id}/reject")
    @RequirePermission("export:sensitive:approve")
    public Result<SensitiveExportApprovalVO> reject(@PathVariable Long id,
                                                    @RequestBody Map<String, String> body) {
        return Result.success(classExportService.reject(id, body.get("remark")));
    }
}
