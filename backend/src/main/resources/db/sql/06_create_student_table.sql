-- =====================================================================
-- 脚本 6：建学生主数据表 stu_student
--
-- 为什么需要这张表：
--   板块一有 7 个 source=SIS 的字段（姓名、学号、性别、民族、身份证、籍贯、班级），
--   editable_roles 为空，任何角色都写不进去。没有学生主数据时，这些值只能靠人工 SQL 直插，
--   学生自己永远无法让板块通过必填校验 —— 业务闭环是断的。
--
-- 口径约定：
--   1. id 与 arc_profile.student_id 同口径（统一认证后的用户 ID），不是自增学籍号；
--   2. student_no 是学号，学籍唯一标识，与 basic.studentNo 对齐，建唯一键；
--   3. class_code 引用 org_class.class_code，是审核权（本班辅导员）的判定依据；
--   4. 本表是 SIS（学工/教务）的落地副本，权威源在外部系统；同步用
--      StudentSyncService 物化到 arc_profile_item，档案侧读的仍是 arc_profile_item，
--      因此板块渲染、校验、变更日志、待确认区全链路都不用改。
--   5. id_card 暂存明文并与 arc_profile_item 口径一致；接入加密后改密文列，
--      届时 reveal 解密 + 审计，不影响本表结构与同步逻辑。
-- =====================================================================
CREATE TABLE IF NOT EXISTS stu_student
(
    id              BIGINT      NOT NULL COMMENT '主键，学生 ID，与 arc_profile.student_id 同口径',
    student_no      VARCHAR(20) NOT NULL COMMENT '学号，学籍唯一标识',
    name            VARCHAR(32) NOT NULL COMMENT '姓名',
    gender          CHAR(1)     NULL COMMENT '性别 M/F/U，与 basic.gender 选项值一致',
    nation          VARCHAR(20) NULL COMMENT '民族，存中文名，与 basic.nation 选项一致',
    id_card         VARCHAR(32) NULL COMMENT '身份证号，18 位；接入加密后改密文列',
    native_place    VARCHAR(64) NULL COMMENT '籍贯，省市区用空格分隔',
    class_code      VARCHAR(32) NULL COMMENT '所属班级，引用 org_class.class_code',
    enrollment_year INT         NULL COMMENT '入学年份，四位',
    status          TINYINT     NOT NULL DEFAULT 1 COMMENT '1 在读 / 2 休学 / 3 退学 / 4 毕业',
    sync_source     VARCHAR(16) NOT NULL DEFAULT 'SIS' COMMENT '数据来源：SIS / MANUAL',
    synced_at       DATETIME    NULL COMMENT '最近一次从来源系统同步的时间',
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_stu_student_no (student_no),
    KEY idx_stu_student_class (class_code, status),
    KEY idx_stu_student_name (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='学生主数据（SIS 落地副本）';
