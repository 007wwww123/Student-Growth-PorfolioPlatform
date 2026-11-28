-- 阶段 C：Flyway 基线，等价于 db/sql/01→29 全量脚本（脚本本身幂等）。
-- 由 scripts/build-flyway-baseline.ps1 生成，请勿手改；新增脚本后重新生成。

-- >>> 01_create_core_tables.sql
-- =====================================================================
-- 脚本 1/3：建核心表
-- 一期核心表结构（板块一落地所需最小集）
--
-- 命名与字段口径见《一期建设总流程与可执行方案》（V1.0 版）：
--   3.x 数据模型、4.1 字段描述规范、5.2 状态机、5.3 字段规则。
-- 约定：
--   1. 六板块共用 arc_profile / arc_profile_item / arc_change_log，用 section_code 区分；
--   2. 字段值一律按 field_code 逐行存储，不随板块新增列（EAV 变体）；
--   3. 脚本幂等，可重复执行；执行顺序按文件名前缀数字 01 → 07：
--      01 建表 → 02 班级 → 03 字段定义 → 04 审计表 → 05 升级补列 → 06 学生主数据表 → 07 学生种子。
--      存量库升级时先跑 05，再重跑 03（把 source_field 补上）。
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
    source_field  VARCHAR(64)  NULL COMMENT 'SIS 同步来源：stu_student 的列名，为空表示不同步',
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


-- >>> 02_insert_class_seed.sql
-- 脚本 2/3：写入班级种子数据（开发与联调用）
-- 说明：basic.classId 存班级编码，basic.majorId 由本表 major_name 推导。
INSERT INTO org_class (id, class_code, class_name, major_name, grade_year, counselor_user_id, status)
VALUES (2001, 'RJ2401', '软件2401', '软件工程', 2024, 9001, 1),
       (2002, 'RJ2402', '软件2402', '软件工程', 2024, 9001, 1),
       (2003, 'DZ2401', '电子2401', '电子信息工程', 2024, 9002, 1)
ON DUPLICATE KEY UPDATE class_name   = VALUES(class_name),
                        major_name   = VALUES(major_name),
                        grade_year   = VALUES(grade_year),
                        counselor_user_id = VALUES(counselor_user_id),
                        status       = VALUES(status),
                        updated_at   = CURRENT_TIMESTAMP;


-- >>> 03_insert_basic_field_defs.sql
-- =====================================================================
-- 脚本 3/3：写入板块一（BASIC 基本信息）字段定义种子
--
-- 口径来源：《一期建设总流程与可执行方案》（V1.0 版）4.2 字段清单 + 4.1.2 来源与可编辑性
-- 自检：SELECT source, COUNT(*) FROM arc_field_def WHERE section_code='BASIC' GROUP BY source;
--       期望 SIS 7 / COU 3 / SYS 1 / STU_CFM 5，合计 16；required=1 共 8 个。
-- 说明：
--   1. validator 是代码注册表中的标识（BR-F02），规则参数放 rule_json，不在代码里按字段硬编码；
--   2. visible_roles 不含 ADMIN：管理员默认无业务数据权限，仅临时授权时可读；
--   3. options_json 支持 ["A","B"] 与 [{"value":"A","label":"甲"}] 两种写法。
-- =====================================================================
INSERT INTO arc_field_def (id, section_code, field_code, field_name, field_type, required, source,
                           visible_roles, editable_roles, validator, rule_json, options_json,
                           sensitive_level, sort_no, status, remark)
VALUES (1101, 'BASIC', 'basic.name', '姓名', 'TEXT', 1, 'SIS', 'STU,COU', '', 'PERSON_NAME',
        '{"minLen":2,"maxLen":32}', NULL, 0, 10, 1, '汉字、字母、间隔号、空格；含数字即拒绝'),
       (1102, 'BASIC', 'basic.studentNo', '学号', 'TEXT', 1, 'SIS', 'STU,COU', '', 'STUDENT_NO',
        '{"minLen":8,"maxLen":12}', NULL, 0, 20, 1, '学籍数据导入；创建后任何角色不可改'),
       (1103, 'BASIC', 'basic.gender', '性别', 'RADIO', 1, 'SIS', 'STU,COU', '', 'ENUM',
        NULL, '[{"value":"M","label":"男"},{"value":"F","label":"女"},{"value":"U","label":"未知"}]', 0, 30, 1,
        '与身份证第 17 位不一致仅告警，不拦截'),
       (1104, 'BASIC', 'basic.nation', '民族', 'SELECT', 1, 'SIS', 'STU,COU', '', 'ENUM',
        NULL,
        '["汉族","蒙古族","回族","藏族","维吾尔族","苗族","彝族","壮族","布依族","朝鲜族","满族","侗族","瑶族","白族","土家族","哈尼族","哈萨克族","傣族","黎族","傈僳族","佤族","畲族","高山族","拉祜族","水族","东乡族","纳西族","景颇族","柯尔克孜族","土族","达斡尔族","仫佬族","羌族","布朗族","撒拉族","毛南族","仡佬族","锡伯族","阿昌族","普米族","塔吉克族","怒族","乌孜别克族","俄罗斯族","鄂温克族","德昂族","保安族","裕固族","京族","塔塔尔族","独龙族","鄂伦春族","赫哲族","门巴族","珞巴族","基诺族","其他"]',
        0, 40, 1, '国标民族名称，值直接存中文名'),
       (1105, 'BASIC', 'basic.idCard', '身份证号', 'TEXT', 1, 'SIS', 'STU,COU', '', 'ID_CARD',
        NULL, NULL, 2, 50, 1, '18 位并校验末位；明文仅 COU 二次查看，查看写审计'),
       (1106, 'BASIC', 'basic.nativePlace', '籍贯', 'TEXT', 0, 'SIS', 'STU,COU', '', 'TEXT_LEN',
        '{"minLen":2,"maxLen":32}', NULL, 0, 60, 1, '省市区三级用空格分隔，如「浙江 杭州」'),
       (1107, 'BASIC', 'basic.dormNo', '宿舍号', 'TEXT', 0, 'COU', 'STU,COU', 'COU', 'DORM_NO',
        '{"regex":"^[0-9A-Za-z]{1,4}-[0-9]{3,4}$"}', NULL, 0, 70, 1, '辅导员维护，学生只读'),
       (1108, 'BASIC', 'basic.classId', '班级', 'SELECT', 1, 'SIS', 'STU,COU', '', 'CLASS_REF',
        NULL, NULL, 0, 80, 1, '必须命中 org_class 中状态为在读的记录'),
       (1109, 'BASIC', 'basic.majorId', '专业', 'SELECT', 1, 'SYS', 'STU,COU', '', 'DERIVED',
        NULL, NULL, 0, 90, 1, '由班级自动推导，界面只读，不接受独立提交'),
       (1110, 'BASIC', 'basic.phone', '手机', 'TEXT', 0, 'STU_CFM', 'STU,COU', 'STU,COU', 'MOBILE',
        NULL, NULL, 1, 100, 1, '修改进入待确认区，辅导员确认后生效'),
       (1111, 'BASIC', 'basic.email', '邮箱', 'TEXT', 0, 'STU_CFM', 'STU,COU', 'STU,COU', 'EMAIL',
        '{"maxLen":128}', NULL, 0, 110, 1, NULL),
       (1112, 'BASIC', 'basic.emergencyContact', '紧急联系人', 'TEXT', 0, 'STU_CFM', 'STU,COU', 'STU,COU', 'TEXT_LEN',
        '{"minLen":2,"maxLen":32}', NULL, 0, 120, 1, '可含关系说明，如「张建国（父）」'),
       (1113, 'BASIC', 'basic.emergencyPhone', '紧急联系电话', 'TEXT', 1, 'STU_CFM', 'STU,COU', 'STU,COU', 'EMERGENCY_TEL',
        NULL, NULL, 1, 130, 1, '手机或固话'),
       (1114, 'BASIC', 'basic.photo', '照片', 'FILE', 0, 'STU_CFM', 'STU,COU', 'STU,COU', 'FILE_ID',
        '{"maxCount":5,"maxBytes":2097152}', NULL, 0, 140, 1, '存文件 ID，不存路径；上传即去除 EXIF'),
       (1115, 'BASIC', 'basic.econLevel', '家庭经济状况', 'SELECT', 0, 'COU', 'STU,COU', 'COU', 'ENUM',
        NULL, '[{"value":"GOOD","label":"良好"},{"value":"NORMAL","label":"一般"},{"value":"DIFFICULT","label":"困难"}]',
        1, 150, 1, '资助认定结论，学生只读，页面不展示判定依据'),
       (1116, 'BASIC', 'basic.familyStructure', '家庭结构', 'SELECT', 0, 'COU', 'STU,COU', 'COU', 'ENUM',
        NULL, '[{"value":"ONLY_CHILD","label":"独生"},{"value":"NOT_ONLY","label":"非独生"},{"value":"OTHER","label":"其他"}]',
        0, 160, 1, NULL)
