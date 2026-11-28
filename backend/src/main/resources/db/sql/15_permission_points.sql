-- =====================================================================
-- 脚本 15：权限点与角色授权（需求 7.2）
--
-- 目的：把「角色硬编码」升级为可查询、可配置的权限点体系。
--   sys_permission       权限点字典（code 形如 org:class:manage）
--   sys_role_permission  角色 → 权限点 → 数据范围（ALL/CLASS/SELF）
--
-- 说明：需求 7.2 文字写「34 条」，表内实列 36 条；以表为准入库。
--       未授给任何角色的权限点（export:sensitive:approve / grant:temp:*）
--       保留在字典中，供后续「学院领导」账号单独授权。
-- 幂等：可重复执行。
-- =====================================================================

CREATE TABLE IF NOT EXISTS sys_permission
(
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    code        VARCHAR(64)  NOT NULL COMMENT '权限码，如 org:class:manage',
    name        VARCHAR(64)  NOT NULL COMMENT '权限名称',
    module      VARCHAR(32)  NOT NULL COMMENT '所属模块：auth/org/account/config/archive/import/export/audit/grant/stat/system',
    description VARCHAR(255) NULL COMMENT '说明',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 停用',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_permission_code (code),
    KEY idx_sys_permission_module (module)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='权限点字典';

CREATE TABLE IF NOT EXISTS sys_role_permission
(
    id              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    role            VARCHAR(16) NOT NULL COMMENT 'STU / COU / ADMIN',
    permission_code VARCHAR(64) NOT NULL COMMENT '权限码',
    scope           VARCHAR(16) NOT NULL DEFAULT 'ALL' COMMENT 'ALL / CLASS / SELF',
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_role_permission (role, permission_code),
    KEY idx_sys_role_permission_code (permission_code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='角色权限绑定（含数据范围）';

INSERT INTO sys_permission (id, code, name, module, description)
VALUES (1, 'auth:login', '登录', 'auth', '账号密码登录'),
       (2, 'auth:password:self', '修改本人密码', 'auth', '修改当前登录账号的密码'),
       (3, 'org:college:manage', '学院维护', 'org', '学院增删改'),
       (4, 'org:major:manage', '专业维护', 'org', '专业增删改'),
       (5, 'org:class:manage', '班级维护与辅导员绑定', 'org', '班级增删改、绑定辅导员'),
       (6, 'org:class:read', '班级信息查看', 'org', '班级列表与详情'),
       (7, 'account:user:manage', '账号开通、停用、重置', 'account', '账号生命周期管理'),
       (8, 'account:role:assign', '角色分配', 'account', '修改账号角色'),
       (9, 'account:scope:manage', '数据范围配置', 'account', '配置账号数据范围'),
       (10, 'config:field:manage', '字段定义增删改', 'config', '字段定义维护'),
       (11, 'config:field:read', '字段定义查看', 'config', '字段定义只读'),
       (12, 'config:dict:manage', '字典维护', 'config', '数据字典维护'),
       (13, 'config:dict:read', '字典查看', 'config', '数据字典只读'),
       (14, 'config:param:manage', '系统参数', 'config', '系统参数维护'),
       (15, 'archive:profile:read', '档案读取', 'archive', '按数据范围读取档案'),
       (16, 'archive:profile:write', '档案写入', 'archive', '按数据范围写入档案'),
       (17, 'archive:profile:submit', '提交板块', 'archive', '提交本人板块'),
       (18, 'archive:profile:withdraw', '撤回', 'archive', '撤回本人已提交板块（限一次）'),
       (19, 'archive:profile:review', '审核通过/退回', 'archive', '本班板块审核'),
       (20, 'archive:profile:archive', '归档', 'archive', '本班毕业归档'),
       (21, 'archive:profile:unlock', '解冻', 'archive', '已归档档案解冻'),
       (22, 'archive:mentor:append', '追加谈话记录', 'archive', '本班导师谈话追加'),
       (23, 'archive:mentor:feedback', '提交学生反馈', 'archive', '学生本人反馈一次'),
       (24, 'archive:change:read', '变更记录查看', 'archive', '按范围查看字段变更'),
       (25, 'import:student:execute', '学生导入', 'import', '本班学生导入'),
       (26, 'import:rank:execute', '成绩导入', 'import', '本班成绩导入'),
       (27, 'import:cert:verify', '证书认定', 'import', '本班证书认定'),
       (28, 'export:profile:pdf', '单人 PDF', 'export', '本人/本班单人档案 PDF'),
       (29, 'export:class:excel', '班级 Excel', 'export', '本班汇总 Excel'),
       (30, 'export:sensitive:approve', '敏感导出审批', 'export', '学院领导审批敏感列导出'),
       (31, 'audit:log:read', '审计查询', 'audit', '按范围查询审计日志'),
       (32, 'audit:login:read', '登录日志', 'audit', '登录成功/失败日志'),
       (33, 'grant:temp:request', '申请临时授权', 'grant', '学院领导发起临时授权'),
       (34, 'grant:temp:approve', '审批临时授权', 'grant', '管理员审批临时授权'),
       (35, 'stat:completion:read', '完成率统计', 'stat', '按范围查看完成率'),
       (36, 'system:task:manage', '定时任务与运维', 'system', '任务与系统参数')
ON DUPLICATE KEY UPDATE name        = VALUES(name),
                        module      = VALUES(module),
                        description = VALUES(description),
                        status      = 1;

-- 角色授权：与需求 7.2 矩阵一致；COU 的「本班」与 STU 的「本人」用 scope 表达。
INSERT INTO sys_role_permission (role, permission_code, scope)
VALUES ('ADMIN', 'auth:login', 'ALL'),
       ('ADMIN', 'auth:password:self', 'ALL'),
       ('ADMIN', 'org:college:manage', 'ALL'),
       ('ADMIN', 'org:major:manage', 'ALL'),
       ('ADMIN', 'org:class:manage', 'ALL'),
       ('ADMIN', 'org:class:read', 'ALL'),
       ('ADMIN', 'account:user:manage', 'ALL'),
       ('ADMIN', 'account:role:assign', 'ALL'),
       ('ADMIN', 'account:scope:manage', 'ALL'),
       ('ADMIN', 'config:field:manage', 'ALL'),
       ('ADMIN', 'config:field:read', 'ALL'),
       ('ADMIN', 'config:dict:manage', 'ALL'),
       ('ADMIN', 'config:dict:read', 'ALL'),
       ('ADMIN', 'config:param:manage', 'ALL'),
       ('ADMIN', 'archive:profile:unlock', 'ALL'),
       ('ADMIN', 'archive:change:read', 'ALL'),
       ('ADMIN', 'audit:log:read', 'ALL'),
       ('ADMIN', 'audit:login:read', 'ALL'),
       ('ADMIN', 'stat:completion:read', 'ALL'),
       ('ADMIN', 'system:task:manage', 'ALL'),

       ('COU', 'auth:login', 'ALL'),
       ('COU', 'auth:password:self', 'ALL'),
       ('COU', 'org:class:read', 'CLASS'),
       ('COU', 'config:field:read', 'ALL'),
       ('COU', 'config:dict:read', 'ALL'),
       ('COU', 'archive:profile:read', 'CLASS'),
       ('COU', 'archive:profile:write', 'CLASS'),
       ('COU', 'archive:profile:review', 'CLASS'),
       ('COU', 'archive:profile:archive', 'CLASS'),
       ('COU', 'archive:mentor:append', 'CLASS'),
       ('COU', 'archive:change:read', 'SELF'),
       ('COU', 'import:student:execute', 'CLASS'),
       ('COU', 'import:rank:execute', 'CLASS'),
       ('COU', 'import:cert:verify', 'CLASS'),
       ('COU', 'export:profile:pdf', 'CLASS'),
       ('COU', 'export:class:excel', 'CLASS'),
       ('COU', 'audit:log:read', 'SELF'),
       ('COU', 'stat:completion:read', 'CLASS'),

       ('STU', 'auth:login', 'ALL'),
       ('STU', 'auth:password:self', 'ALL'),
       ('STU', 'config:dict:read', 'ALL'),
       ('STU', 'archive:profile:read', 'SELF'),
       ('STU', 'archive:profile:write', 'SELF'),
       ('STU', 'archive:profile:submit', 'SELF'),
       ('STU', 'archive:profile:withdraw', 'SELF'),
       ('STU', 'archive:mentor:feedback', 'SELF'),
       ('STU', 'archive:change:read', 'SELF'),
       ('STU', 'export:profile:pdf', 'SELF'),
       ('STU', 'audit:log:read', 'SELF')
ON DUPLICATE KEY UPDATE scope = VALUES(scope);

SELECT role, COUNT(*) AS permissions FROM sys_role_permission GROUP BY role ORDER BY role;
SELECT COUNT(*) AS permission_total FROM sys_permission WHERE status = 1;
