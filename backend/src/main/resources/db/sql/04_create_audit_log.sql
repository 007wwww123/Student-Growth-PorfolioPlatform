-- =====================================================================
-- 脚本 4：建系统审计日志表
--
-- 与 arc_change_log 的分工：
--   arc_change_log 记「档案内容发生了什么」（字段、状态、审核意见）；
--   sys_audit_log  记「谁调用了什么敏感能力」（提交、撤回、审核、查看明文、确认待确认值）。
-- S-1-12 要求状态变更同时写这两张表，且与业务同一事务。
--
-- 约定：审计日志不存业务明文，detail 只写动作说明与原因。
-- =====================================================================
CREATE TABLE IF NOT EXISTS sys_audit_log
(
    id            BIGINT      NOT NULL COMMENT '主键，雪花 ID',
    biz_type      VARCHAR(32) NOT NULL COMMENT 'STATUS_CHANGE/REVIEW/SENSITIVE_VIEW/FIELD_CONFIRM',
    target_type   VARCHAR(16) NOT NULL COMMENT 'PROFILE/FIELD',
    target_id     BIGINT      NULL COMMENT '目标对象 ID：板块实例或字段行',
    student_id    BIGINT      NULL COMMENT '关联学生，便于按人追溯',
    section_code  VARCHAR(16) NULL COMMENT '板块编码，如 BASIC',
    field_code    VARCHAR(64) NULL COMMENT '字段编码，字段级操作时填写',
    action        VARCHAR(32) NOT NULL COMMENT 'SUBMIT/WITHDRAW/APPROVE/REJECT/REVEAL/CONFIRM/DISCARD',
    detail        VARCHAR(500) NULL COMMENT '补充说明，禁止写敏感明文',
    operator_id   BIGINT      NULL COMMENT '操作人',
    operator_role VARCHAR(16) NULL COMMENT '操作人角色',
    trace_id      VARCHAR(64) NULL COMMENT '链路追踪号，对应响应头 X-Trace-Id',
    created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
    PRIMARY KEY (id),
    KEY idx_audit_student (student_id, biz_type, created_at),
    KEY idx_audit_operator (operator_id, created_at),
    KEY idx_audit_target (target_type, target_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='系统审计日志';