ON DUPLICATE KEY UPDATE field_name     = VALUES(field_name),
                        field_type     = VALUES(field_type),
                        required       = VALUES(required),
                        source         = VALUES(source),
                        visible_roles  = VALUES(visible_roles),
                        -- 可编辑角色由管理员在权限配置页维护，重跑种子不得覆盖
                        validator      = VALUES(validator),
                        rule_json      = VALUES(rule_json),
                        options_json   = VALUES(options_json),
                        sensitive_level = VALUES(sensitive_level),
                        sort_no        = VALUES(sort_no),
                        status         = VALUES(status),
                        remark         = VALUES(remark),
                        updated_at     = CURRENT_TIMESTAMP;


-- >>> 04_create_audit_log.sql
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


-- >>> 05_alter_field_def_source_field.sql
-- =====================================================================
-- 脚本 5：升级脚本 —— arc_field_def 增加 source_field 列并回填 SIS 映射
--
-- 作用：让「哪些字段由学生主数据同步」也变成定义驱动，而不是写在代码里。
--   source_field 存 stu_student 的列名（如 name / class_code），
--   StudentSyncService 据此把主数据值物化进 arc_profile_item；
--   为空表示该字段不从主数据同步（SYS / COU / STU_CFM 字段都为空）。
--
-- 幂等：MySQL 8.0 的 ALTER TABLE 不支持 ADD COLUMN IF NOT EXISTS，
--       因此先用 information_schema 判断，再用动态 SQL 执行，可重复运行。
-- 顺序：本脚本在 03 之后执行（03 只插字段定义，不回填本列）。
--       新库：01 已含该列，本脚本的 ALTER 自动跳过，只做回填。
--       存量库：先 ALTER 补列，再回填。
-- =====================================================================
SET @col_exists := (SELECT COUNT(*)
                    FROM information_schema.columns
                    WHERE table_schema = DATABASE()
                      AND table_name = 'arc_field_def'
                      AND column_name = 'source_field');
SET @ddl := IF(@col_exists = 0,
               'ALTER TABLE arc_field_def ADD COLUMN source_field VARCHAR(64) NULL COMMENT ''SIS 同步来源：stu_student 的列名，为空表示不同步'' AFTER `source`',
               'SELECT ''arc_field_def.source_field 已存在，跳过 ALTER'' AS note');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 回填板块一的 7 个 SIS 字段映射（值取自 stu_student 的同名列）
-- 专业 basic.majorId 是 DERIVED（由 org_class.major_name 推导），不参与同步。
-- ---------------------------------------------------------------------
UPDATE arc_field_def
SET source_field = CASE field_code
                       WHEN 'basic.name' THEN 'name'
                       WHEN 'basic.studentNo' THEN 'student_no'
                       WHEN 'basic.gender' THEN 'gender'
                       WHEN 'basic.nation' THEN 'nation'
                       WHEN 'basic.idCard' THEN 'id_card'
                       WHEN 'basic.nativePlace' THEN 'native_place'
                       WHEN 'basic.classId' THEN 'class_code'
                       ELSE NULL
    END
WHERE section_code = 'BASIC';

-- 自检：期望 7 行有 source_field，其余 9 行为 NULL
-- SELECT field_code, source, source_field FROM arc_field_def
--  WHERE section_code = 'BASIC' ORDER BY sort_no;


-- >>> 06_create_student_table.sql
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


-- >>> 07_insert_student_seed.sql
-- =====================================================================
-- 脚本 7：学生主数据种子（开发与联调用）
--
-- 覆盖三个班级，便于验证「本班辅导员可见、非本班不可见」：
--   RJ2401 → 辅导员 9001（李明 1001 / 王芳 1002）
--   RJ2402 → 辅导员 9001（陈晨 1003）
--   DZ2401 → 辅导员 9002（赵敏 1004）
-- 身份证号均通过 MOD 11-2 校验位校验，可直接用于 ID_CARD 校验器。
-- 幂等：ON DUPLICATE KEY UPDATE，可重复执行。
-- =====================================================================
INSERT INTO stu_student (id, student_no, name, gender, nation, id_card, native_place,
                         class_code, enrollment_year, status, sync_source, synced_at)
