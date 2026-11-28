-- =====================================================================
-- 脚本 21：登录日志（F-14 / AC-050–052）
--
-- 与 sys_audit_log 分开：登录失败没有可用身份上下文，单独存账号、结果、
-- 原因、IP 与 UA；不存密码/令牌。
-- 幂等：可重复执行。
-- =====================================================================

CREATE TABLE IF NOT EXISTS sys_login_log
(
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id    BIGINT       NULL COMMENT '用户 ID（账号不存在时为空）',
    username   VARCHAR(64)  NULL COMMENT '尝试登录的账号',
    success    TINYINT      NOT NULL COMMENT '1 成功 / 0 失败',
    reason     VARCHAR(255) NULL COMMENT '失败原因或动作（LOGIN_FAIL/DISABLED/LOGOUT）',
    ip         VARCHAR(64)  NULL COMMENT '客户端 IP',
    user_agent VARCHAR(255) NULL COMMENT 'UA',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_login_log_username (username, created_at),
    KEY idx_login_log_success (success, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='登录成功/失败与登出日志';

SELECT COUNT(*) AS login_log_table FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name = 'sys_login_log';
