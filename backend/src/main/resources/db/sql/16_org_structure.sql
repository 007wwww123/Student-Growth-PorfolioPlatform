-- =====================================================================
-- 脚本 16：组织架构（学院 / 专业 / 班级）
--
-- 背景：需求 F-02 要求学院、专业、班级可维护，辅导员可绑定；
--       既有 org_class 只有班级，且专业用 major_name 文本表示。
-- 本脚本新增 org_college / org_major，并给 org_class 补 college_id / major_id，
-- 保留 major_name 列以兼容 basic.majorId 的历史口径。
--
-- 幂等：表用 IF NOT EXISTS；列先查 information_schema 再 ALTER；种子用 UPSERT。
-- =====================================================================

CREATE TABLE IF NOT EXISTS org_college
(
    id           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    college_code VARCHAR(32)  NOT NULL COMMENT '学院编码，全局唯一',
    college_name VARCHAR(64)  NOT NULL COMMENT '学院名称',
    status       TINYINT     NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 停用',
    created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_org_college_code (college_code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='学院';

CREATE TABLE IF NOT EXISTS org_major
(
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    college_id BIGINT      NOT NULL COMMENT '所属学院 org_college.id',
    major_code VARCHAR(32)  NOT NULL COMMENT '专业编码，全局唯一',
    major_name VARCHAR(64)  NOT NULL COMMENT '专业名称',
    status     TINYINT     NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 停用',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_org_major_code (major_code),
    KEY idx_org_major_college (college_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='专业';

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'org_class' AND COLUMN_NAME = 'college_id');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE org_class ADD COLUMN college_id BIGINT NULL COMMENT ''所属学院 org_college.id'' AFTER class_name',
    'SELECT ''org_class.college_id already exists'' AS skipped');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'org_class' AND COLUMN_NAME = 'major_id');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE org_class ADD COLUMN major_id BIGINT NULL COMMENT ''所属专业 org_major.id'' AFTER college_id',
    'SELECT ''org_class.major_id already exists'' AS skipped');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO org_college (id, college_code, college_name, status)
VALUES (3001, 'ZJGCS', '卓越工程师学院', 1)
ON DUPLICATE KEY UPDATE college_name = VALUES(college_name), status = 1;

INSERT INTO org_major (id, college_id, major_code, major_name, status)
VALUES (3101, 3001, 'RJGC', '软件工程', 1),
       (3102, 3001, 'DZXX', '电子信息工程', 1)
ON DUPLICATE KEY UPDATE college_id = VALUES(college_id), major_name = VALUES(major_name), status = 1;

UPDATE org_class c
JOIN org_major m ON m.major_name = c.major_name
SET c.college_id = m.college_id,
    c.major_id   = m.id
WHERE c.college_id IS NULL OR c.major_id IS NULL;

SELECT 'college' AS item, COUNT(*) AS cnt FROM org_college
UNION ALL SELECT 'major', COUNT(*) FROM org_major
UNION ALL SELECT 'class_with_major', COUNT(*) FROM org_class WHERE major_id IS NOT NULL;