VALUES (1001, '2024010101', '李明', 'M', '汉族', '320102200201011232', '江苏 南京', 'RJ2401', 2024, 1, 'SIS', CURRENT_TIMESTAMP),
       (1002, '2024010102', '王芳', 'F', '回族', '320102200201015671', '江苏 南京', 'RJ2401', 2024, 1, 'SIS', CURRENT_TIMESTAMP),
       (1003, '2024010201', '陈晨', 'M', '汉族', '320102200201022345', '江苏 苏州', 'RJ2402', 2024, 1, 'SIS', CURRENT_TIMESTAMP),
       (1004, '2024010301', '赵敏', 'F', '满族', '320102200201034565', '江苏 无锡', 'DZ2401', 2024, 1, 'SIS', CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE student_no      = VALUES(student_no),
                        name            = VALUES(name),
                        gender          = VALUES(gender),
                        nation          = VALUES(nation),
                        id_card         = VALUES(id_card),
                        native_place    = VALUES(native_place),
                        class_code      = VALUES(class_code),
                        enrollment_year = VALUES(enrollment_year),
                        status          = VALUES(status),
                        sync_source     = VALUES(sync_source),
                        synced_at       = CURRENT_TIMESTAMP,
                        updated_at      = CURRENT_TIMESTAMP;

-- 自检：期望 4 条，且班级分布 RJ2401=2 / RJ2402=1 / DZ2401=1
-- SELECT class_code, COUNT(*) FROM stu_student GROUP BY class_code;


-- >>> 08_create_user_table.sql
-- =====================================================================
-- 脚本 8：建账号表 sys_user
--
-- 为什么需要这张表：
--   此前身份靠请求头 X-User-Id / X-User-Role 注入，任何调用方都能自封管理员，
--   字段级权限与审核权等于没有。登录后由本表校验账号密码并签发 JWT，
--   请求头方式关闭。
--
-- 口径约定：
--   1. id 与 stu_student.id / arc_profile.student_id 同口径（统一认证的用户 ID），
--      学生账号的 id 直接等于其 student_id；
--   2. username 统一为登录名：学生用学号，辅导员用 cou+工号，管理员用 admin；
--   3. password_hash 存 PBKDF2-SHA256 派生值，格式 pbkdf2$迭代次数$盐$哈希，
--      不存明文也不存可逆密文；
--   4. temp_grant 对应「管理员临时只读授权」（BR-S03）：管理员默认无业务数据权限；
--   5. 锁定与停用分开：status=0 表示停用，连续失败锁定在 login_attempts / locked_until
--      预留，本期只做停用与密码校验，锁定策略由需求文档 4.1 后续补充。
-- =====================================================================
CREATE TABLE IF NOT EXISTS sys_user
(
    id            BIGINT       NOT NULL COMMENT '用户 ID，与学生 ID 同口径',
    username      VARCHAR(64)  NOT NULL COMMENT '登录名：学号 / cou+工号 / admin',
    password_hash VARCHAR(255) NOT NULL COMMENT 'PBKDF2-SHA256，格式 pbkdf2$iter$salt$hash',
    display_name  VARCHAR(64)  NOT NULL COMMENT '展示名：学生姓名 / 辅导员姓名 / 管理员',
    role          VARCHAR(16)  NOT NULL COMMENT 'STU 学生 / COU 辅导员 / ADMIN 管理员',
    student_id    BIGINT       NULL COMMENT '学生账号关联的 stu_student.id，其他角色为空',
    temp_grant    TINYINT      NOT NULL DEFAULT 0 COMMENT '1 持临时只读授权（仅管理员有意义）',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 停用',
    last_login_at DATETIME     NULL COMMENT '最近一次登录成功时间',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_username (username),
    KEY idx_sys_user_student (student_id),
    KEY idx_sys_user_role (role, status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='平台账号（登录与身份）';


-- >>> 09_insert_user_seed.sql
-- =====================================================================
-- 脚本 9：账号种子（开发与联调用）
--
-- 统一密码：Passw0rd!（仅开发环境，生产必须由管理员初始化后强制修改）
-- 哈希格式 pbkdf2$120000$<salt-base64>$<hash-base64>，由 PasswordHasher 校验，
-- 迭代次数与盐都写在串里，后续调参不需要改数据。
--
-- 账号与数据范围对应关系，便于验证「本班可见、跨班 403」：
--   2024010101 李明 / 2024010102 王芳 / 2024010201 陈晨 → 辅导员 cou9001（软件 2401、2402）
--   2024010301 赵敏                                  → 辅导员 cou9002（电子 2401）
--   admin 持临时授权，可读业务数据但不可写
--
-- 幂等：ON DUPLICATE KEY UPDATE，可重复执行；不会覆盖已改过的密码之外的其他字段
-- （重复执行会把密码重置为上面的统一密码，便于联调）。
-- =====================================================================
INSERT INTO sys_user (id, username, password_hash, display_name, role, student_id, temp_grant, status)
VALUES (1001, '2024010101',
        'pbkdf2$120000$EB/nHxJBdb35jvcLwwI2XQ==$zB8dlDnBFeUuGusb0kOhq8L4v0C/3DOSb4UPtnCGbP0=',
        '李明', 'STU', 1001, 0, 1),
       (1002, '2024010102',
        'pbkdf2$120000$+D+1BFScgkeSstW7JoxoNw==$zJds65zWQZi6gOPglpaYqRR+eVQd8A2NHBL1rM2smSE=',
        '王芳', 'STU', 1002, 0, 1),
       (1003, '2024010201',
        'pbkdf2$120000$RViFFLg7h5cLBGZbHTPb/g==$AaqB2ZoV5l+l69HdmHZe+PVfBcPoXZNfadJQLBc8Lho=',
        '陈晨', 'STU', 1003, 0, 1),
       (1004, '2024010301',
        'pbkdf2$120000$OoDHPmcX4YXx3oguxPCeIw==$2lYMdmAPphUTXj+PeRplrq7zsE8Q5elZoDUmdzbWkEg=',
        '赵敏', 'STU', 1004, 0, 1),
       (9001, 'cou9001',
        'pbkdf2$120000$IIoT45t15s5pev6f0Ohj9w==$BsxF6tCjyHsQU/56XkL85YSgMU/cBJzBmVAhnxusPI4=',
        '辅导员·软件', 'COU', NULL, 0, 1),
       (9002, 'cou9002',
        'pbkdf2$120000$lyYGv3sfKoMQ7TOOTcEI8A==$o1P/2c+hUMH5hc3XljUsJxobAdOGTMmk2cGHc+5mZWI=',
        '辅导员·电子', 'COU', NULL, 0, 1),
       (9003, 'admin',
        'pbkdf2$120000$QIxIow8DfzoGZhvSH+ZC5Q==$i30wZtSnoAh+8z31oqEtcq738Sq2bDI1ft74mJBik7A=',
        '系统管理员', 'ADMIN', NULL, 1, 1),
       (9004, 'admin0',
        'pbkdf2$120000$QIxIow8DfzoGZhvSH+ZC5Q==$i30wZtSnoAh+8z31oqEtcq738Sq2bDI1ft74mJBik7A=',
        '管理员·无授权', 'ADMIN', NULL, 0, 1)
ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash),
                        display_name  = VALUES(display_name),
                        role          = VALUES(role),
                        student_id    = VALUES(student_id),
                        temp_grant    = VALUES(temp_grant),
                        status        = VALUES(status);


-- >>> 10_insert_academic_interest_field_defs.sql
-- 学业发展与兴趣特长共用 arc_profile_item，数组字段写入 value_json。
INSERT INTO arc_field_def
    (id, section_code, field_code, field_name, field_type, required, source,
     visible_roles, editable_roles, validator, rule_json, sort_no)
VALUES
    (1201, 'ACADEMIC', 'academic.goalShort', '短期学业目标', 'TEXT', 1, 'STU', 'STU,COU', 'STU', 'TEXT_LEN', '{"minLen":20,"maxLen":200}', 10),
    (1202, 'ACADEMIC', 'academic.goalLong', '长期发展目标', 'TEXTAREA', 0, 'STU', 'STU,COU', 'STU', 'TEXT_LEN', '{"minLen":20,"maxLen":300}', 20),
    (1203, 'ACADEMIC', 'academic.strongSubjects', '优势科目', 'MULTI_SELECT', 0, 'STU', 'STU,COU', 'STU', 'STRING_LIST', '{"maxCount":3}', 30),
    (1204, 'ACADEMIC', 'academic.weakSubjects', '待提升科目', 'MULTI_SELECT', 0, 'STU', 'STU,COU', 'STU', 'STRING_LIST', '{"maxCount":3}', 40),
    (1205, 'ACADEMIC', 'academic.abilityNote', '学科能力认定', 'TEXTAREA', 0, 'COU', 'STU,COU', 'COU', 'TEXT_LEN', '{"maxLen":300}', 50),
    (1301, 'INTEREST', 'interest.hobbies', '兴趣爱好', 'MULTI_SELECT', 1, 'STU', 'STU,COU', 'STU', 'STRING_LIST', '{"maxCount":5}', 10),
    (1302, 'INTEREST', 'interest.hobbyDesc', '兴趣描述', 'TEXTAREA', 0, 'STU', 'STU,COU', 'STU', 'TEXT_LEN', '{"maxLen":300}', 20),
    (1303, 'INTEREST', 'interest.skills', '技能特长', 'TEXT', 0, 'STU', 'STU,COU', 'STU', 'TEXT_LEN', '{"maxLen":300}', 30),
    (1304, 'INTEREST', 'interest.ideal', '理想描述', 'TEXTAREA', 0, 'STU', 'STU,COU', 'STU', 'TEXT_LEN', '{"maxLen":300}', 40)
ON DUPLICATE KEY UPDATE
    field_name = VALUES(field_name), field_type = VALUES(field_type), required = VALUES(required),
    source = VALUES(source), visible_roles = VALUES(visible_roles),
    validator = VALUES(validator), rule_json = VALUES(rule_json), sort_no = VALUES(sort_no);

-- 有意不更新 editable_roles：这一列是管理员在「字段权限配置」页保存的结果，
-- 重跑种子脚本不应把它改回默认值（与 03 / 12 同一口径）。
-- default_editable_roles 由 11 号的 ALTER 或 12 号末尾的回填补齐，本脚本不直接引用该列，
-- 以保证在未升级的老库上也能单独执行。


-- >>> 11_add_field_permission_columns.sql
-- =====================================================================
-- 脚本 11：字段编辑权限配置 —— 增加默认可编辑角色列
--
-- 背景：管理员模式支持按板块、按字段配置「仅学生 / 仅老师 / 师生均可 / 均不可」。
--       配置结果写入 arc_field_def.editable_roles（六个板块表单与保存校验共用的唯一来源），
--       同时保留初始化口径 default_editable_roles，用于页面标识「已调整」和「恢复默认」。
--
-- 幂等：列已存在时跳过 ALTER；回填只处理 NULL，可重复执行。
-- 注意：中文 Windows 的 mysql 客户端需加 --default-character-set=utf8mb4
-- =====================================================================

SET @col_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'arc_field_def'
      AND COLUMN_NAME = 'default_editable_roles'
);

SET @ddl := IF(
    @col_exists = 0,
    'ALTER TABLE arc_field_def ADD COLUMN default_editable_roles VARCHAR(64) NULL COMMENT ''默认可编辑角色（初始化口径），用于恢复默认'' AFTER editable_roles',
    'SELECT ''default_editable_roles already exists'' AS skipped'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 存量数据以当前 editable_roles 作为默认口径；后续管理员修改只写 editable_roles。
UPDATE arc_field_def
SET default_editable_roles = editable_roles
WHERE default_editable_roles IS NULL;

SELECT section_code, COUNT(*) AS fields, SUM(required = 1) AS required_fields
FROM arc_field_def
WHERE status = 1
GROUP BY section_code
ORDER BY section_code;


-- >>> 12_seed_section_field_defs.sql
-- =====================================================================
-- 脚本 12：补齐板块四至板块六的字段定义（权限配置覆盖六大板块的前提）
--
-- 板块二/三（学业发展、兴趣特长）已由 10_insert_academic_interest_field_defs.sql 定义，
-- 本脚本不再重复写，否则会覆盖那边的字段类型与校验器
-- （例如 strongSubjects 的 MULTI_SELECT 必须配 STRING_LIST，覆盖成 TEXT_LEN 会让保存直接报类型错误）。
--
-- 依据：《一期建设总流程与可执行方案》（V1.0 版）4.5–4.7 字段清单
-- 数量自检：LIFE 12 / CAREER 5 / MENTOR 6，合计 23；
--           加上板块一 16、板块二 5、板块三 4，六板块共 48 个主表字段。
-- 前置：必须先执行 11_add_field_permission_columns.sql（本脚本写入 default_editable_roles）。
-- 幂等：字段已存在时只更新元数据，**不覆盖 editable_roles**，
--       否则重跑脚本会把管理员配置的权限悄悄改回默认值。
-- =====================================================================

INSERT INTO arc_field_def (id, section_code, field_code, field_name, field_type, required, source,
                           visible_roles, editable_roles, default_editable_roles, validator, rule_json,
                           options_json, sensitive_level, sort_no, status, remark)
VALUES
-- 板块四 生活心理（LIFE）
(1401, 'LIFE', 'life.orgRole', '社团/学生工作职务', 'TEXT', 0, 'STU', 'STU,COU', 'STU', 'STU',
 'TEXT_LEN', '{"maxLen":64}', NULL, 0, 10, 1, '无职务填「无」'),
(1402, 'LIFE', 'life.orgLevel', '参与度', 'SELECT', 0, 'STU', 'STU,COU', 'STU', 'STU',
 'ENUM', NULL, '[{"value":"HIGH","label":"高"},{"value":"MID","label":"中"},{"value":"LOW","label":"低"}]',
 0, 20, 1, NULL),
(1403, 'LIFE', 'life.sleepHabit', '作息习惯', 'SELECT', 0, 'STU', 'STU,COU', 'STU', 'STU',
 'ENUM', NULL, '[{"value":"EARLY","label":"早睡早起"},{"value":"NORMAL","label":"规律"},{"value":"LATE","label":"晚睡"}]',
 0, 30, 1, NULL),
(1404, 'LIFE', 'life.exerciseFreq', '运动频率', 'SELECT', 0, 'STU', 'STU,COU', 'STU', 'STU',
 'ENUM', NULL, '[{"value":"NONE","label":"不运动"},{"value":"LOW","label":"每周 1–2 次"},{"value":"MID","label":"每周 3–4 次"},{"value":"HIGH","label":"每周 5 次以上"}]',
 0, 40, 1, NULL),
(1405, 'LIFE', 'life.personality', '个性描述', 'TEXTAREA', 0, 'STU', 'STU,COU', 'STU', 'STU',
 'TEXT_LEN', '{"maxLen":200}', NULL, 0, 50, 1, NULL),
(1406, 'LIFE', 'life.econSelf', '经济状况自评', 'SELECT', 0, 'STU', 'STU,COU', 'STU', 'STU',
 'ENUM', NULL, '[{"value":"GOOD","label":"良好"},{"value":"NORMAL","label":"一般"},{"value":"TIGHT","label":"紧张"}]',
 0, 60, 1, '与基本信息的家庭经济状况不一致时仅提示'),
(1407, 'LIFE', 'life.aidSource', '资助来源', 'MULTI_SELECT', 0, 'COU', 'STU,COU', 'COU', 'COU',
 'TEXT_LEN', '{"maxLen":128}', NULL, 0, 70, 1, '辅导员维护，学生只读'),
(1408, 'LIFE', 'life.stressScore', '压力自评', 'NUMBER', 1, 'STU', 'STU,COU', 'STU', 'STU',
 'TEXT_LEN', '{"maxLen":1}', NULL, 0, 80, 1, '整数 1–5；原始值仅学生本人可见'),
(1409, 'LIFE', 'life.stressSource', '压力来源', 'MULTI_SELECT', 0, 'STU', 'STU,COU', 'STU', 'STU',
 'TEXT_LEN', '{"maxLen":128}', NULL, 0, 90, 1, '最多 3 项'),
(1410, 'LIFE', 'life.mood', '整体情绪', 'SELECT', 1, 'STU', 'STU,COU', 'STU', 'STU',
 'ENUM', NULL, '[{"value":"GOOD","label":"良好"},{"value":"FAIR","label":"一般"},{"value":"LOW","label":"低落"}]',
 0, 100, 1, 'LOW 连续两次触发关注标记'),
(1411, 'LIFE', 'life.majorEvent', '重大事件备忘', 'TEXTAREA', 0, 'COU', 'COU', 'COU', 'COU',
 'TEXT_LEN', '{"maxLen":500}', NULL, 0, 110, 1, '辅导员工作记录，学生端不返回'),
(1412, 'LIFE', 'life.careFlag', '关注标记', 'CHECKBOX', 0, 'SYS', 'STU,COU', '', '',
 'TEXT_LEN', NULL, NULL, 0, 120, 1, '由压力与情绪派生，固定不可编辑'),

-- 板块五 职业规划（CAREER）
(1501, 'CAREER', 'career.intention', '发展意向', 'SELECT', 1, 'STU', 'STU,COU', 'STU', 'STU',
 'ENUM', NULL, '[{"value":"EMPLOY","label":"就业"},{"value":"POSTGRAD","label":"考研"},{"value":"OVERSEAS","label":"留学"},{"value":"STARTUP","label":"创业"},{"value":"UNDECIDED","label":"未定"}]',
 0, 10, 1, NULL),
(1502, 'CAREER', 'career.industry', '心仪行业', 'TEXT', 0, 'STU', 'STU,COU', 'STU', 'STU',
 'TEXT_LEN', '{"maxLen":64}', NULL, 0, 20, 1, NULL),
(1503, 'CAREER', 'career.position', '心仪岗位', 'TEXT', 0, 'STU', 'STU,COU', 'STU', 'STU',
 'TEXT_LEN', '{"maxLen":64}', NULL, 0, 30, 1, NULL),
(1504, 'CAREER', 'career.plan', '已做准备', 'TEXTAREA', 0, 'STU', 'STU,COU', 'STU', 'STU',
 'TEXT_LEN', '{"maxLen":300}', NULL, 0, 40, 1, NULL),
(1505, 'CAREER', 'career.support', '所需支持', 'TEXTAREA', 0, 'STU', 'STU,COU', 'STU', 'STU',
 'TEXT_LEN', '{"maxLen":300}', NULL, 0, 50, 1, NULL),

-- 板块六 导师寄语（MENTOR）
(1601, 'MENTOR', 'mentor.talkDate', '谈话日期', 'DATE', 1, 'COU', 'STU,COU', 'COU', 'COU',
 'TEXT_LEN', '{"maxLen":10}', NULL, 0, 10, 1, '不晚于今天'),
(1602, 'MENTOR', 'mentor.topic', '谈话主题', 'SELECT', 1, 'COU', 'STU,COU', 'COU', 'COU',
 'ENUM', NULL, '[{"value":"ACADEMIC","label":"学业规划"},{"value":"CAREER","label":"就业指导"},{"value":"PSYCH","label":"心理疏导"},{"value":"DAILY","label":"日常交流"}]',
 0, 20, 1, NULL),
(1603, 'MENTOR', 'mentor.advice', '建议与寄语', 'TEXTAREA', 1, 'COU', 'STU,COU', 'COU', 'COU',
 'TEXT_LEN', '{"minLen":20,"maxLen":1000}', NULL, 0, 30, 1, '学生可见'),
(1604, 'MENTOR', 'mentor.starLevel', '重要标记', 'RADIO', 0, 'COU', 'STU,COU', 'COU', 'COU',
 'ENUM', NULL, '[{"value":"0","label":"无"},{"value":"1","label":"★"},{"value":"2","label":"★★"}]',
 0, 40, 1, NULL),
(1605, 'MENTOR', 'mentor.feedback', '学生反馈与承诺', 'TEXTAREA', 0, 'STU', 'STU,COU', 'STU', 'STU',
 'TEXT_LEN', '{"maxLen":500}', NULL, 0, 50, 1, '每条谈话仅可提交一次'),
(1606, 'MENTOR', 'mentor.internalRemark', '内部备注', 'TEXTAREA', 0, 'COU', 'COU', 'COU', 'COU',
 'TEXT_LEN', '{"maxLen":500}', NULL, 0, 60, 1, '学生端不返回')
ON DUPLICATE KEY UPDATE
    field_name           = VALUES(field_name),
    field_type           = VALUES(field_type),
    required             = VALUES(required),
    source               = VALUES(source),
    visible_roles        = VALUES(visible_roles),
    validator            = VALUES(validator),
    rule_json            = VALUES(rule_json),
    options_json         = VALUES(options_json),
    sensitive_level      = VALUES(sensitive_level),
    sort_no              = VALUES(sort_no),
    status               = VALUES(status),
    remark               = VALUES(remark),
    -- 只补空值，不覆盖管理员已保存的权限与默认口径
    default_editable_roles = IF(default_editable_roles IS NULL,
                                VALUES(default_editable_roles), default_editable_roles),
    updated_at           = CURRENT_TIMESTAMP;

-- 兜底回填：10 号脚本与 11 号的 ALTER 谁先执行都无所谓，这里统一补默认口径。
UPDATE arc_field_def
SET default_editable_roles = editable_roles
WHERE default_editable_roles IS NULL;

-- 自检：六板块字段数应为 16 / 5 / 4 / 12 / 5 / 6，合计 48
SELECT section_code, COUNT(*) AS fields, SUM(required = 1) AS required_fields
FROM arc_field_def
WHERE status = 1
GROUP BY section_code
ORDER BY FIELD(section_code, 'BASIC', 'ACADEMIC', 'INTEREST', 'LIFE', 'CAREER', 'MENTOR');


-- >>> 13_life_field_def_align.sql
-- =====================================================================
-- 脚本 13：生活心理（LIFE）字段定义对齐
--
-- 目的：让 12 号种子脚本里的 LIFE 字段定义与需求 4.5 的口径、以及板块实现一致。
-- 只改 LIFE 的 12 个字段，不动板块二/三（学业、兴趣）与板块五/六（职业、导师）的定义，
-- 也不覆盖管理员已保存的 editable_roles —— 权限配置结果不能被升级脚本改回去。
--
-- 三处修正：
--   1. life.aidSource / life.stressSource 是多选（MULTI_SELECT），12 号却给了 TEXT_LEN。
--      保存接口按「校验器是否为 STRING_LIST」判断该字段是不是数组，配成 TEXT_LEN 会让
--      数组值直接报「字段值类型错误」，即「页面上能选、存不进去」。改为 STRING_LIST。
--   2. life.stressScore 的原始值只对学生本人可见（需求 4.5：COU 界面只显示 careFlag 与提示）。
--      visible_roles 收敛为 STU；它不是脱敏字段，脱敏位保持 0，避免 COU 拿到「***」这种
--      既不准确又会被误当成真实分值的展示。
--   3. life.careFlag 为派生字段（source=SYS），显式固定 editable_roles 为空，
--      防止历史上被误配成可编辑后出现手工改标记。
--
-- 幂等：可重复执行；执行顺序在 12 之后。
-- =====================================================================

UPDATE arc_field_def
SET validator   = 'STRING_LIST',
    rule_json   = NULL,
    remark      = '多选，命中字典 aid_type；辅导员维护，学生只读',
    updated_at  = CURRENT_TIMESTAMP
WHERE field_code IN ('life.aidSource', 'life.stressSource');

UPDATE arc_field_def
SET visible_roles  = 'STU',
    sensitive_level = 0,
    remark          = '整数 1–5；原始值仅学生本人可见，辅导员只看 careFlag',
    updated_at      = CURRENT_TIMESTAMP
WHERE field_code = 'life.stressScore';

UPDATE arc_field_def
SET editable_roles = '',
    remark         = '由压力自评与情绪派生，固定不可编辑',
    updated_at     = CURRENT_TIMESTAMP
WHERE field_code = 'life.careFlag';

-- 兜底回填：老库若在 11 号脚本之前建过 LIFE 字段，default_editable_roles 可能为空，
-- 会让「恢复默认」无值可恢复。只补空值，不改已有配置。
UPDATE arc_field_def
SET default_editable_roles = editable_roles
WHERE section_code = 'LIFE'
  AND default_editable_roles IS NULL;

-- 自检：LIFE 应为 12 个字段，其中必填 2 个（stressScore、mood），多选 2 个（STRING_LIST）
SELECT field_code, field_type, validator, required, visible_roles, editable_roles, sensitive_level
FROM arc_field_def
WHERE section_code = 'LIFE'
  AND status = 1
ORDER BY sort_no;


-- >>> 14_create_mentor_note.sql
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


-- >>> 15_permission_points.sql
-- =====================================================================
-- 脚本 15：权限点与角色授权（需求 7.2）
--
-- 目的：把「角色硬编码」升级为可查询、可配置的权限点体系。
--   sys_permission       权限点字典（code 形如 org:class:manage）
--   sys_role_permission  角色 → 权限点 → 数据范围（ALL/CLASS/SELF）
--
-- 说明：需求 7.2 文字写「34 条」，表内实列 36 条；以表为准入库。
--       未授给任何角色的权限点（export:sensitive:approve / grant:temp:*）
--       保留在字典中，供后续「学院领导」账号单独授权。
-- 幂等：可重复执行。
-- =====================================================================

CREATE TABLE IF NOT EXISTS sys_permission
(
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    code        VARCHAR(64)  NOT NULL COMMENT '权限码，如 org:class:manage',
    name        VARCHAR(64)  NOT NULL COMMENT '权限名称',
    module      VARCHAR(32)  NOT NULL COMMENT '所属模块：auth/org/account/config/archive/import/export/audit/grant/stat/system',
    description VARCHAR(255) NULL COMMENT '说明',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 停用',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_permission_code (code),
    KEY idx_sys_permission_module (module)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='权限点字典';

CREATE TABLE IF NOT EXISTS sys_role_permission
(
    id              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    role            VARCHAR(16) NOT NULL COMMENT 'STU / COU / ADMIN',
    permission_code VARCHAR(64) NOT NULL COMMENT '权限码',
    scope           VARCHAR(16) NOT NULL DEFAULT 'ALL' COMMENT 'ALL / CLASS / SELF',
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_role_permission (role, permission_code),
    KEY idx_sys_role_permission_code (permission_code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='角色权限绑定（含数据范围）';

INSERT INTO sys_permission (id, code, name, module, description)
VALUES (1, 'auth:login', '登录', 'auth', '账号密码登录'),
       (2, 'auth:password:self', '修改本人密码', 'auth', '修改当前登录账号的密码'),
       (3, 'org:college:manage', '学院维护', 'org', '学院增删改'),
       (4, 'org:major:manage', '专业维护', 'org', '专业增删改'),
       (5, 'org:class:manage', '班级维护与辅导员绑定', 'org', '班级增删改、绑定辅导员'),
       (6, 'org:class:read', '班级信息查看', 'org', '班级列表与详情'),
       (7, 'account:user:manage', '账号开通、停用、重置', 'account', '账号生命周期管理'),
       (8, 'account:role:assign', '角色分配', 'account', '修改账号角色'),
       (9, 'account:scope:manage', '数据范围配置', 'account', '配置账号数据范围'),
       (10, 'config:field:manage', '字段定义增删改', 'config', '字段定义维护'),
       (11, 'config:field:read', '字段定义查看', 'config', '字段定义只读'),
       (12, 'config:dict:manage', '字典维护', 'config', '数据字典维护'),
       (13, 'config:dict:read', '字典查看', 'config', '数据字典只读'),
       (14, 'config:param:manage', '系统参数', 'config', '系统参数维护'),
       (15, 'archive:profile:read', '档案读取', 'archive', '按数据范围读取档案'),
       (16, 'archive:profile:write', '档案写入', 'archive', '按数据范围写入档案'),
       (17, 'archive:profile:submit', '提交板块', 'archive', '提交本人板块'),
       (18, 'archive:profile:withdraw', '撤回', 'archive', '撤回本人已提交板块（限一次）'),
       (19, 'archive:profile:review', '审核通过/退回', 'archive', '本班板块审核'),
       (20, 'archive:profile:archive', '归档', 'archive', '本班毕业归档'),
       (21, 'archive:profile:unlock', '解冻', 'archive', '已归档档案解冻'),
       (22, 'archive:mentor:append', '追加谈话记录', 'archive', '本班导师谈话追加'),
       (23, 'archive:mentor:feedback', '提交学生反馈', 'archive', '学生本人反馈一次'),
       (24, 'archive:change:read', '变更记录查看', 'archive', '按范围查看字段变更'),
       (25, 'import:student:execute', '学生导入', 'import', '本班学生导入'),
       (26, 'import:rank:execute', '成绩导入', 'import', '本班成绩导入'),
       (27, 'import:cert:verify', '证书认定', 'import', '本班证书认定'),
       (28, 'export:profile:pdf', '单人 PDF', 'export', '本人/本班单人档案 PDF'),
       (29, 'export:class:excel', '班级 Excel', 'export', '本班汇总 Excel'),
       (30, 'export:sensitive:approve', '敏感导出审批', 'export', '学院领导审批敏感列导出'),
       (31, 'audit:log:read', '审计查询', 'audit', '按范围查询审计日志'),
       (32, 'audit:login:read', '登录日志', 'audit', '登录成功/失败日志'),
       (33, 'grant:temp:request', '申请临时授权', 'grant', '学院领导发起临时授权'),
       (34, 'grant:temp:approve', '审批临时授权', 'grant', '管理员审批临时授权'),
       (35, 'stat:completion:read', '完成率统计', 'stat', '按范围查看完成率'),
       (36, 'system:task:manage', '定时任务与运维', 'system', '任务与系统参数')
ON DUPLICATE KEY UPDATE name        = VALUES(name),
                        module      = VALUES(module),
                        description = VALUES(description),
                        status      = 1;

-- 角色授权：与需求 7.2 矩阵一致；COU 的「本班」与 STU 的「本人」用 scope 表达。
INSERT INTO sys_role_permission (role, permission_code, scope)
VALUES ('ADMIN', 'auth:login', 'ALL'),
       ('ADMIN', 'auth:password:self', 'ALL'),
       ('ADMIN', 'org:college:manage', 'ALL'),
       ('ADMIN', 'org:major:manage', 'ALL'),
       ('ADMIN', 'org:class:manage', 'ALL'),
       ('ADMIN', 'org:class:read', 'ALL'),
       ('ADMIN', 'account:user:manage', 'ALL'),
       ('ADMIN', 'account:role:assign', 'ALL'),
       ('ADMIN', 'account:scope:manage', 'ALL'),
       ('ADMIN', 'config:field:manage', 'ALL'),
       ('ADMIN', 'config:field:read', 'ALL'),
       ('ADMIN', 'config:dict:manage', 'ALL'),
       ('ADMIN', 'config:dict:read', 'ALL'),
       ('ADMIN', 'config:param:manage', 'ALL'),
       ('ADMIN', 'archive:profile:unlock', 'ALL'),
       ('ADMIN', 'archive:change:read', 'ALL'),
       ('ADMIN', 'audit:log:read', 'ALL'),
       ('ADMIN', 'audit:login:read', 'ALL'),
       ('ADMIN', 'stat:completion:read', 'ALL'),
       ('ADMIN', 'system:task:manage', 'ALL'),

       ('COU', 'auth:login', 'ALL'),
       ('COU', 'auth:password:self', 'ALL'),
       ('COU', 'org:class:read', 'CLASS'),
       ('COU', 'config:field:read', 'ALL'),
       ('COU', 'config:dict:read', 'ALL'),
       ('COU', 'archive:profile:read', 'CLASS'),
       ('COU', 'archive:profile:write', 'CLASS'),
       ('COU', 'archive:profile:review', 'CLASS'),
       ('COU', 'archive:profile:archive', 'CLASS'),
       ('COU', 'archive:mentor:append', 'CLASS'),
       ('COU', 'archive:change:read', 'SELF'),
       ('COU', 'import:student:execute', 'CLASS'),
       ('COU', 'import:rank:execute', 'CLASS'),
       ('COU', 'import:cert:verify', 'CLASS'),
       ('COU', 'export:profile:pdf', 'CLASS'),
       ('COU', 'export:class:excel', 'CLASS'),
       ('COU', 'audit:log:read', 'SELF'),
       ('COU', 'stat:completion:read', 'CLASS'),

       ('STU', 'auth:login', 'ALL'),
       ('STU', 'auth:password:self', 'ALL'),
       ('STU', 'config:dict:read', 'ALL'),
       ('STU', 'archive:profile:read', 'SELF'),
       ('STU', 'archive:profile:write', 'SELF'),
       ('STU', 'archive:profile:submit', 'SELF'),
       ('STU', 'archive:profile:withdraw', 'SELF'),
       ('STU', 'archive:mentor:feedback', 'SELF'),
       ('STU', 'archive:change:read', 'SELF'),
       ('STU', 'export:profile:pdf', 'SELF'),
       ('STU', 'audit:log:read', 'SELF')
ON DUPLICATE KEY UPDATE scope = VALUES(scope);

SELECT role, COUNT(*) AS permissions FROM sys_role_permission GROUP BY role ORDER BY role;
SELECT COUNT(*) AS permission_total FROM sys_permission WHERE status = 1;


-- >>> 16_org_structure.sql
-- =====================================================================
-- 脚本 16：组织架构（学院 / 专业 / 班级）
--
-- 背景：需求 F-02 要求学院、专业、班级可维护，辅导员可绑定；
--       既有 org_class 只有班级，且专业用 major_name 文本表示。
-- 本脚本新增 org_college / org_major，并给 org_class 补 college_id / major_id，
-- 保留 major_name 列以兼容 basic.majorId 的历史口径。
--
-- 幂等：表用 IF NOT EXISTS；列先查 information_schema 再 ALTER；种子用 UPSERT。
-- =====================================================================

CREATE TABLE IF NOT EXISTS org_college
(
    id           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    college_code VARCHAR(32)  NOT NULL COMMENT '学院编码，全局唯一',
    college_name VARCHAR(64)  NOT NULL COMMENT '学院名称',
    status       TINYINT     NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 停用',
    created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_org_college_code (college_code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='学院';

CREATE TABLE IF NOT EXISTS org_major
(
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    college_id BIGINT      NOT NULL COMMENT '所属学院 org_college.id',
    major_code VARCHAR(32)  NOT NULL COMMENT '专业编码，全局唯一',
    major_name VARCHAR(64)  NOT NULL COMMENT '专业名称',
    status     TINYINT     NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 停用',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_org_major_code (major_code),
    KEY idx_org_major_college (college_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='专业';

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'org_class' AND COLUMN_NAME = 'college_id');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE org_class ADD COLUMN college_id BIGINT NULL COMMENT ''所属学院 org_college.id'' AFTER class_name',
    'SELECT ''org_class.college_id already exists'' AS skipped');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'org_class' AND COLUMN_NAME = 'major_id');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE org_class ADD COLUMN major_id BIGINT NULL COMMENT ''所属专业 org_major.id'' AFTER college_id',
    'SELECT ''org_class.major_id already exists'' AS skipped');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO org_college (id, college_code, college_name, status)
VALUES (3001, 'ZJGCS', '卓越工程师学院', 1)
ON DUPLICATE KEY UPDATE college_name = VALUES(college_name), status = 1;

INSERT INTO org_major (id, college_id, major_code, major_name, status)
VALUES (3101, 3001, 'RJGC', '软件工程', 1),
       (3102, 3001, 'DZXX', '电子信息工程', 1)
ON DUPLICATE KEY UPDATE college_id = VALUES(college_id), major_name = VALUES(major_name), status = 1;

UPDATE org_class c
JOIN org_major m ON m.major_name = c.major_name
SET c.college_id = m.college_id,
    c.major_id   = m.id
WHERE c.college_id IS NULL OR c.major_id IS NULL;

SELECT 'college' AS item, COUNT(*) AS cnt FROM org_college
UNION ALL SELECT 'major', COUNT(*) FROM org_major
UNION ALL SELECT 'class_with_major', COUNT(*) FROM org_class WHERE major_id IS NOT NULL;


-- >>> 17_student_import.sql
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


-- >>> 18_academic_records.sql
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


-- >>> 19_academic_import_permissions.sql
-- =====================================================================
-- 脚本 19：学业成果导入权限（F-10）
--
-- 7.2 矩阵中 import:rank:execute 只给 COU；AC-033/034 由辅导员执行，
-- 管理员在演示/兜底场景也需要导入，这里补 ADMIN 授权（COU 仍按本班校验）。
-- 幂等：可重复执行。
-- =====================================================================

INSERT INTO sys_role_permission (role, permission_code, scope)
VALUES ('ADMIN', 'import:rank:execute', 'ALL'),
       ('ADMIN', 'import:cert:verify', 'ALL')
ON DUPLICATE KEY UPDATE scope = VALUES(scope);

SELECT role, permission_code, scope FROM sys_role_permission
WHERE permission_code IN ('import:rank:execute', 'import:cert:verify')
ORDER BY role, permission_code;


-- >>> 20_profile_export.sql
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


-- >>> 21_login_log.sql
-- =====================================================================
-- 脚本 21：登录日志（F-14 / AC-050–052）
--
-- 与 sys_audit_log 分开：登录失败没有可用身份上下文，单独存账号、结果、
-- 原因、IP 与 UA；不存密码/令牌。
-- 幂等：可重复执行。
-- =====================================================================

CREATE TABLE IF NOT EXISTS sys_login_log
(
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id    BIGINT       NULL COMMENT '用户 ID（账号不存在时为空）',
    username   VARCHAR(64)  NULL COMMENT '尝试登录的账号',
    success    TINYINT      NOT NULL COMMENT '1 成功 / 0 失败',
    reason     VARCHAR(255) NULL COMMENT '失败原因或动作（LOGIN_FAIL/DISABLED/LOGOUT）',
    ip         VARCHAR(64)  NULL COMMENT '客户端 IP',
    user_agent VARCHAR(255) NULL COMMENT 'UA',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_login_log_username (username, created_at),
    KEY idx_login_log_success (success, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='登录成功/失败与登出日志';

SELECT COUNT(*) AS login_log_table FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name = 'sys_login_log';


-- >>> 22_id_card_encryption.sql
-- =====================================================================
-- 脚本 22：身份证号加密列（需求 7.3）
--
-- stu_student.id_card 原为明文；新增 id_card_cipher（AES-256-GCM，enc:v1: 前缀）
-- 与 id_card_hash（SHA-256），并给哈希建唯一键用于去重。
-- 存量数据由应用启动时的 IdCardMigration 迁移（加密后清空明文列）。
-- 幂等：列/索引不存在时才创建。
-- =====================================================================

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'stu_student' AND COLUMN_NAME = 'id_card_cipher');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE stu_student ADD COLUMN id_card_cipher VARCHAR(512) NULL COMMENT ''身份证密文 enc:v1:'' AFTER id_card',
    'SELECT ''id_card_cipher already exists'' AS skipped');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'stu_student' AND COLUMN_NAME = 'id_card_hash');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE stu_student ADD COLUMN id_card_hash CHAR(64) NULL COMMENT ''SHA-256（十六进制）'' AFTER id_card_cipher',
    'SELECT ''id_card_hash already exists'' AS skipped');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'stu_student' AND INDEX_NAME = 'uk_stu_id_card_hash');
SET @ddl := IF(@idx_exists = 0,
    'ALTER TABLE stu_student ADD UNIQUE KEY uk_stu_id_card_hash (id_card_hash)',
    'SELECT ''uk_stu_id_card_hash already exists'' AS skipped');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT COUNT(*) AS total_students, SUM(id_card IS NOT NULL) AS plaintext_rows,
       SUM(id_card_cipher IS NOT NULL) AS cipher_rows
FROM stu_student;


-- >>> 23_temp_grant.sql
-- =====================================================================
-- 脚本 23：临时授权申请/审批（F-17 / AC-053）
--
-- sys_approval 为通用审批单，后续敏感导出（F-13）、申诉（F-18）、
-- 归档解冻共用；本脚本只启用 TEMP_GRANT。
-- 临时授权最长 4 小时；sys_user.temp_grant_expire_at 用于运行时判定，
-- 旧令牌未过期也会在到期后失效。
-- 幂等：可重复执行。
-- =====================================================================

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user'
                      AND COLUMN_NAME = 'temp_grant_expire_at');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE sys_user ADD COLUMN temp_grant_expire_at DATETIME NULL COMMENT ''临时授权到期时间'' AFTER temp_grant',
    'SELECT ''temp_grant_expire_at already exists'' AS skipped');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS sys_approval
