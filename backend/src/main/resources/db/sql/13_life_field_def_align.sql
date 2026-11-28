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
