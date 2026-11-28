package com.college.growth.ledger.mapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 台账明细查询结果（UNION ALL 行集的原始映射）。
 *
 * <p>只承载 SQL 里能直接取到的列；板块中文名与状态中文名由 Service 按枚举补齐，
 * 避免在 SQL 里再维护一份「BASIC → 基本信息」的映射。</p>
 */
@Data
public class LedgerTaskRow {

    /** 板块实例 ID；未建档行为空。 */
    private Long profileId;

    private Long studentId;

    private String studentNo;

    private String studentName;

    private String classCode;

    private String className;

    /** 板块编码，大写；未建档行为空。 */
    private String sectionCode;

    /** 五状态之一，或未建档行的 UNARCHIVED。 */
    private String status;

    private BigDecimal completeness;

    private Integer submitCount;

    private LocalDateTime submittedAt;

    private LocalDateTime reviewedAt;

    private Long reviewerId;

    /** 审核人展示名，来自 sys_user.display_name。 */
    private String reviewerName;
}
