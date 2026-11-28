-- =====================================================================
-- 脚本 27：站内通知（S-1-05/07，阶段 B-5）
--
-- 事件：提交待审（通知辅导员）、审核通过/退回（通知学生）、归档（通知学生）、
-- 申诉处理结果（通知学生）。只存站内，不发短信。
-- 幂等。
-- =====================================================================

CREATE TABLE IF NOT EXISTS sys_notification
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL COMMENT '接收人',
    type       VARCHAR(32)  NOT NULL COMMENT 'REVIEW_SUBMITTED/REVIEW_APPROVED/REVIEW_REJECTED/ARCHIVED/APPEAL_RESULT',
    title      VARCHAR(128) NOT NULL,
    content    VARCHAR(500) NULL,
    biz_type   VARCHAR(32)  NULL COMMENT 'PROFILE/APPEAL/ARCHIVE',
    biz_id     BIGINT       NULL,
    read_at    DATETIME     NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_notification_user (user_id, read_at, created_at),
    KEY idx_notification_biz (biz_type, biz_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='站内通知';

SELECT COUNT(*) AS notification_table FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name = 'sys_notification';