(
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    approval_type   VARCHAR(32)  NOT NULL COMMENT 'TEMP_GRANT / EXPORT_SENSITIVE / APPEAL / UNFREEZE',
    applicant_id    BIGINT       NOT NULL COMMENT '申请人',
    target_json     VARCHAR(1000) NULL COMMENT '申请参数，如 {"hours":2}',
    reason          VARCHAR(500) NOT NULL COMMENT '申请理由',
    status          VARCHAR(16)  NOT NULL COMMENT 'PENDING / APPROVED / REJECTED / EXPIRED / REVOKED',
    approver_id     BIGINT       NULL COMMENT '审批人',
    approver_remark VARCHAR(500) NULL COMMENT '审批意见',
    approved_at     DATETIME     NULL COMMENT '审批时间',
    expire_at       DATETIME     NULL COMMENT '生效到期时间',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_approval_status (status, created_at),
    KEY idx_approval_applicant (applicant_id, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='审批单（临时授权/敏感导出/申诉/解冻）';

-- 第二个管理员：审批人不能是申请人本人，联调需要一个独立审批账号
INSERT INTO sys_user (id, username, password_hash, display_name, role, student_id, temp_grant, status)
SELECT 9005, 'admin1', password_hash, '学院领导', 'ADMIN', NULL, 0, 1
FROM sys_user WHERE username = 'admin0'
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), role = VALUES(role), status = 1;

-- 阶段 A 预留但未授出的两个权限点，本阶段启用
INSERT INTO sys_role_permission (role, permission_code, scope)
VALUES ('ADMIN', 'grant:temp:request', 'ALL'),
       ('ADMIN', 'grant:temp:approve', 'ALL')
ON DUPLICATE KEY UPDATE scope = VALUES(scope);

SELECT COUNT(*) AS approval_table FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name = 'sys_approval';
SELECT role, COUNT(*) AS permissions FROM sys_role_permission WHERE role = 'ADMIN' GROUP BY role;


-- >>> 24_change_appeal.sql
-- =====================================================================
-- 脚本 24：变更记录申诉（F-18 / AC-070）
--
-- 复用 sys_approval：APPEAL 类型额外记录 student_id 与 biz_id(change_id)，
-- 便于按学生/班级和变更记录反查。幂等。
-- =====================================================================

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_approval' AND COLUMN_NAME = 'student_id');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE sys_approval ADD COLUMN student_id BIGINT NULL COMMENT ''关联学生（申诉/导出）'' AFTER applicant_id',
    'SELECT ''student_id already exists'' AS skipped');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_approval' AND COLUMN_NAME = 'biz_id');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE sys_approval ADD COLUMN biz_id BIGINT NULL COMMENT ''关联业务 ID（change_id 等）'' AFTER student_id',
    'SELECT ''biz_id already exists'' AS skipped');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_approval' AND INDEX_NAME = 'idx_approval_student');
