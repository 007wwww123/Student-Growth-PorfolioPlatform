package com.college.growth.common.log.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 系统审计日志（物理表 {@code sys_audit_log}）。
 *
 * <p>与 {@link ArcChangeLog} 的分工：变更日志记「档案内容发生了什么」，
 * 审计日志记「谁调用了什么敏感能力」，用于安全审计与合规追溯（S-1-12 要求两条都写）。
 * 审计日志不存业务明文，只存动作、对象与原因。</p>
 */
@Data
@TableName("sys_audit_log")
public class SysAuditLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 业务类型：STATUS_CHANGE / REVIEW / SENSITIVE_VIEW / FIELD_CONFIRM。 */
    private String bizType;

    /** 目标对象类型：PROFILE / FIELD。 */
    private String targetType;

    /** 目标对象 ID：板块实例 ID 或字段记录 ID。 */
    private Long targetId;

    /** 关联学生 ID，便于按学生追溯。 */
    private Long studentId;

    /** 板块编码，如 BASIC。 */
    private String sectionCode;

    /** 字段编码，字段级操作时填写。 */
    private String fieldCode;

    /** 动作：SUBMIT / WITHDRAW / APPROVE / REJECT / REVEAL / CONFIRM / DISCARD。 */
    private String action;

    /** 补充说明，禁止写入敏感明文。 */
    private String detail;

    private Long operatorId;

    private String operatorRole;

    /** 链路追踪号，与响应头 X-Trace-Id 一致。 */
    private String traceId;

    private LocalDateTime createdAt;
}
