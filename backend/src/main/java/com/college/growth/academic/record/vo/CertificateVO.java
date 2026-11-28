package com.college.growth.academic.record.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/** 证书技能视图。 */
@Data
public class CertificateVO {

    private Long id;

    private Long studentId;

    private String certType;

    private String certName;

    private String score;

    private LocalDate obtainDate;

    private Long evidenceFileId;

    private String verifyStatus;

    private Long verifyUserId;

    private LocalDateTime verifyAt;

    private String verifyRemark;
}
