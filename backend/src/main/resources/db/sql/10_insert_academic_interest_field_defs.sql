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
