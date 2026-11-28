package com.college.growth.auth.controller;

import com.college.growth.audit.service.LoginLogService;
import com.college.growth.auth.dto.ChangePasswordDTO;
import com.college.growth.auth.dto.LoginDTO;
import com.college.growth.auth.service.AuthService;
import com.college.growth.auth.vo.LoginVO;
import com.college.growth.auth.vo.UserVO;
import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.context.UserContext;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.result.Result;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录与会话接口（S-1-02）。
 *
 * <p>{@code /auth/login} 是唯一匿名可访问的业务接口，其余接口都要求
 * {@code Authorization: Bearer <token>}，由 {@code JwtAuthFilter} 统一拦截。</p>
 *
 * <p>登录成功/失败与登出在这里写 {@code sys_login_log}（F-14）：失败可能是账号不存在，
 * 不能依赖 UserContext；日志失败也不阻断登录。</p>
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    private final LoginLogService loginLogService;

    public AuthController(AuthService authService, LoginLogService loginLogService) {
        this.authService = authService;
        this.loginLogService = loginLogService;
    }

    /** 账号密码登录，成功后返回令牌与用户信息。 */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        try {
            LoginVO vo = authService.login(dto);
            loginLogService.success(vo.getUser().getUserId(), dto.getUsername());
            return Result.success(vo);
        } catch (BusinessException ex) {
            loginLogService.failure(null, dto.getUsername(),
                    ex.getResultCode().getCode() + ":" + ex.getMessage());
            throw ex;
        }
    }

    /** 当前登录用户，供刷新页面后恢复会话。 */
    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.success(authService.me());
    }

    /**
     * 修改本人密码（F-01 / 7.2 {@code auth:password:self}）。
     *
     * <p>返回结构与登录一致：后端递增令牌版本后旧令牌失效，必须同时把新令牌交给前端，
     * 否则用户改完密码就被踢回登录页。</p>
     */
    @PostMapping("/password")
    public Result<LoginVO> changePassword(@Valid @RequestBody ChangePasswordDTO dto) {
        return Result.success(authService.changePassword(dto.getOldPassword(), dto.getNewPassword()));
    }

    /** 登出：令牌进入吊销名单，客户端同时清除本地状态。 */
    @PostMapping("/logout")
    public Result<Void> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        CurrentUser user = UserContext.get();
        authService.logout(bearerOf(authorization));
        loginLogService.logout(user.userId(), null);
        return Result.success();
    }

    private String bearerOf(String authorization) {
        if (authorization == null) {
            return null;
        }
        String value = authorization.trim();
        return value.regionMatches(true, 0, "Bearer ", 0, 7) ? value.substring(7).trim() : value;
    }
}
