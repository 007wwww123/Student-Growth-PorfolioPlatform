-- =====================================================================
-- 脚本 26：数据字典（F-15 / AC-008）
--
-- sys_dict 描述字典类型，并可绑定一个字段编码；sys_dict_item 为字典项。
-- 停用项不删除，字段 options_json 同步为 disabled=true，新填报校验拒绝，
-- 历史值仍按 label 展示并在界面标注「已停用」。
-- 幂等。
-- =====================================================================

CREATE TABLE IF NOT EXISTS sys_dict
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    dict_code  VARCHAR(64)  NOT NULL COMMENT '字典编码，如 hobby',
    dict_name  VARCHAR(64)  NOT NULL COMMENT '字典名称',
    field_code VARCHAR(64)  NULL COMMENT '绑定的字段编码，可空',
    remark     VARCHAR(255) NULL,
    status     TINYINT      NOT NULL DEFAULT 1,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_dict_code (dict_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='数据字典';

CREATE TABLE IF NOT EXISTS sys_dict_item
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    dict_id    BIGINT       NOT NULL,
    item_value VARCHAR(128) NOT NULL,
    item_label VARCHAR(128) NOT NULL,
    sort_no    INT          NOT NULL DEFAULT 0,
    status     TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 停用（历史保留）',
    builtin    TINYINT      NOT NULL DEFAULT 0,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_dict_item (dict_id, item_value),
    KEY idx_dict_item_status (dict_id, status, sort_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='字典项';

INSERT INTO sys_dict (dict_code, dict_name, field_code, remark)
VALUES ('hobby', '兴趣爱好', 'interest.hobbies', '兴趣特长板块的兴趣爱好选项')
ON DUPLICATE KEY UPDATE dict_name = VALUES(dict_name), field_code = VALUES(field_code);

SET @hobby_id := (SELECT id FROM sys_dict WHERE dict_code = 'hobby');

INSERT INTO sys_dict_item (dict_id, item_value, item_label, sort_no, status, builtin)
VALUES (@hobby_id, '摄影', '摄影', 10, 1, 1),
       (@hobby_id, '旅游', '旅游', 20, 1, 1),
       (@hobby_id, '阅读', '阅读', 30, 1, 1),
       (@hobby_id, '运动', '运动', 40, 1, 1),
       (@hobby_id, '音乐', '音乐', 50, 1, 1),
       (@hobby_id, '其他', '其他', 60, 1, 1)
ON DUPLICATE KEY UPDATE item_label = VALUES(item_label), sort_no = VALUES(sort_no);

-- 绑定的字段：把字典项同步为字段选项（含停用项），保证历史值可显示 label
UPDATE arc_field_def
SET options_json = '[{"value":"摄影","label":"摄影"},{"value":"旅游","label":"旅游"},{"value":"阅读","label":"阅读"},{"value":"运动","label":"运动"},{"value":"音乐","label":"音乐"},{"value":"其他","label":"其他"}]'
WHERE field_code = 'interest.hobbies';

SELECT d.dict_code, COUNT(i.id) AS items, SUM(i.status = 0) AS disabled
FROM sys_dict d LEFT JOIN sys_dict_item i ON i.dict_id = d.id
GROUP BY d.id, d.dict_code;
