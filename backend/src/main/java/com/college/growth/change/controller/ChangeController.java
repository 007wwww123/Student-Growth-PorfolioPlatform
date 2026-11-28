package com.college.growth.change.controller;

import com.college.growth.change.service.ChangeAppealService;
import com.college.growth.change.vo.AppealVO;
import com.college.growth.change.vo.ChangeLogVO;
import com.college.growth.common.result.PageResult;
import com.college.growth.common.result.Result;
import com.college.growth.permission.support.RequirePermission;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 变更记录与申诉接口（F-18 / AC-070）。
 *
 * <p>学生看本人、辅导员看本班、管理员看全部；申诉由 COU 处理，管理员兜底。</p>
 */
@RestController
@RequestMapping("/changes")
public class ChangeController {

    private final ChangeAppealService changeAppealService;

    public ChangeController(ChangeAppealService changeAppealService) {
        this.changeAppealService = changeAppealService;
    }

    @GetMapping
    @RequirePermission("archive:change:read")
    public Result<PageResult<ChangeLogVO>> list(
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) String sectionCode,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String fieldCode,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return Result.success(changeAppealService.list(studentId, sectionCode, bizType, fieldCode,
                from, to, pageNum, pageSize));
    }

    @PostMapping("/{changeId}/appeals")
    @RequirePermission("archive:change:read")
    public Result<AppealVO> appeal(@PathVariable Long changeId, @RequestBody Map<String, String> body) {
        return Result.success(changeAppealService.appeal(changeId, body.get("reason")));
    }

    @GetMapping("/appeals")
    @RequirePermission("archive:change:read")
    public Result<PageResult<AppealVO>> appeals(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return Result.success(changeAppealService.appeals(status, pageNum, pageSize));
    }

    @PostMapping("/appeals/{id}/approve")
    @RequirePermission("archive:change:read")
    public Result<AppealVO> approve(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        return Result.success(changeAppealService.approve(id, body == null ? null : body.get("remark")));
    }

    @PostMapping("/appeals/{id}/reject")
    @RequirePermission("archive:change:read")
    public Result<AppealVO> reject(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return Result.success(changeAppealService.reject(id, body.get("remark")));
    }
}
