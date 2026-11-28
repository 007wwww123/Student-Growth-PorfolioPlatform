package com.college.growth.account.controller;

import com.college.growth.account.dto.BatchOpenDTO;
import com.college.growth.account.dto.ResetPasswordDTO;
import com.college.growth.account.dto.RoleAssignDTO;
import com.college.growth.account.dto.UserCreateDTO;
import com.college.growth.account.service.AccountService;
import com.college.growth.account.vo.AccountUserVO;
import com.college.growth.account.vo.BatchOpenResultVO;
import com.college.growth.account.vo.CreateAccountResultVO;
import com.college.growth.account.vo.ResetPasswordResultVO;
import com.college.growth.common.dto.StatusUpdateDTO;
import com.college.growth.common.result.PageResult;
import com.college.growth.common.result.Result;
import com.college.growth.permission.support.RequirePermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 账号管理接口（F-03 / AC-006）。
 *
 * <p>所有接口都要求管理员权限点；学生账号按学号与 ID 绑定，不能通过本接口伪造。</p>
 */
@RestController
@RequestMapping("/account")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/users")
    @RequirePermission("account:user:manage")
    public Result<PageResult<AccountUserVO>> users(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return Result.success(accountService.list(role, status, keyword, pageNum, pageSize));
    }

    @PostMapping("/users")
    @RequirePermission("account:user:manage")
    public Result<CreateAccountResultVO> create(@Valid @RequestBody UserCreateDTO dto) {
        return Result.success(accountService.create(dto));
    }

    @PutMapping("/users/{id}/status")
    @RequirePermission("account:user:manage")
    public Result<Void> updateStatus(@PathVariable Long id,
                                     @Valid @RequestBody StatusUpdateDTO dto) {
        accountService.updateStatus(id, dto.getStatus());
        return Result.success();
    }

    @PostMapping("/users/{id}/reset-password")
    @RequirePermission("account:user:manage")
    public Result<ResetPasswordResultVO> resetPassword(@PathVariable Long id,
                                                       @RequestBody(required = false) ResetPasswordDTO dto) {
        return Result.success(accountService.resetPassword(id, dto == null ? null : dto.getPassword()));
    }

    @PutMapping("/users/{id}/role")
    @RequirePermission("account:role:assign")
    public Result<Void> assignRole(@PathVariable Long id, @Valid @RequestBody RoleAssignDTO dto) {
        accountService.assignRole(id, dto.getRole());
        return Result.success();
    }

    @PostMapping("/batch-open")
    @RequirePermission("account:user:manage")
    public Result<BatchOpenResultVO> batchOpen(@Valid @RequestBody BatchOpenDTO dto) {
        return Result.success(accountService.batchOpen(dto));
    }
}
