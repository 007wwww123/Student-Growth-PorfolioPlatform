-- =====================================================================
-- 脚本 11：字段编辑权限配置 —— 增加默认可编辑角色列
--
-- 背景：管理员模式支持按板块、按字段配置「仅学生 / 仅老师 / 师生均可 / 均不可」。
--       配置结果写入 arc_field_def.editable_roles（六个板块表单与保存校验共用的唯一来源），
--       同时保留初始化口径 default_editable_roles，用于页面标识「已调整」和「恢复默认」。
--
-- 幂等：列已存在时跳过 ALTER；回填只处理 NULL，可重复执行。
-- 注意：中文 Windows 的 mysql 客户端需加 --default-character-set=utf8mb4
-- =====================================================================

SET @col_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'arc_field_def'
      AND COLUMN_NAME = 'default_editable_roles'
);

SET @ddl := IF(
    @col_exists = 0,
    'ALTER TABLE arc_field_def ADD COLUMN default_editable_roles VARCHAR(64) NULL COMMENT ''默认可编辑角色（初始化口径），用于恢复默认'' AFTER editable_roles',
    'SELECT ''default_editable_roles already exists'' AS skipped'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 存量数据以当前 editable_roles 作为默认口径；后续管理员修改只写 editable_roles。
UPDATE arc_field_def
SET default_editable_roles = editable_roles
WHERE default_editable_roles IS NULL;

SELECT section_code, COUNT(*) AS fields, SUM(required = 1) AS required_fields
FROM arc_field_def
WHERE status = 1
GROUP BY section_code
ORDER BY section_code;
