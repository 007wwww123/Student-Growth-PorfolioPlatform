package com.college.growth.grant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 审批单（{@code sys_approval}，脚本 23）。 */
@Data
@TableName("sys_approval")
public class SysApproval {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** TEMP_GRANT / EXPORT_SENSITIVE / APPEAL / UNFREEZE */
    private String approvalType;

    private Long applicantId;

    /** 关联学生（申诉/敏感导出）。 */
    private Long studentId;

    /** 关联业务 ID（如 change_id）。 */
    private Long bizId;

    /** 申请参数，JSON 字符串，如 {"hours":2}。 */
    private String targetJson;

    private String reason;

    /** PENDING / APPROVED / REJECTED / EXPIRED / REVOKED */
    private String status;

    private Long approverId;

    private String approverRemark;

    private LocalDateTime approvedAt;

    private LocalDateTime expireAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
