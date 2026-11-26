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
