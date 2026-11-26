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
                        editable_roles = VALUES(editable_roles),
                        validator      = VALUES(validator),
                        rule_json      = VALUES(rule_json),
                        options_json   = VALUES(options_json),
                        sensitive_level = VALUES(sensitive_level),
                        sort_no        = VALUES(sort_no),
                        status         = VALUES(status),
                        remark         = VALUES(remark),
                        updated_at     = CURRENT_TIMESTAMP;
