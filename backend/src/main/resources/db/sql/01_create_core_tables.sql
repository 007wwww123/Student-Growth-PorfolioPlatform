-- =====================================================================
-- 脚本 1/3：建核心表
-- 一期核心表结构（板块一落地所需最小集）
--
-- 命名与字段口径见《一期建设总流程与可执行方案》（V1.0 版）：
--   3.x 数据模型、4.1 字段描述规范、5.2 状态机、5.3 字段规则。
-- 约定：
--   1. 六板块共用 arc_profile / arc_profile_item / arc_change_log，用 section_code 区分；
--   2. 字段值一律按 field_code 逐行存储，不随板块新增列（EAV 变体）；
--   3. 脚本幂等，可重复执行；执行顺序按文件名前缀数字 01 → 02 → 03。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 板块实例：一个学生 × 一个板块 × 一个学年 一条记录
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS arc_profile
(
    id             BIGINT       NOT NULL COMMENT '主键，雪花 ID',
    student_id     BIGINT       NOT NULL COMMENT '学生 ID',
    academic_year  VARCHAR(16)  NOT NULL COMMENT '学年，格式 2025-2026-1',
    section_code   VARCHAR(16)  NOT NULL COMMENT '板块编码：BASIC/ACADEMIC/...',
    status         VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT '五状态，见 ProfileStatusEnum',
    completed      TINYINT      NOT NULL DEFAULT 0 COMMENT '是否计入整体完成度（六板块均 APPROVED 时置 1）',
    completeness   DECIMAL(5, 2) NOT NULL DEFAULT 0.00 COMMENT '完成度百分比 0–100，只统计必填且启用字段',
    submit_count   INT          NOT NULL DEFAULT 0 COMMENT '提交次数（BR-S05）',
    withdraw_count SMALLINT     NOT NULL DEFAULT 0 COMMENT '撤回次数，全生命周期上限 1（BR-S06）',
    reviewer_id    BIGINT       NULL COMMENT '最近一次审核人',
    reviewed_at    DATETIME     NULL COMMENT '最近一次审核时间',
    submitted_at   DATETIME     NULL COMMENT '最近一次提交时间',
    version        INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号，每次更新加一',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_profile_student_section_year (student_id, section_code, academic_year),
    KEY idx_profile_status (section_code, status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='档案板块实例（六板块共用）';

-- ---------------------------------------------------------------------
-- 字段定义：表单渲染、校验、权限、必填统计的唯一来源（BR-F01/F02/F03）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS arc_field_def
(
    id            BIGINT       NOT NULL COMMENT '主键',
    section_code  VARCHAR(16)  NOT NULL COMMENT '板块编码，大写',
    field_code    VARCHAR(64)  NOT NULL COMMENT '字段编码，形如 basic.name',
    field_name    VARCHAR(64)  NOT NULL COMMENT '界面显示名，与纸质模板用词一致',
    field_type    VARCHAR(16)  NOT NULL COMMENT 'TEXT/TEXTAREA/NUMBER/DATE/SELECT/MULTI_SELECT/RADIO/CHECKBOX/FILE',
    required      TINYINT      NOT NULL DEFAULT 0 COMMENT '提交时是否必填（1 是）',
    source        VARCHAR(16)  NOT NULL COMMENT '值来源：SIS/COU/STU/STU_CFM/SYS/IMP',
    visible_roles VARCHAR(64)  NOT NULL COMMENT '可见角色，逗号分隔，如 STU,COU；不含管理员（管理员默认无业务数据权限）',
    editable_roles VARCHAR(64) NOT NULL DEFAULT '' COMMENT '可编辑角色，逗号分隔；空表示任何角色均不可直接填写',
    validator     VARCHAR(32)  NOT NULL COMMENT '校验器标识，由代码注册表按标识执行（BR-F02）',
    rule_json     TEXT         NULL COMMENT '校验器参数，如 {"minLen":2,"maxLen":32}',
    options_json  TEXT         NULL COMMENT '枚举选项，[{"value":"M","label":"男"}]',
    -- 注意：SENSITIVE 自 MySQL 9.0 起为保留字，故列名用 sensitive_level，勿改回
    sensitive_level TINYINT    NOT NULL DEFAULT 0 COMMENT '敏感级别：0 普通 / 1 脱敏展示 / 2 仅辅导员明文（需二次查看并审计）',
    sort_no       INT          NOT NULL DEFAULT 0 COMMENT '表单顺序',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 停用；停用字段不渲染、不计必填（BR-F03）',
    remark        VARCHAR(255) NULL COMMENT '口径备注，供实施与验收比对',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_field_def_code (field_code),
    KEY idx_field_def_section (section_code, status, sort_no)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='字段定义（表单/校验/权限的唯一来源）';

-- ---------------------------------------------------------------------
-- 字段值：一行一个字段，TEXT 类落 value_text，JSON 类落 value_json
-- STU_CFM 字段的修改先进 pending_*，辅导员确认后替换（BR-F06）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS arc_profile_item
(
    id             BIGINT      NOT NULL COMMENT '主键',
    profile_id     BIGINT      NOT NULL COMMENT '所属板块实例',
    field_code     VARCHAR(64) NOT NULL COMMENT '字段编码',
    value_text     TEXT        NULL COMMENT '文本类字段值（TEXT/TEXTAREA/NUMBER/DATE/SELECT/RADIO/CHECKBOX 布尔）',
    value_json     TEXT        NULL COMMENT 'JSON 类字段值（MULTI_SELECT/FILE 数组）',
    pending_text   TEXT        NULL COMMENT '待确认文本值（STU_CFM 修改后暂存，确认前对外仍返回旧值）',
    pending_json   TEXT        NULL COMMENT '待确认 JSON 值',
    confirm_status VARCHAR(16) NOT NULL DEFAULT 'NONE' COMMENT 'NONE/PENDING/APPROVED/REJECTED',
    confirmed_by   BIGINT      NULL COMMENT '确认人',
    confirmed_at   DATETIME    NULL COMMENT '确认时间',
    updated_by     BIGINT      NULL COMMENT '最后修改人',
    created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_item_profile_field (profile_id, field_code),
    KEY idx_item_field (field_code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='字段值（含待确认区）';

-- ---------------------------------------------------------------------
-- 变更日志：每次字段修改与状态流转都写一条，与业务同事务（BR-S11）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS arc_change_log
(
    id            BIGINT       NOT NULL COMMENT '主键',
    profile_id    BIGINT       NULL COMMENT '所属板块实例，建档前为空',
    student_id    BIGINT       NOT NULL COMMENT '学生 ID',
    section_code  VARCHAR(16)  NOT NULL COMMENT '板块编码',
    biz_type      VARCHAR(32)  NOT NULL COMMENT 'FIELD_EDIT/STATUS_CHANGE/REVIEW/IMPORT/ARCHIVE',
    field_code    VARCHAR(64)  NULL COMMENT '字段级变更时的字段编码',
    old_value     VARCHAR(500) NULL COMMENT '变更前值（敏感字段只存脱敏值）',
    new_value     VARCHAR(500) NULL COMMENT '变更后值（敏感字段只存脱敏值）',
    from_status   VARCHAR(16)  NULL COMMENT '状态流转前',
    to_status     VARCHAR(16)  NULL COMMENT '状态流转后',
    operator_id   BIGINT       NULL COMMENT '操作人',
    operator_role VARCHAR(16)  NULL COMMENT '操作人角色',
    source        VARCHAR(16)  NOT NULL DEFAULT 'WEB' COMMENT 'WEB/IMPORT/SYSTEM',
    reason        VARCHAR(255) NULL COMMENT '操作原因，审核、查看敏感字段等场景必填',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_change_log_profile (profile_id, created_at),
    KEY idx_change_log_student (student_id, biz_type, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='档案变更日志';

-- ---------------------------------------------------------------------
-- 班级：basic.classId 的引用对象，同时决定 basic.majorId 的推导与审核权
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS org_class
(
    id                 BIGINT      NOT NULL COMMENT '主键',
    class_code         VARCHAR(32) NOT NULL COMMENT '班级编码，全局唯一',
    class_name         VARCHAR(64) NOT NULL COMMENT '班级名称，如 软件2401',
    major_name         VARCHAR(64) NOT NULL COMMENT '专业名称，basic.majorId 由此推导',
    grade_year         INT         NOT NULL COMMENT '年级，四位年份',
    counselor_user_id  BIGINT      NULL COMMENT '现任辅导员，决定审核权',
    status             TINYINT     NOT NULL DEFAULT 1 COMMENT '1 在读 / 0 已毕业或撤销',
    created_at         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_org_class_code (class_code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='班级基础数据';
