-- =====================================================================
-- 脚本 31：登录与会话补强（F-01 / BR-A02–BR-A07 / AC-002 / AC-004）
--
-- 为什么需要这五个列：此前 sys_user 只区分「启用 / 停用」，
-- 密码策略、首次强制改密、连续失败锁定、权限变更即时生效都没有落点；
-- 需求 7.2 的 auth:password:self 也只能靠改库完成。
--
-- 口径：
--   1. must_change_password：首次登录（新建账号、管理员重置）后置 1，
--      改密成功后置 0；为 1 时除改密接口外一律 EC-40305（BR-A04）。
--   2. failed_login_count + locked_until：连续失败计数；达到 5 次锁定 30 分钟，
--      锁定期间即使密码正确也拒绝（BR-A03 / AC-002），登录成功即清零。
--   3. token_version：写进 JWT，每次请求与库中值比对；改密、重置密码、
--      调整角色时递增，旧令牌在下一次请求即失效（BR-A06 / BR-A07），
--      不依赖令牌自然过期。
--   4. password_changed_at：只用于审计与排查，不参与鉴权判定。
--
-- 幂等：先查 information_schema 再决定是否 ADD COLUMN，可重复执行。
-- =====================================================================

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user'
                      AND COLUMN_NAME = 'must_change_password');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE sys_user ADD COLUMN must_change_password TINYINT NOT NULL DEFAULT 0 COMMENT ''1 首次登录必须改密（BR-A04）'' AFTER status',
    'SELECT ''must_change_password already exists'' AS skipped');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user'
                      AND COLUMN_NAME = 'failed_login_count');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE sys_user ADD COLUMN failed_login_count INT NOT NULL DEFAULT 0 COMMENT ''连续密码错误次数（BR-A03）'' AFTER must_change_password',
    'SELECT ''failed_login_count already exists'' AS skipped');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user'
                      AND COLUMN_NAME = 'locked_until');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE sys_user ADD COLUMN locked_until DATETIME NULL COMMENT ''锁定到期时间，为空或已过表示未锁定'' AFTER failed_login_count',
    'SELECT ''locked_until already exists'' AS skipped');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user'
                      AND COLUMN_NAME = 'token_version');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE sys_user ADD COLUMN token_version INT NOT NULL DEFAULT 0 COMMENT ''令牌版本号，递增即让旧令牌失效（BR-A06）'' AFTER locked_until',
    'SELECT ''token_version already exists'' AS skipped');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user'
                      AND COLUMN_NAME = 'password_changed_at');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE sys_user ADD COLUMN password_changed_at DATETIME NULL COMMENT ''最近一次改密时间'' AFTER token_version',
    'SELECT ''password_changed_at already exists'' AS skipped');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 演示账号保持可直接登录：不强制改密，也不带锁定。
UPDATE sys_user
SET must_change_password = 0,
    failed_login_count  = 0
WHERE must_change_password IS NULL OR failed_login_count IS NULL;
