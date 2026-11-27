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

    /** 动作：从学生主数据重新同步。 */
    public static final String ACTION_SYNC = "SYNC";

    /** 目标类型：板块实例。 */
    public static final String TARGET_PROFILE = "PROFILE";

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
