-- =====================================================================
-- 脚本 28：归档解冻审批 + MENTOR 更正字段（B-6）
--
-- 解冻复用 sys_approval(UNFREEZE)，不新增表；MENTOR 归档后只追加更正记录，
-- 不覆盖原文（BR-S07），因此新增 corrects_note_id / correction_reason。
-- 幂等。
-- =====================================================================

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'arc_mentor_note'
                      AND COLUMN_NAME = 'corrects_note_id');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE arc_mentor_note ADD COLUMN corrects_note_id BIGINT NULL COMMENT ''更正的原记录 ID'' AFTER student_id',
    'SELECT ''corrects_note_id already exists'' AS skipped');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'arc_mentor_note'
                      AND COLUMN_NAME = 'correction_reason');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE arc_mentor_note ADD COLUMN correction_reason VARCHAR(255) NULL COMMENT ''更正原因'' AFTER corrects_note_id',
    'SELECT ''correction_reason already exists'' AS skipped');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT COUNT(*) AS mentor_correction_columns FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'arc_mentor_note'
  AND COLUMN_NAME IN ('corrects_note_id', 'correction_reason');
