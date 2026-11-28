-- =====================================================================
-- 脚本 30：阶段 C schema 补齐（30 表口径 + 主表 51 字段）
--
-- 补齐设计文档 7.3 中缺失的表：sys_role / sys_user_role / sys_role_data_scope /
-- stu_class_history / stu_counselor_scope / arc_section / sys_config；
-- 并补 ACADEMIC 三个入口字段（rankMatrix / certificates / events，隐藏只读，
-- 明细仍落子表），使主表字段数达到 51。
-- 幂等。
-- =====================================================================

CREATE TABLE IF NOT EXISTS sys_role
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    role_code   VARCHAR(32)  NOT NULL COMMENT 'ADMIN/COU/STU',
    role_name   VARCHAR(64)  NOT NULL,
    description VARCHAR(255) NULL,
    is_builtin  TINYINT      NOT NULL DEFAULT 1,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (role_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='角色';

CREATE TABLE IF NOT EXISTS sys_user_role
(
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    KEY idx_user_role_role (role_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='用户-角色';

CREATE TABLE IF NOT EXISTS sys_role_data_scope
(
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    role_id     BIGINT      NOT NULL,
    scope_type  VARCHAR(16) NOT NULL COMMENT 'ALL/COLLEGE/MAJOR/CLASS/SELF',
    scope_ref_id BIGINT     NULL,
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_scope (role_id, scope_type, scope_ref_id),
    KEY idx_scope_role (role_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='角色数据范围';

CREATE TABLE IF NOT EXISTS stu_class_history
(
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    student_id    BIGINT      NOT NULL,
    class_id      BIGINT      NOT NULL,
    start_date    DATE        NOT NULL,
    end_date      DATE        NULL,
    change_reason VARCHAR(255) NULL,
    operator_id   BIGINT      NULL,
    created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_hist_student (student_id, start_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='学生班级归属历史';

CREATE TABLE IF NOT EXISTS stu_counselor_scope
(
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    counselor_user_id BIGINT      NOT NULL,
    scope_type        VARCHAR(16) NOT NULL COMMENT 'CLASS/MAJOR/COLLEGE',
    scope_ref_id      BIGINT      NOT NULL,
    valid_from        DATE        NULL,
    valid_to          DATE        NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cscope (counselor_user_id, scope_type, scope_ref_id),
    KEY idx_cscope_user (counselor_user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='辅导员管辖范围';

CREATE TABLE IF NOT EXISTS arc_section
(
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    section_code VARCHAR(16) NOT NULL,
    section_name VARCHAR(64) NOT NULL,
    sort_no      INT         NOT NULL DEFAULT 0,
    status       TINYINT     NOT NULL DEFAULT 1,
    created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_section_code (section_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='板块定义';

CREATE TABLE IF NOT EXISTS sys_config
(
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    config_key   VARCHAR(128) NOT NULL,
    config_value TEXT         NULL,
    value_type   VARCHAR(16)  NOT NULL DEFAULT 'STRING',
    group_code   VARCHAR(32)  NULL,
    description  VARCHAR(255) NULL,
    updated_by   BIGINT       NULL,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_config_key (config_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='系统参数';

INSERT INTO sys_role (role_code, role_name, description)
VALUES ('ADMIN', '管理员', '系统配置与只读审计'),
       ('COU', '辅导员', '本班档案审核与台账'),
       ('STU', '学生', '本人档案填报')
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name);

INSERT INTO arc_section (section_code, section_name, sort_no)
VALUES ('BASIC', '基本信息', 10), ('ACADEMIC', '学业发展', 20),
       ('INTEREST', '兴趣特长', 30), ('LIFE', '生活心理', 40),
       ('CAREER', '职业规划', 50), ('MENTOR', '导师寄语', 60)
ON DUPLICATE KEY UPDATE section_name = VALUES(section_name), sort_no = VALUES(sort_no);

INSERT INTO sys_config (config_key, config_value, value_type, group_code, description)
VALUES ('export.watermark', '仅供内部使用', 'STRING', 'export', '导出水印文字'),
       ('session.timeout.minutes', '480', 'NUMBER', 'auth', '会话超时（分钟）'),
       ('upload.max.bytes', '10485760', 'NUMBER', 'file', '单文件上限（字节）')
ON DUPLICATE KEY UPDATE config_value = VALUES(config_value), description = VALUES(description);

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u JOIN sys_role r ON r.role_code = u.role
ON DUPLICATE KEY UPDATE user_id = VALUES(user_id);

-- ACADEMIC 主表补齐 3 个入口字段：隐藏只读，明细仍落 arc_rank/arc_certificate/arc_event
INSERT INTO arc_field_def
(id, section_code, field_code, field_name, field_type, required, source, visible_roles, editable_roles,
 validator, rule_json, options_json, sensitive_level, sort_no, status, remark)
VALUES
(1206, 'ACADEMIC', 'academic.rankMatrix', '成绩轨迹', 'RANK_MATRIX', 0, 'SYS', '', '', '', NULL, NULL, 0, 60, 1, '入口字段，明细见 arc_rank'),
(1207, 'ACADEMIC', 'academic.certificates', '证书技能', 'RANK_MATRIX', 0, 'SYS', '', '', '', NULL, NULL, 0, 70, 1, '入口字段，明细见 arc_certificate'),
(1208, 'ACADEMIC', 'academic.events', '竞赛科研', 'RANK_MATRIX', 0, 'SYS', '', '', '', NULL, NULL, 0, 80, 1, '入口字段，明细见 arc_event')
ON DUPLICATE KEY UPDATE field_name = VALUES(field_name), status = 1;

SELECT COUNT(*) AS business_tables FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name NOT IN ('flyway_schema_history');
SELECT COUNT(*) AS main_fields FROM arc_field_def WHERE status = 1;
