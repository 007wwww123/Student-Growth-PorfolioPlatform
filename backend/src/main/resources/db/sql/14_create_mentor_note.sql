-- =====================================================================
-- 脚本 14：导师寄语谈话记录子表（板块六）
--
-- 依据：需求 4.7——MENTOR 板块没有主表字段值，全部内容在子表里，一行一次谈话。
-- 原因是谈话记录是时间序列，用固定字段无法表达「N 次谈话」；
-- 且记录只可追加、不可编辑、不可删除（BR-S07），发现错误时追加一条更正记录。
--
-- 与 arc_profile 的关系：arc_profile 仍保留 MENTOR 实例，承载状态机与完成度；
-- 谈话内容不写 arc_profile_item（那里一行一个字段，表达不了多次谈话）。
--
-- 编号说明：13 号脚本由生活心理分支提供（字段口径对齐），与本脚本互不依赖，
-- 因此单独占用 14，避免两个分支撞号。
-- 幂等：可重复执行。
-- =====================================================================

CREATE TABLE IF NOT EXISTS arc_mentor_note
(
    id              BIGINT      NOT NULL COMMENT '主键，雪花 ID',
    profile_id      BIGINT      NOT NULL COMMENT '所属 MENTOR 板块实例（arc_profile.id）',
    student_id      BIGINT      NOT NULL COMMENT '学生 ID，冗余一列便于按学生查时间线',
    academic_year   VARCHAR(16) NOT NULL COMMENT '学年，与板块实例一致',
    talk_date       DATE        NOT NULL COMMENT '谈话日期，不晚于今天且不早于入学日期',
    topic           VARCHAR(32) NOT NULL COMMENT '谈话主题，命中字典 talk_topic',
    advice          TEXT        NOT NULL COMMENT '建议与寄语，20–1000 字，学生可见',
    star_level      TINYINT     NOT NULL DEFAULT 0 COMMENT '重要标记：0 无 / 1 ★ / 2 ★★',
    feedback        TEXT        NULL COMMENT '学生反馈与承诺，不超过 500 字；每条仅可提交一次',
    feedback_at     DATETIME    NULL COMMENT '学生反馈提交时间，非空即视为已锁定',
    internal_remark TEXT        NULL COMMENT '辅导员内部备注，学生端不返回',
    counselor_id    BIGINT      NOT NULL COMMENT '撰写辅导员（sys_user.id）',
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '追加时间',
    updated_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（仅反馈会触发）',
    PRIMARY KEY (id),
    KEY idx_mentor_note_profile (profile_id, talk_date),
    KEY idx_mentor_note_student (student_id, academic_year, talk_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='导师谈话记录（只追加，不编辑不删除）';

-- 自检：空表即为正常（首次部署没有谈话记录）
SELECT COUNT(*) AS mentor_notes FROM arc_mentor_note;
