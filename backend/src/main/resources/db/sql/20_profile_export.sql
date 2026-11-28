-- =====================================================================
-- 脚本 20：单人档案 PDF 导出任务（F-12 / S-3）
--
-- 文件落本地私有目录（默认 backend/target/export，可用 EXPORT_DIR 覆盖），
-- 下载链接 24 小时有效；生产环境可替换为 MinIO 预签名，表结构不变。
-- 幂等：可重复执行。
-- =====================================================================

CREATE TABLE IF NOT EXISTS sys_export_task
(
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    task_type     VARCHAR(32)  NOT NULL COMMENT 'PROFILE_PDF',
    student_id    BIGINT       NOT NULL COMMENT '被导出学生',
    academic_year VARCHAR(16)  NOT NULL COMMENT '学年',
    status        VARCHAR(16)  NOT NULL COMMENT 'SUCCESS / FAILED',
    file_name     VARCHAR(255) NULL COMMENT '下载文件名',
    file_path     VARCHAR(512) NULL COMMENT '服务端私有路径',
    expire_at     DATETIME     NOT NULL COMMENT '下载过期时间（创建后 24 小时）',
    operator_id   BIGINT       NOT NULL COMMENT '导出人',
    error_msg     VARCHAR(255) NULL COMMENT '失败原因',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_export_task_student (student_id, created_at),
    KEY idx_export_task_operator (operator_id, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='导出任务（单人 PDF）';

SELECT COUNT(*) AS export_task_table FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name = 'sys_export_task';
