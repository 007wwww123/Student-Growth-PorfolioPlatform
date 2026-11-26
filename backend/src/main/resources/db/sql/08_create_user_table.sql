-- =====================================================================
-- 脚本 8：建账号表 sys_user
--
-- 为什么需要这张表：
--   此前身份靠请求头 X-User-Id / X-User-Role 注入，任何调用方都能自封管理员，
--   字段级权限与审核权等于没有。登录后由本表校验账号密码并签发 JWT，
--   请求头方式关闭。
--
-- 口径约定：
--   1. id 与 stu_student.id / arc_profile.student_id 同口径（统一认证的用户 ID），
--      学生账号的 id 直接等于其 student_id；
--   2. username 统一为登录名：学生用学号，辅导员用 cou+工号，管理员用 admin；
--   3. password_hash 存 PBKDF2-SHA256 派生值，格式 pbkdf2$迭代次数$盐$哈希，
--      不存明文也不存可逆密文；
--   4. temp_grant 对应「管理员临时只读授权」（BR-S03）：管理员默认无业务数据权限；
--   5. 锁定与停用分开：status=0 表示停用，连续失败锁定在 login_attempts / locked_until
--      预留，本期只做停用与密码校验，锁定策略由需求文档 4.1 后续补充。
-- =====================================================================
CREATE TABLE IF NOT EXISTS sys_user
(
    id            BIGINT       NOT NULL COMMENT '用户 ID，与学生 ID 同口径',
    username      VARCHAR(64)  NOT NULL COMMENT '登录名：学号 / cou+工号 / admin',
    password_hash VARCHAR(255) NOT NULL COMMENT 'PBKDF2-SHA256，格式 pbkdf2$iter$salt$hash',
    display_name  VARCHAR(64)  NOT NULL COMMENT '展示名：学生姓名 / 辅导员姓名 / 管理员',
    role          VARCHAR(16)  NOT NULL COMMENT 'STU 学生 / COU 辅导员 / ADMIN 管理员',
    student_id    BIGINT       NULL COMMENT '学生账号关联的 stu_student.id，其他角色为空',
    temp_grant    TINYINT      NOT NULL DEFAULT 0 COMMENT '1 持临时只读授权（仅管理员有意义）',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 停用',
    last_login_at DATETIME     NULL COMMENT '最近一次登录成功时间',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_username (username),
    KEY idx_sys_user_student (student_id),
    KEY idx_sys_user_role (role, status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='平台账号（登录与身份）';
