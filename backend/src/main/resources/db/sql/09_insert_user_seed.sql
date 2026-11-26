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
