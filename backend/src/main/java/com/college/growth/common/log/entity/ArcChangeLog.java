package com.college.growth.common.log.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 档案变更日志（物理表 {@code arc_change_log}）。
 *
 * <p>字段修改与状态流转都必须与业务同事务写入（BR-S11）：日志写失败要回滚业务，
 * 不接受「先改数据、后补日志」。</p>
 */
@Data
@TableName("arc_change_log")
public class ArcChangeLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long profileId;

    private Long studentId;

    private String sectionCode;

    /** FIELD_EDIT / STATUS_CHANGE / REVIEW / IMPORT / ARCHIVE。 */
    private String bizType;

    private String fieldCode;

    /** 变更前值，敏感字段只存脱敏值。 */
    private String oldValue;

    /** 变更后值，敏感字段只存脱敏值。 */
    private String newValue;

    private String fromStatus;

    private String toStatus;

    private Long operatorId;

    private String operatorRole;

    /** WEB / IMPORT / SYSTEM。 */
    private String source;

    private String reason;

    private LocalDateTime createdAt;
}
