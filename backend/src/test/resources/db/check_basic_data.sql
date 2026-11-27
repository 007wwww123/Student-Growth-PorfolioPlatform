-- Read-only integrity checks. Each result counts inconsistent rows; all should be zero.
SELECT 'student_account_binding' AS rule_name, COUNT(*) AS violations
FROM sys_user u LEFT JOIN stu_student s ON s.id = u.student_id
WHERE u.role = 'STU' AND (s.id IS NULL OR u.id <> s.id)
UNION ALL
SELECT 'student_class', COUNT(*)
FROM stu_student s LEFT JOIN org_class c ON c.class_code = s.class_code
WHERE s.status = 1 AND c.id IS NULL
UNION ALL
SELECT 'class_reviewer', COUNT(*)
FROM org_class c LEFT JOIN sys_user u ON u.id = c.counselor_user_id
WHERE c.status = 1 AND (u.id IS NULL OR u.role <> 'COU' OR u.status <> 1)
UNION ALL
SELECT 'field_scope', COUNT(*)
FROM arc_profile_item i LEFT JOIN arc_profile p ON p.id = i.profile_id
LEFT JOIN arc_field_def d ON d.field_code = i.field_code
WHERE p.id IS NULL OR d.id IS NULL OR p.section_code <> d.section_code
UNION ALL
SELECT 'profile_state', COUNT(*) FROM arc_profile
WHERE status NOT IN ('DRAFT','SUBMITTED','RETURNED','APPROVED','LOCKED')
   OR completeness NOT BETWEEN 0 AND 100 OR withdraw_count NOT BETWEEN 0 AND 1
UNION ALL
SELECT 'field_json', COUNT(*) FROM arc_profile_item
WHERE (value_json IS NOT NULL AND JSON_VALID(value_json) = 0)
   OR (pending_json IS NOT NULL AND JSON_VALID(pending_json) = 0);
