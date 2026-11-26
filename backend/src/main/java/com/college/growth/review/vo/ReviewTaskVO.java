package com.college.growth.review.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 待审列表行：一个「学生 × 板块」的待审任务。
 *
 * <p>只带审核页列表需要的字段；打开详情仍走各板块自己的详情接口，
 * 避免审核模块复制一套字段渲染与权限逻辑。</p>
 */
@Data
public class ReviewTaskVO {

    /** 板块实例 ID。 */
    private Long profileId;

    private Long studentId;

    /** 学生姓名，取自基本信息板块 basic.name。 */
    private String studentName;

    /** 班级编码，取自 basic.classId。 */
    private String classId;

    /** 班级名称，取自 org_class.className。 */
    private String className;

    /** 板块编码，如 BASIC。 */
    private String sectionCode;

    /** 板块中文名，如 基本信息。 */
    private String sectionName;

    private String academicYear;

    /** 状态：默认只列 SUBMITTED。 */
    private String status;

    private String statusLabel;

    private BigDecimal completeness;

    private Integer submitCount;

    private LocalDateTime submittedAt;

    private Long reviewerId;

    private LocalDateTime reviewedAt;
}