SET @ddl := IF(@idx_exists = 0,
    'ALTER TABLE sys_approval ADD KEY idx_approval_student (student_id, status)',
    'SELECT ''idx_approval_student already exists'' AS skipped');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT COUNT(*) AS appeal_columns FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_approval' AND COLUMN_NAME IN ('student_id', 'biz_id');


-- >>> 25_class_export.sql
-- =====================================================================
-- 脚本 25：班级 Excel 敏感列审批（F-13 / AC-044）
--
-- 复用 sys_approval 的 EXPORT_SENSITIVE；审批通过后 4 小时内可导出一次/多次
-- 本班敏感列。审批人需 export:sensitive:approve（阶段 A 预留，本脚本授给 ADMIN）。
-- 幂等。
-- =====================================================================

INSERT INTO sys_role_permission (role, permission_code, scope)
VALUES ('ADMIN', 'export:sensitive:approve', 'ALL')
ON DUPLICATE KEY UPDATE scope = VALUES(scope);

SELECT role, permission_code, scope FROM sys_role_permission
WHERE permission_code = 'export:sensitive:approve';


-- >>> 26_dict.sql
-- =====================================================================
-- 脚本 26：数据字典（F-15 / AC-008）
--
-- sys_dict 描述字典类型，并可绑定一个字段编码；sys_dict_item 为字典项。
-- 停用项不删除，字段 options_json 同步为 disabled=true，新填报校验拒绝，
-- 历史值仍按 label 展示并在界面标注「已停用」。
-- 幂等。
-- =====================================================================

