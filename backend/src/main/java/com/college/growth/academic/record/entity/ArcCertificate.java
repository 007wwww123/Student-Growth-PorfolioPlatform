package com.college.growth.academic.record.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/** 证书技能（{@code arc_certificate}）：学生新增，COU 认定。 */
@Data
@TableName("arc_certificate")
public class ArcCertificate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long studentId;

    /** 外语 / 计算机 / 职业资格 / 技能 / 其他。 */
    private String certType;

    private String certName;

    private String score;

    private LocalDate obtainDate;

    private Long evidenceFileId;

    /** PENDING / PASSED / REJECTED。 */
    private String verifyStatus;

    private Long verifyUserId;

    private LocalDateTime verifyAt;

    private String verifyRemark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
