package com.college.growth.academic.record.vo;

import java.math.BigDecimal;
import lombok.Data;

/** 成绩轨迹行。 */
@Data
public class RankVO {

    private Long id;

    private String academicYear;

    private Integer classRank;

    private Integer majorRank;

    private BigDecimal gpa;

    private String source;
}
