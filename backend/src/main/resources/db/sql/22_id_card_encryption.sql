-- =====================================================================
-- 脚本 22：身份证号加密列（需求 7.3）
--
-- stu_student.id_card 原为明文；新增 id_card_cipher（AES-256-GCM，enc:v1: 前缀）
-- 与 id_card_hash（SHA-256），并给哈希建唯一键用于去重。
-- 存量数据由应用启动时的 IdCardMigration 迁移（加密后清空明文列）。
-- 幂等：列/索引不存在时才创建。
-- =====================================================================

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'stu_student' AND COLUMN_NAME = 'id_card_cipher');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE stu_student ADD COLUMN id_card_cipher VARCHAR(512) NULL COMMENT ''身份证密文 enc:v1:'' AFTER id_card',
    'SELECT ''id_card_cipher already exists'' AS skipped');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'stu_student' AND COLUMN_NAME = 'id_card_hash');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE stu_student ADD COLUMN id_card_hash CHAR(64) NULL COMMENT ''SHA-256（十六进制）'' AFTER id_card_cipher',
    'SELECT ''id_card_hash already exists'' AS skipped');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'stu_student' AND INDEX_NAME = 'uk_stu_id_card_hash');
SET @ddl := IF(@idx_exists = 0,
    'ALTER TABLE stu_student ADD UNIQUE KEY uk_stu_id_card_hash (id_card_hash)',
    'SELECT ''uk_stu_id_card_hash already exists'' AS skipped');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT COUNT(*) AS total_students, SUM(id_card IS NOT NULL) AS plaintext_rows,
       SUM(id_card_cipher IS NOT NULL) AS cipher_rows
FROM stu_student;
