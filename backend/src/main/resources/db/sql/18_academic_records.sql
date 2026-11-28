-- =====================================================================
-- 脚本 18：学业成果子表（F-10 / 4.8 / 4.9）
--
-- arc_rank        成绩轨迹：一名学生一学期一行，由教务 Excel 导入
-- arc_certificate 证书技能：学生新增，COU 认定
-- arc_event       竞赛科研：学生新增，COU 认定
--
-- 认定状态：PENDING / PASSED / REJECTED；只有 PASSED 计入成果统计。
-- 幂等：可重复执行。
-- =====================================================================

CREATE TABLE IF NOT EXISTS arc_rank
(
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    student_id    BIGINT       NOT NULL COMMENT '学生 ID',
    academic_year VARCHAR(16)  NOT NULL COMMENT '学期，如 2025-2026-1',
    class_rank    INT          NULL COMMENT '班级排名',
    major_rank    INT          NULL COMMENT '专业排名',
    gpa           DECIMAL(3,2) NULL COMMENT 'GPA 0-5，两位小数',
    source        VARCHAR(16)  NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL / IMPORT',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_arc_rank_student_year (student_id, academic_year),
    KEY idx_arc_rank_year (academic_year)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='成绩轨迹（只读，教务导入）';

CREATE TABLE IF NOT EXISTS arc_certificate
(
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    student_id       BIGINT       NOT NULL COMMENT '学生 ID',
    cert_type        VARCHAR(32)  NOT NULL COMMENT '证书类型：外语/计算机/职业资格/技能/其他',
    cert_name        VARCHAR(128) NOT NULL COMMENT '证书名称',
    score            VARCHAR(32)  NULL COMMENT '成绩或等级',
    obtain_date      DATE         NOT NULL COMMENT '取得日期',
    evidence_file_id BIGINT       NULL COMMENT '证据文件 ID',
    verify_status    VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PASSED/REJECTED',
    verify_user_id   BIGINT       NULL COMMENT '认定人',
    verify_at        DATETIME     NULL COMMENT '认定时间',
    verify_remark    VARCHAR(255) NULL COMMENT '认定意见，REJECTED 时必填',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_arc_cert_student (student_id, verify_status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='证书技能（学生新增，COU 认定）';

CREATE TABLE IF NOT EXISTS arc_event
(
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    student_id       BIGINT       NOT NULL COMMENT '学生 ID',
    event_type       VARCHAR(16)  NOT NULL COMMENT 'COMPETITION/PROJECT/RESEARCH',
    title            VARCHAR(255) NOT NULL COMMENT '竞赛/项目/科研名称',
    role_name        VARCHAR(64)  NULL COMMENT '本人角色',
    level            VARCHAR(16)  NOT NULL COMMENT 'SCHOOL/PROVINCE/NATIONAL/INTERNATIONAL',
    start_date       DATE         NOT NULL COMMENT '开始日期',
    end_date         DATE         NULL COMMENT '结束日期',
    description      TEXT         NULL COMMENT '描述，不超过 500 字',
    evidence_file_id BIGINT       NULL COMMENT '证据文件 ID',
    verify_status    VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PASSED/REJECTED',
    verify_user_id   BIGINT       NULL COMMENT '认定人',
    verify_at        DATETIME     NULL COMMENT '认定时间',
    verify_remark    VARCHAR(255) NULL COMMENT '认定意见',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_arc_event_student (student_id, verify_status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='竞赛科研（学生新增，COU 认定）';

SELECT 'arc_rank' AS table_name, COUNT(*) AS cnt FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name = 'arc_rank'
UNION ALL SELECT 'arc_certificate', COUNT(*) FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name = 'arc_certificate'
UNION ALL SELECT 'arc_event', COUNT(*) FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name = 'arc_event';
