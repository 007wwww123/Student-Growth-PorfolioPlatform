package com.college.growth.academic.record.vo;

import java.util.List;
import lombok.Data;

/** 学业成果聚合：成绩轨迹 + 证书 + 竞赛。 */
@Data
public class AcademicRecordVO {

    private Long studentId;

    private List<RankVO> ranks;

    private List<CertificateVO> certificates;

    private List<EventVO> events;

    /** 已认定通过的证书/竞赛数量（PENDING、REJECTED 不计）。 */
    private int passedCertificates;

    private int passedEvents;
}
