-- =====================================================================
-- 脚本 24：变更记录申诉（F-18 / AC-070）
--
-- 复用 sys_approval：APPEAL 类型额外记录 student_id 与 biz_id(change_id)，
-- 便于按学生/班级和变更记录反查。幂等。
-- =====================================================================

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_approval' AND COLUMN_NAME = 'student_id');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE sys_approval ADD COLUMN student_id BIGINT NULL COMMENT ''关联学生（申诉/导出）'' AFTER applicant_id',
    'SELECT ''student_id already exists'' AS skipped');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_approval' AND COLUMN_NAME = 'biz_id');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE sys_approval ADD COLUMN biz_id BIGINT NULL COMMENT ''关联业务 ID（change_id 等）'' AFTER student_id',
    'SELECT ''biz_id already exists'' AS skipped');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_approval' AND INDEX_NAME = 'idx_approval_student');
SET @ddl := IF(@idx_exists = 0,
    'ALTER TABLE sys_approval ADD KEY idx_approval_student (student_id, status)',
    'SELECT ''idx_approval_student already exists'' AS skipped');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT COUNT(*) AS appeal_columns FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_approval' AND COLUMN_NAME IN ('student_id', 'biz_id');
