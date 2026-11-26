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
