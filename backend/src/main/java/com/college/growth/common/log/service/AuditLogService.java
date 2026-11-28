package com.college.growth.common.log.service;

import com.college.growth.common.constant.CommonConstants;
import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.context.UserContext;
import com.college.growth.common.log.entity.SysAuditLog;
import com.college.growth.common.log.mapper.SysAuditLogMapper;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

/**
 * 系统审计日志写入入口（物理表 {@code sys_audit_log}）。
 *
 * <p>状态流转、审核、敏感字段查看、待确认区确认这些「能力型」操作都写审计，
 * 与 {@link ChangeLogService} 的档案变更日志同时落库（S-1-12）。
 * 与业务同事务，日志写失败即回滚。</p>
 */
@Service
public class AuditLogService {

    /** 业务类型：状态流转。 */
    public static final String BIZ_STATUS_CHANGE = "STATUS_CHANGE";

    /** 业务类型：板块审核。 */
    public static final String BIZ_REVIEW = "REVIEW";

    /** 业务类型：敏感字段查看。 */
    public static final String BIZ_SENSITIVE_VIEW = "SENSITIVE_VIEW";

    /** 业务类型：待确认字段处理。 */
    public static final String BIZ_FIELD_CONFIRM = "FIELD_CONFIRM";

    /** 业务类型：外部系统数据同步。 */
    public static final String BIZ_IMPORT = "IMPORT";

    /** 业务类型：毕业归档（S-1-11）。 */
    public static final String BIZ_ARCHIVE = "ARCHIVE";

    /** 业务类型：完成标记重算（BR-S10 派生数据维护）。 */
    public static final String BIZ_COMPLETION = "COMPLETION";

    /** 业务类型：字段编辑权限配置（管理员模式）。 */
    public static final String BIZ_PERMISSION_CONFIG = "PERMISSION_CONFIG";

    /** 业务类型：字段定义配置（新增/修改/启停/必填开关）。 */
    public static final String BIZ_FIELD_CONFIG = "FIELD_CONFIG";

    /** 业务类型：学业成果（成绩/证书/竞赛）。 */
    public static final String BIZ_ACADEMIC = "ACADEMIC";

    /** 业务类型：导出（F-12）。 */
    public static final String BIZ_EXPORT = "EXPORT";

    /** 业务类型：安全拒绝（越权尝试）。 */
    public static final String BIZ_SECURITY = "SECURITY";

    /** 业务类型：审批与临时授权。 */
    public static final String BIZ_GRANT = "GRANT";

    /** 业务类型：组织架构变更（学院/专业/班级）。 */
    public static final String BIZ_ORG = "ORG";

    /** 业务类型：账号生命周期。 */
    public static final String BIZ_ACCOUNT = "ACCOUNT";

    /** 动作：新增。 */
    public static final String ACTION_CREATE = "CREATE";

    /** 动作：修改。 */
    public static final String ACTION_UPDATE = "UPDATE";

    /** 动作：启用。 */
    public static final String ACTION_ENABLE = "ENABLE";

    /** 动作：停用。 */
    public static final String ACTION_DISABLE = "DISABLE";

    /** 动作：绑定/分配。 */
    public static final String ACTION_ASSIGN = "ASSIGN";

    /** 动作：分配角色。 */
    public static final String ACTION_ROLE_ASSIGN = "ROLE_ASSIGN";

    /** 动作：批量开通账号。 */
    public static final String ACTION_BATCH_OPEN = "BATCH_OPEN";

    /** 动作：导入执行。 */
    public static final String ACTION_IMPORT = "IMPORT";

    /** 动作：导出执行。 */
    public static final String ACTION_EXPORT = "EXPORT";

    /** 动作：文件下载。 */
    public static final String ACTION_DOWNLOAD = "DOWNLOAD";

    /** 动作：越权拒绝。 */
    public static final String ACTION_DENY = "DENY";

    /** 动作：申请。 */
    public static final String ACTION_APPLY = "APPLY";

    /** 动作：审批通过。 */
    public static final String ACTION_APPROVE = "APPROVE";

    /** 动作：审批拒绝。 */
    public static final String ACTION_REJECT = "REJECT";

    /** 动作：撤销。 */
    public static final String ACTION_REVOKE = "REVOKE";

    /** 动作：到期。 */
    public static final String ACTION_EXPIRE = "EXPIRE";

    /** 动作：从学生主数据重新同步。 */
    public static final String ACTION_SYNC = "SYNC";

    /** 动作：恢复字段默认权限。 */
    public static final String ACTION_RESET = "RESET";

    /** 目标类型：板块实例。 */
    public static final String TARGET_PROFILE = "PROFILE";

    /** 目标类型：字段定义（权限配置的作用对象）。 */
    public static final String TARGET_FIELD_DEF = "FIELD_DEF";

    /** 目标类型：组织节点（学院/专业/班级）。 */
    public static final String TARGET_ORG = "ORG";

    /** 目标类型：账号。 */
    public static final String TARGET_USER = "USER";

    /** 目标类型：学生主数据。 */
    public static final String TARGET_STUDENT = "STUDENT";

    /** 目标类型：导出任务。 */
    public static final String TARGET_EXPORT = "EXPORT_TASK";

    /** 目标类型：接口路径（安全拒绝）。 */
    public static final String TARGET_API = "API";

    /** 目标类型：审批单。 */
    public static final String TARGET_APPROVAL = "APPROVAL";

    private final SysAuditLogMapper auditLogMapper;

    public AuditLogService(SysAuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    /**
     * 写一条审计日志。
     *
     * @param bizType    业务类型
     * @param targetType 目标类型，PROFILE / FIELD
     * @param targetId   目标 ID
     * @param studentId  关联学生
     * @param sectionCode 板块编码
     * @param fieldCode  字段编码，非字段级操作传 null
     * @param action     动作标识
     * @param detail     补充说明，禁止写敏感明文
     */
    public void record(String bizType, String targetType, Long targetId, Long studentId,
                       String sectionCode, String fieldCode, String action, String detail) {
        CurrentUser user = UserContext.get();
        SysAuditLog auditLog = new SysAuditLog();
        auditLog.setBizType(bizType);
        auditLog.setTargetType(targetType);
        auditLog.setTargetId(targetId);
        auditLog.setStudentId(studentId);
        auditLog.setSectionCode(sectionCode);
        auditLog.setFieldCode(fieldCode);
        auditLog.setAction(action);
        auditLog.setDetail(detail);
        auditLog.setOperatorId(user.userId());
        auditLog.setOperatorRole(user.role().name());
        auditLog.setTraceId(MDC.get(CommonConstants.TRACE_ID));
        auditLogMapper.insert(auditLog);
    }
}
