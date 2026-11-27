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
