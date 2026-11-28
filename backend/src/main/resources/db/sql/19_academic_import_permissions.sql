-- =====================================================================
-- 脚本 19：学业成果导入权限（F-10）
--
-- 7.2 矩阵中 import:rank:execute 只给 COU；AC-033/034 由辅导员执行，
-- 管理员在演示/兜底场景也需要导入，这里补 ADMIN 授权（COU 仍按本班校验）。
-- 幂等：可重复执行。
-- =====================================================================

INSERT INTO sys_role_permission (role, permission_code, scope)
VALUES ('ADMIN', 'import:rank:execute', 'ALL'),
       ('ADMIN', 'import:cert:verify', 'ALL')
ON DUPLICATE KEY UPDATE scope = VALUES(scope);

SELECT role, permission_code, scope FROM sys_role_permission
WHERE permission_code IN ('import:rank:execute', 'import:cert:verify')
ORDER BY role, permission_code;
