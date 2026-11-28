-- =====================================================================
-- 脚本 25：班级 Excel 敏感列审批（F-13 / AC-044）
--
-- 复用 sys_approval 的 EXPORT_SENSITIVE；审批通过后 4 小时内可导出一次/多次
-- 本班敏感列。审批人需 export:sensitive:approve（阶段 A 预留，本脚本授给 ADMIN）。
-- 幂等。
-- =====================================================================

INSERT INTO sys_role_permission (role, permission_code, scope)
VALUES ('ADMIN', 'export:sensitive:approve', 'ALL')
ON DUPLICATE KEY UPDATE scope = VALUES(scope);

SELECT role, permission_code, scope FROM sys_role_permission
WHERE permission_code = 'export:sensitive:approve';