CREATE TABLE IF NOT EXISTS sys_dict
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    dict_code  VARCHAR(64)  NOT NULL COMMENT '字典编码，如 hobby',
    dict_name  VARCHAR(64)  NOT NULL COMMENT '字典名称',
    field_code VARCHAR(64)  NULL COMMENT '绑定的字段编码，可空',
    remark     VARCHAR(255) NULL,
    status     TINYINT      NOT NULL DEFAULT 1,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_dict_code (dict_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='数据字典';

CREATE TABLE IF NOT EXISTS sys_dict_item
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    dict_id    BIGINT       NOT NULL,
    item_value VARCHAR(128) NOT NULL,
    item_label VARCHAR(128) NOT NULL,
    sort_no    INT          NOT NULL DEFAULT 0,
    status     TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 停用（历史保留）',
    builtin    TINYINT      NOT NULL DEFAULT 0,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_dict_item (dict_id, item_value),
    KEY idx_dict_item_status (dict_id, status, sort_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='字典项';

INSERT INTO sys_dict (dict_code, dict_name, field_code, remark)
VALUES ('hobby', '兴趣爱好', 'interest.hobbies', '兴趣特长板块的兴趣爱好选项')
ON DUPLICATE KEY UPDATE dict_name = VALUES(dict_name), field_code = VALUES(field_code);

SET @hobby_id := (SELECT id FROM sys_dict WHERE dict_code = 'hobby');

INSERT INTO sys_dict_item (dict_id, item_value, item_label, sort_no, status, builtin)
VALUES (@hobby_id, '摄影', '摄影', 10, 1, 1),
       (@hobby_id, '旅游', '旅游', 20, 1, 1),
       (@hobby_id, '阅读', '阅读', 30, 1, 1),
       (@hobby_id, '运动', '运动', 40, 1, 1),
       (@hobby_id, '音乐', '音乐', 50, 1, 1),
       (@hobby_id, '其他', '其他', 60, 1, 1)
ON DUPLICATE KEY UPDATE item_label = VALUES(item_label), sort_no = VALUES(sort_no);

-- 绑定的字段：把字典项同步为字段选项（含停用项），保证历史值可显示 label
UPDATE arc_field_def
SET options_json = '[{"value":"摄影","label":"摄影"},{"value":"旅游","label":"旅游"},{"value":"阅读","label":"阅读"},{"value":"运动","label":"运动"},{"value":"音乐","label":"音乐"},{"value":"其他","label":"其他"}]'
WHERE field_code = 'interest.hobbies';

SELECT d.dict_code, COUNT(i.id) AS items, SUM(i.status = 0) AS disabled
FROM sys_dict d LEFT JOIN sys_dict_item i ON i.dict_id = d.id
GROUP BY d.id, d.dict_code;


-- >>> 27_notification.sql
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


-- >>> 28_archive_unfreeze.sql
-- =====================================================================
-- 脚本 28：归档解冻审批 + MENTOR 更正字段（B-6）
--
-- 解冻复用 sys_approval(UNFREEZE)，不新增表；MENTOR 归档后只追加更正记录，
-- 不覆盖原文（BR-S07），因此新增 corrects_note_id / correction_reason。
-- 幂等。
-- =====================================================================

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'arc_mentor_note'
                      AND COLUMN_NAME = 'corrects_note_id');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE arc_mentor_note ADD COLUMN corrects_note_id BIGINT NULL COMMENT ''更正的原记录 ID'' AFTER student_id',
    'SELECT ''corrects_note_id already exists'' AS skipped');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'arc_mentor_note'
                      AND COLUMN_NAME = 'correction_reason');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE arc_mentor_note ADD COLUMN correction_reason VARCHAR(255) NULL COMMENT ''更正原因'' AFTER corrects_note_id',
    'SELECT ''correction_reason already exists'' AS skipped');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT COUNT(*) AS mentor_correction_columns FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'arc_mentor_note'
  AND COLUMN_NAME IN ('corrects_note_id', 'correction_reason');


-- >>> 29_file.sql
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

