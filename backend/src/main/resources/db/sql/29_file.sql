-- =====================================================================
-- 脚本 29：附件/文件服务（B-6 下）
--
-- 本地私有目录存储（生产可替换 MinIO），数据库只存元数据与随机文件名，
-- 不存原始路径；下载必须经过鉴权接口并写审计。
-- 幂等。
-- =====================================================================

CREATE TABLE IF NOT EXISTS sys_file
(
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    student_id    BIGINT       NOT NULL COMMENT '所属学生',
    biz_type      VARCHAR(32)  NOT NULL COMMENT 'BASIC_PHOTO / CERT_EVIDENCE / OTHER',
    biz_id        BIGINT       NULL COMMENT '关联业务 ID',
    original_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
    stored_name   VARCHAR(128) NOT NULL COMMENT '随机存储名',
    content_type  VARCHAR(128) NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    sha256        CHAR(64)     NULL,
    uploaded_by   BIGINT       NOT NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_file_student (student_id, created_at),
    KEY idx_file_biz (biz_type, biz_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='附件元数据';

SELECT COUNT(*) AS file_table FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name = 'sys_file';
