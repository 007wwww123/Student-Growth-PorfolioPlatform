package com.college.growth.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 平台账号（物理表 {@code sys_user}）。
 *
 * <p>登录时用它校验密码、确定角色与数据范围，签发 JWT 后请求不再信任任何身份请求头。
 * {@code id} 与学生 ID 同口径：学生账号的 {@code id} 直接等于 {@code student_id}，
 * 因此 {@code UserContext.userId()} 与既有业务代码（审核权、本人校验）无需换算。</p>
 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.INPUT)
    private Long id;

    /** 登录名：学生用学号，辅导员用 cou+工号，管理员用 admin。 */
    private String username;

    /** PBKDF2 派生值，格式 {@code pbkdf2$迭代次数$盐$哈希}，不存明文。 */
    private String passwordHash;

    /** 展示名。 */
    private String displayName;

    /** STU / COU / ADMIN。 */
    private String role;

    /** 学生账号关联的 stu_student.id，其他角色为空。 */
    private Long studentId;

    /** 1 表示管理员持临时只读授权（BR-S03）。 */
    private Integer tempGrant;

    /** 临时授权到期时间；为空表示未设置（历史演示账号）。 */
    private LocalDateTime tempGrantExpireAt;

    /** 1 启用 / 0 停用。 */
    private Integer status;

    /** 1 表示首次登录必须改密（BR-A04）：新建账号、管理员重置密码后置 1，改密成功置 0。 */
    private Integer mustChangePassword;

    /** 连续密码错误次数（BR-A03）：登录成功清零，达到 5 次写 {@code lockedUntil}。 */
    private Integer failedLoginCount;

    /** 锁定到期时间；为空或已过表示未锁定，锁定期间密码正确同样拒绝。 */
    private LocalDateTime lockedUntil;

    /**
     * 令牌版本号（BR-A06）。
     *
     * <p>签发令牌时写入 JWT 的 {@code ver}，每次请求与库中值比对；改密、重置密码、
     * 调整角色时递增，旧令牌在下一次请求即失效，不依赖自然过期。</p>
     */
    private Integer tokenVersion;

    /** 最近一次改密时间，仅供审计与排查，不参与鉴权判定。 */
    private LocalDateTime passwordChangedAt;

    private LocalDateTime lastLoginAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
