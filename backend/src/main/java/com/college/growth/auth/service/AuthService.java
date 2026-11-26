package com.college.growth.auth.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.college.growth.auth.dto.LoginDTO;
import com.college.growth.auth.entity.SysUser;
import com.college.growth.auth.mapper.SysUserMapper;
import com.college.growth.auth.vo.LoginVO;
import com.college.growth.auth.vo.UserVO;
import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.context.UserContext;
import com.college.growth.common.enums.UserRoleEnum;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.result.ResultCode;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 登录、会话信息与登出。
 *
 * <p>三条规则：</p>
 * <ol>
 *   <li>账号不存在与密码错误返回同一个 {@code EC-40104}，前端不区分，避免枚举账号；</li>
 *   <li>停用账号返回 {@code EC-40105}，锁定返回 {@code EC-40103}（需求文档 S-1-02）；</li>
 *   <li>登出把 {@code jti} 写进吊销名单，令牌在剩余有效期内立即失效。</li>
 * </ol>
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /**
     * 账号不存在时也跑一次哈希，避免「响应快 = 账号不存在」的时间差被用来枚举账号。
     * 值本身无意义，只求耗时与正常校验接近。
     */
    private static final String DUMMY_HASH =
            "pbkdf2$120000$EB/nHxJBdb35jvcLwwI2XQ==$zB8dlDnBFeUuGusb0kOhq8L4v0C/3DOSb4UPtnCGbP0=";

    private final SysUserMapper userMapper;

    private final PasswordHasher passwordHasher;

    private final JwtService jwtService;

    private final TokenBlacklistService blacklistService;

    public AuthService(SysUserMapper userMapper, PasswordHasher passwordHasher,
                       JwtService jwtService, TokenBlacklistService blacklistService) {
        this.userMapper = userMapper;
        this.passwordHasher = passwordHasher;
        this.jwtService = jwtService;
        this.blacklistService = blacklistService;
    }

    /** 校验账号密码并签发令牌。 */
    public LoginVO login(LoginDTO dto) {
        String username = dto.getUsername().trim();
        SysUser user = userMapper.selectOne(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username));

        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            passwordHasher.matches(dto.getPassword(), DUMMY_HASH);
            if (user == null) {
                log.warn("登录失败：账号不存在 username={}", username);
                throw new BusinessException(ResultCode.LOGIN_FAILED);
            }
            log.warn("登录失败：账号已停用 userId={}", user.getId());
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        }

        if (!passwordHasher.matches(dto.getPassword(), user.getPasswordHash())) {
            log.warn("登录失败：密码不正确 userId={}", user.getId());
            throw new BusinessException(ResultCode.LOGIN_FAILED);
        }

        touchLastLogin(user.getId());
        String token = jwtService.issue(user);
        log.info("登录成功 userId={} role={}", user.getId(), user.getRole());
        return new LoginVO(token, "Bearer", jwtService.getTtlSeconds(), toVO(user));
    }

    /** 当前会话信息；token 有效但账号已被删除或停用时立即失效。 */
    public UserVO me() {
        CurrentUser current = UserContext.get();
        if (current.userId() == null || current.userId() <= 0) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        SysUser user = userMapper.selectById(current.userId());
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return toVO(user);
    }

    /** 登出：令牌本身无状态，靠吊销名单让其立即失效。 */
    public void logout(String token) {
        try {
            JwtService.Claims claims = jwtService.parse(token);
            blacklistService.revoke(claims.jti(), jwtService.remainingSeconds(claims));
            log.info("登出 userId={}", claims.userId());
        } catch (JwtService.JwtException ex) {
            // 令牌本来就无效，登出视为成功：客户端只需要清掉本地状态
            log.debug("登出时令牌不可用：{}", ex.getMessage());
        }
    }

    private void touchLastLogin(Long userId) {
        try {
            userMapper.update(null, Wrappers.<SysUser>lambdaUpdate()
                    .set(SysUser::getLastLoginAt, LocalDateTime.now())
                    .eq(SysUser::getId, userId));
        } catch (Exception ex) {
            // 登录成功不应因为记录时间失败而回滚
            log.warn("更新最近登录时间失败 userId={}：{}", userId, ex.getMessage());
        }
    }

    private UserVO toVO(SysUser user) {
        UserRoleEnum role = UserRoleEnum.fromCode(user.getRole());
        return new UserVO(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                role.name(),
                role.getLabel(),
                user.getStudentId(),
                user.getTempGrant() != null && user.getTempGrant() == 1);
    }
}
