-- =====================================================================
-- 脚本 17：学生批量导入任务表（F-09 / S-2）
--
-- 记录每次导入的文件名、行数、成功/失败数与错误明细；
-- 错误回执由任务记录生成，不额外存二进制文件。
--
-- 另外补一条权限授权：需求 AC-006 由 ADMIN 执行导入，而 7.2 矩阵只给 COU；
-- 这里给 ADMIN 补 import:student:execute，COU 仍按本班范围校验。
-- 幂等：可重复执行。
-- =====================================================================

CREATE TABLE IF NOT EXISTS sys_import_task
(
    id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    task_type     VARCHAR(32) NOT NULL COMMENT '任务类型：STUDENT',
    file_name     VARCHAR(255) NULL COMMENT '上传文件名',
    total_rows    INT         NOT NULL DEFAULT 0 COMMENT '数据行数（不含表头）',
    success_rows  INT         NOT NULL DEFAULT 0 COMMENT '成功行数',
    failed_rows   INT         NOT NULL DEFAULT 0 COMMENT '失败行数',
    status        VARCHAR(16) NOT NULL COMMENT 'SUCCESS / PARTIAL / FAILED',
    error_json    TEXT        NULL COMMENT '失败行明细 JSON：[{rowNo,studentNo,name,errors}]',
    operator_id   BIGINT      NULL COMMENT '操作人 sys_user.id',
    created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_import_task_type (task_type, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='导入任务与回执';

INSERT INTO sys_role_permission (role, permission_code, scope)
VALUES ('ADMIN', 'import:student:execute', 'ALL')
ON DUPLICATE KEY UPDATE scope = VALUES(scope);

SELECT COUNT(*) AS import_task_table FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name = 'sys_import_task';
