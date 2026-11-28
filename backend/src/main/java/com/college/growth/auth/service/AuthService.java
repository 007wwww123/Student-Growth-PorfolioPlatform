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
import com.college.growth.common.log.service.AuditLogService;
import com.college.growth.common.result.ResultCode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 登录、会话信息、改密与登出（F-01）。
 *
 * <p>四条规则：</p>
 * <ol>
 *   <li>账号不存在与密码错误返回同一个 {@code EC-40104}，前端不区分，避免枚举账号；</li>
 *   <li>停用账号返回 {@code EC-40105}；连续 5 次密码错误锁定 30 分钟，
 *       锁定期间即使密码正确也返回 {@code EC-40103} 并给出解锁时间（BR-A03 / AC-002）；</li>
 *   <li>首次登录（新建账号、管理员重置）必须改密，改密前只能访问改密接口
 *       （BR-A04 / AC-004），由 {@code JwtAuthFilter} 与令牌里的 {@code mcp} 位共同拦截；</li>
 *   <li>改密成功后令牌版本号加一，旧令牌在下一次请求即失效（BR-A06），
 *       同时签发新令牌让当前设备继续可用。</li>
 * </ol>
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /** 连续失败达到该次数即锁定（BR-A03）。 */
    static final int MAX_FAILED_ATTEMPTS = 5;

    /** 锁定时长（分钟）。 */
    static final int LOCK_MINUTES = 30;

    private static final DateTimeFormatter UNLOCK_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

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

    private final AuditLogService auditLogService;

    public AuthService(SysUserMapper userMapper, PasswordHasher passwordHasher,
                       JwtService jwtService, TokenBlacklistService blacklistService,
                       AuditLogService auditLogService) {
        this.userMapper = userMapper;
        this.passwordHasher = passwordHasher;
        this.jwtService = jwtService;
        this.blacklistService = blacklistService;
        this.auditLogService = auditLogService;
    }

    /** 校验账号密码并签发令牌。 */
    public LoginVO login(LoginDTO dto) {
        String username = dto.getUsername().trim();
        SysUser user = userMapper.selectOne(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username));

        if (user == null) {
            passwordHasher.matches(dto.getPassword(), DUMMY_HASH);
            log.warn("登录失败：账号不存在 username={}", username);
            throw new BusinessException(ResultCode.LOGIN_FAILED);
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            passwordHasher.matches(dto.getPassword(), DUMMY_HASH);
            log.warn("登录失败：账号已停用 userId={}", user.getId());
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        }

        // 锁定判定必须在密码校验之前：锁定期间密码正确同样拒绝（BR-A03）
        ensureNotLocked(user);

        if (!passwordHasher.matches(dto.getPassword(), user.getPasswordHash())) {
            log.warn("登录失败：密码不正确 userId={}", user.getId());
            throw registerFailure(user);
        }

        resetFailureAndTouchLogin(user.getId());
        String token = jwtService.issue(user);
        log.info("登录成功 userId={} role={} mustChangePassword={}", user.getId(), user.getRole(),
                mustChange(user));
        return new LoginVO(token, "Bearer", jwtService.getTtlSeconds(), toVO(user), mustChange(user));
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

    /**
     * 修改本人密码（F-01 / 7.2 {@code auth:password:self}）。
     *
     * <p>改密成功后递增令牌版本号：其它设备上的旧令牌立即失效，当前设备拿到新令牌。
     * 返回结构与登录一致，前端直接覆盖本地会话即可。</p>
     */
    public LoginVO changePassword(String oldPassword, String newPassword) {
        CurrentUser current = UserContext.get();
        if (current.userId() == null || current.userId() <= 0) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        SysUser user = userMapper.selectById(current.userId());
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (!passwordHasher.matches(oldPassword, user.getPasswordHash())) {
            log.warn("改密失败：原密码不正确 userId={}", user.getId());
            throw new BusinessException(ResultCode.PASSWORD_INCORRECT);
        }
        String password = PasswordPolicy.validate(newPassword);
        if (passwordHasher.matches(password, user.getPasswordHash())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "新密码不能与原密码相同");
        }

        int nextVersion = versionOf(user) + 1;
        String encoded = passwordHasher.hash(password);
        LocalDateTime now = LocalDateTime.now();
        userMapper.update(null, Wrappers.<SysUser>lambdaUpdate()
                .set(SysUser::getPasswordHash, encoded)
                .set(SysUser::getMustChangePassword, 0)
                .set(SysUser::getPasswordChangedAt, now)
                .set(SysUser::getTokenVersion, nextVersion)
                .set(SysUser::getFailedLoginCount, 0)
                .set(SysUser::getLockedUntil, null)
                .eq(SysUser::getId, user.getId()));

        user.setPasswordHash(encoded);
        user.setMustChangePassword(0);
        user.setTokenVersion(nextVersion);
        user.setPasswordChangedAt(now);
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);

        auditLogService.record(AuditLogService.BIZ_ACCOUNT, AuditLogService.TARGET_USER,
                user.getId(), null, null, null, AuditLogService.ACTION_UPDATE,
                "修改本人密码");
        log.info("改密成功 userId={} tokenVersion={}", user.getId(), nextVersion);

        String token = jwtService.issue(user);
        return new LoginVO(token, "Bearer", jwtService.getTtlSeconds(), toVO(user), false);
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

    // ------------------------------------------------------------------
    // 锁定与计数
    // ------------------------------------------------------------------

    /** 锁定未到期直接拒绝；已到期则清零，允许重新尝试。 */
    private void ensureNotLocked(SysUser user) {
        LocalDateTime lockedUntil = user.getLockedUntil();
        if (lockedUntil == null) {
            return;
        }
        if (lockedUntil.isAfter(LocalDateTime.now())) {
            log.warn("登录失败：账号锁定中 userId={} until={}", user.getId(), lockedUntil);
            throw new BusinessException(ResultCode.ACCOUNT_LOCKED, unlockMessage(lockedUntil));
        }
        // 锁定期已过：计数清零后继续校验密码，避免「到期了还进不去」
        userMapper.update(null, Wrappers.<SysUser>lambdaUpdate()
                .set(SysUser::getFailedLoginCount, 0)
                .set(SysUser::getLockedUntil, null)
                .eq(SysUser::getId, user.getId()));
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
    }

    /**
     * 记一次失败；达到阈值时写锁定时间并抛出 {@code EC-40103}。
     *
     * <p>返回值仅用于让调用方以 `throw registerFailure(user)` 的形式收尾，
     * 未达阈值时返回统一的 {@code EC-40104}，不暴露剩余次数（避免被用来爆破）。</p>
     */
    private BusinessException registerFailure(SysUser user) {
        int failed = nvl(user.getFailedLoginCount()) + 1;
        LocalDateTime lockedUntil = failed >= MAX_FAILED_ATTEMPTS
                ? LocalDateTime.now().plusMinutes(LOCK_MINUTES) : null;
        userMapper.update(null, Wrappers.<SysUser>lambdaUpdate()
                .set(SysUser::getFailedLoginCount, failed)
                .set(SysUser::getLockedUntil, lockedUntil)
                .eq(SysUser::getId, user.getId()));
        if (lockedUntil != null) {
            log.warn("账号锁定 userId={} failed={} until={}", user.getId(), failed, lockedUntil);
            return new BusinessException(ResultCode.ACCOUNT_LOCKED, unlockMessage(lockedUntil));
        }
        return new BusinessException(ResultCode.LOGIN_FAILED);
    }

    /** 登录成功：清零失败计数并记录最近登录时间，一次 UPDATE 完成。 */
    private void resetFailureAndTouchLogin(Long userId) {
        try {
            userMapper.update(null, Wrappers.<SysUser>lambdaUpdate()
                    .set(SysUser::getLastLoginAt, LocalDateTime.now())
                    .set(SysUser::getFailedLoginCount, 0)
                    .set(SysUser::getLockedUntil, null)
                    .eq(SysUser::getId, userId));
        } catch (Exception ex) {
            // 登录成功不应因为记录时间失败而回滚
            log.warn("更新最近登录时间失败 userId={}：{}", userId, ex.getMessage());
        }
    }

    private String unlockMessage(LocalDateTime lockedUntil) {
        return "账号已锁定，请于 " + UNLOCK_FORMAT.format(lockedUntil) + " 后重试";
    }

    private int nvl(Integer value) {
        return value == null ? 0 : value;
    }

    private int versionOf(SysUser user) {
        return nvl(user.getTokenVersion());
    }

    private boolean mustChange(SysUser user) {
        return user.getMustChangePassword() != null && user.getMustChangePassword() == 1;
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
                user.getTempGrant() != null && user.getTempGrant() == 1,
                mustChange(user));
    }
}
