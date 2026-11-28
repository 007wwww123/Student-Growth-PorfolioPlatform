package com.college.growth.grant.controller;

import com.college.growth.common.result.Result;
import com.college.growth.grant.dto.ApprovalDecisionDTO;
import com.college.growth.grant.dto.TempGrantApplyDTO;
import com.college.growth.grant.service.TempGrantService;
import com.college.growth.grant.vo.TempGrantVO;
import com.college.growth.permission.support.RequirePermission;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 临时授权接口（F-17 / AC-053）。
 *
 * <p>管理员申请 → 另一管理员审批 → 1–4 小时只读；审批人不能是申请人；
 * 申请人/审批人可撤销；到期由服务端懒清理并写审计。</p>
 */
@RestController
@RequestMapping("/admin/temp-grants")
public class TempGrantController {

    private final TempGrantService tempGrantService;

    public TempGrantController(TempGrantService tempGrantService) {
        this.tempGrantService = tempGrantService;
    }

    @PostMapping("/applications")
    @RequirePermission("grant:temp:request")
    public Result<TempGrantVO> apply(@Valid @RequestBody TempGrantApplyDTO dto) {
        return Result.success(tempGrantService.apply(dto));
    }

    @GetMapping("/applications")
    @RequirePermission("grant:temp:request")
    public Result<List<TempGrantVO>> list() {
        return Result.success(tempGrantService.list());
    }

    @GetMapping("/active")
    @RequirePermission("grant:temp:request")
    public Result<List<TempGrantVO>> active() {
        return Result.success(tempGrantService.active());
    }

    @PostMapping("/applications/{id}/approve")
    @RequirePermission("grant:temp:approve")
    public Result<TempGrantVO> approve(@PathVariable Long id,
                                       @Valid @RequestBody(required = false) ApprovalDecisionDTO dto) {
        return Result.success(tempGrantService.approve(id, dto == null ? null : dto.getRemark()));
    }

    @PostMapping("/applications/{id}/reject")
    @RequirePermission("grant:temp:approve")
    public Result<TempGrantVO> reject(@PathVariable Long id,
                                      @RequestBody ApprovalDecisionDTO dto) {
        return Result.success(tempGrantService.reject(id, dto == null ? null : dto.getRemark()));
    }

    @PostMapping("/applications/{id}/revoke")
    @RequirePermission("grant:temp:request")
    public Result<TempGrantVO> revoke(@PathVariable Long id) {
        return Result.success(tempGrantService.revoke(id));
    }
}
