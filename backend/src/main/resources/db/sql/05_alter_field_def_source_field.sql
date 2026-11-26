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
