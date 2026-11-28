-- =====================================================================
-- 脚本 23：临时授权申请/审批（F-17 / AC-053）
--
-- sys_approval 为通用审批单，后续敏感导出（F-13）、申诉（F-18）、
-- 归档解冻共用；本脚本只启用 TEMP_GRANT。
-- 临时授权最长 4 小时；sys_user.temp_grant_expire_at 用于运行时判定，
-- 旧令牌未过期也会在到期后失效。
-- 幂等：可重复执行。
-- =====================================================================

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user'
                      AND COLUMN_NAME = 'temp_grant_expire_at');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE sys_user ADD COLUMN temp_grant_expire_at DATETIME NULL COMMENT ''临时授权到期时间'' AFTER temp_grant',
    'SELECT ''temp_grant_expire_at already exists'' AS skipped');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS sys_approval
(
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    approval_type   VARCHAR(32)  NOT NULL COMMENT 'TEMP_GRANT / EXPORT_SENSITIVE / APPEAL / UNFREEZE',
    applicant_id    BIGINT       NOT NULL COMMENT '申请人',
    target_json     VARCHAR(1000) NULL COMMENT '申请参数，如 {"hours":2}',
    reason          VARCHAR(500) NOT NULL COMMENT '申请理由',
    status          VARCHAR(16)  NOT NULL COMMENT 'PENDING / APPROVED / REJECTED / EXPIRED / REVOKED',
    approver_id     BIGINT       NULL COMMENT '审批人',
    approver_remark VARCHAR(500) NULL COMMENT '审批意见',
    approved_at     DATETIME     NULL COMMENT '审批时间',
    expire_at       DATETIME     NULL COMMENT '生效到期时间',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_approval_status (status, created_at),
    KEY idx_approval_applicant (applicant_id, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='审批单（临时授权/敏感导出/申诉/解冻）';

-- 第二个管理员：审批人不能是申请人本人，联调需要一个独立审批账号
INSERT INTO sys_user (id, username, password_hash, display_name, role, student_id, temp_grant, status)
SELECT 9005, 'admin1', password_hash, '学院领导', 'ADMIN', NULL, 0, 1
FROM sys_user WHERE username = 'admin0'
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), role = VALUES(role), status = 1;

-- 阶段 A 预留但未授出的两个权限点，本阶段启用
INSERT INTO sys_role_permission (role, permission_code, scope)
VALUES ('ADMIN', 'grant:temp:request', 'ALL'),
       ('ADMIN', 'grant:temp:approve', 'ALL')
ON DUPLICATE KEY UPDATE scope = VALUES(scope);

SELECT COUNT(*) AS approval_table FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name = 'sys_approval';
SELECT role, COUNT(*) AS permissions FROM sys_role_permission WHERE role = 'ADMIN' GROUP BY role;
