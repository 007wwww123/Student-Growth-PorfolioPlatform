-- 数据一致性核对（11.5）：应全部为 0（除统计项）
SELECT 'duplicate_profiles' AS check_name, COUNT(*) AS bad_rows
FROM (SELECT student_id, section_code, academic_year, COUNT(*) c
      FROM arc_profile GROUP BY student_id, section_code, academic_year HAVING c > 1) t
UNION ALL
SELECT 'orphan_items', COUNT(*)
FROM arc_profile_item i LEFT JOIN arc_profile p ON p.id = i.profile_id WHERE p.id IS NULL
UNION ALL
SELECT 'profile_without_student', COUNT(*)
FROM arc_profile p LEFT JOIN stu_student s ON s.id = p.student_id WHERE s.id IS NULL
UNION ALL
SELECT 'items_without_field_def', COUNT(*)
FROM arc_profile_item i LEFT JOIN arc_field_def f ON f.field_code = i.field_code WHERE f.id IS NULL
UNION ALL
SELECT 'completed_without_six_approved', COUNT(*)
FROM (SELECT p.student_id, p.academic_year, SUM(p.status IN ('APPROVED','LOCKED')) ok, MAX(p.completed) completed
      FROM arc_profile p GROUP BY p.student_id, p.academic_year HAVING completed = 1 AND ok < 6) t;

SELECT 'students' AS metric, COUNT(*) AS value FROM stu_student
UNION ALL SELECT 'profiles', COUNT(*) FROM arc_profile
UNION ALL SELECT 'items', COUNT(*) FROM arc_profile_item
UNION ALL SELECT 'change_logs', COUNT(*) FROM arc_change_log
UNION ALL SELECT 'flyway_version', MAX(version) FROM flyway_schema_history;
