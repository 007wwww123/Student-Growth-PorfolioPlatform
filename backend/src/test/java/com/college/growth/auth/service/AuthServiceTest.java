package com.college.growth.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.college.growth.auth.dto.LoginDTO;
import com.college.growth.auth.entity.SysUser;
import com.college.growth.auth.mapper.SysUserMapper;
import com.college.growth.auth.vo.LoginVO;
import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.context.UserContext;
import com.college.growth.common.enums.UserRoleEnum;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.result.ResultCode;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.time.Instant;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 登录流程：账号枚举、停用、密码错误、成功签发、会话与登出。 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private SysUserMapper userMapper;

    @Mock
    private PasswordHasher passwordHasher;

    @Mock
    private JwtService jwtService;

    @Mock
    private TokenBlacklistService blacklistService;

    @InjectMocks
    private AuthService authService;

    static {
        // LambdaUpdateWrapper 在纯单测里需要表元数据，否则解析列名会抛 "can not find lambda cache"
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysUser.class);
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    private LoginDTO loginDTO() {
        LoginDTO dto = new LoginDTO();
        dto.setUsername("2024010101");
        dto.setPassword("Passw0rd!");
        return dto;
    }

    private SysUser student() {
        SysUser user = new SysUser();
        user.setId(1001L);
        user.setUsername("2024010101");
        user.setDisplayName("李明");
        user.setRole("STU");
        user.setStudentId(1001L);
        user.setTempGrant(0);
        user.setStatus(1);
        user.setPasswordHash("pbkdf2$120000$x$y");
        return user;
    }

    @Test
    @DisplayName("账号不存在时返回统一的登录失败码，不暴露账号是否存在")
    void unknownUserFails() {
        when(userMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> authService.login(loginDTO()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getResultCode())
                        .isEqualTo(ResultCode.LOGIN_FAILED));
        // 仍然跑一次哈希，避免用响应时间判断账号是否存在
        verify(passwordHasher).matches(eq("Passw0rd!"), anyString());
    }

    @Test
    @DisplayName("停用账号返回 EC-40105")
    void disabledUserFails() {
        SysUser user = student();
        user.setStatus(0);
        when(userMapper.selectOne(any())).thenReturn(user);

        assertThatThrownBy(() -> authService.login(loginDTO()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getResultCode())
                        .isEqualTo(ResultCode.ACCOUNT_DISABLED));
        verify(jwtService, never()).issue(any());
    }

    @Test
    @DisplayName("密码错误返回 EC-40104")
    void wrongPasswordFails() {
        when(userMapper.selectOne(any())).thenReturn(student());
        when(passwordHasher.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(loginDTO()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getResultCode())
                        .isEqualTo(ResultCode.LOGIN_FAILED));
        verify(jwtService, never()).issue(any());
    }

    @Test
    @DisplayName("登录成功返回令牌与用户信息，并记录登录时间")
    void loginSuccess() {
        when(userMapper.selectOne(any())).thenReturn(student());
        when(passwordHasher.matches(anyString(), anyString())).thenReturn(true);
        when(jwtService.issue(any())).thenReturn("token-value");
        when(jwtService.getTtlSeconds()).thenReturn(28800L);

        LoginVO vo = authService.login(loginDTO());

        assertThat(vo.getToken()).isEqualTo("token-value");
        assertThat(vo.getTokenType()).isEqualTo("Bearer");
        assertThat(vo.getExpiresIn()).isEqualTo(28800L);
        assertThat(vo.getUser().getUserId()).isEqualTo(1001L);
        assertThat(vo.getUser().getRole()).isEqualTo("STU");
        assertThat(vo.getUser().getRoleLabel()).isEqualTo("学生");
        assertThat(vo.getUser().getStudentId()).isEqualTo(1001L);
        verify(userMapper).update(any(), any());
    }

    @Test
    @DisplayName("无上下文时 /me 返回 401")
    void meWithoutContext() {
        assertThatThrownBy(() -> authService.me())
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getResultCode())
                        .isEqualTo(ResultCode.UNAUTHORIZED));
    }

    @Test
    @DisplayName("令牌有效但账号已停用时 /me 失效")
    void meWithDisabledUser() {
        UserContext.set(new CurrentUser(1001L, UserRoleEnum.STU, false));
        SysUser user = student();
        user.setStatus(0);
        when(userMapper.selectById(1001L)).thenReturn(user);

        assertThatThrownBy(() -> authService.me())
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getResultCode())
                        .isEqualTo(ResultCode.UNAUTHORIZED));
    }

    @Test
    @DisplayName("登出把 jti 写入吊销名单")
    void logoutRevokesToken() {
        JwtService.Claims claims = new JwtService.Claims(1001L, UserRoleEnum.STU, false, "李明",
                "jti-1", Instant.now(), Instant.now().plusSeconds(3600));
        when(jwtService.parse("token-value")).thenReturn(claims);
        when(jwtService.remainingSeconds(claims)).thenReturn(3600L);

        authService.logout("token-value");

        verify(blacklistService).revoke("jti-1", 3600L);
    }

    @Test
    @DisplayName("登出时令牌已失效不报错")
    void logoutWithInvalidTokenIsSilent() {
        when(jwtService.parse("bad")).thenThrow(new JwtService.JwtException("签名不合法", false));

        authService.logout("bad");

        verify(blacklistService, never()).revoke(anyString(), anyLong());
    }
}
