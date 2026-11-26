package com.college.growth.basic.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * 板块详情视图：基本信息（BASIC）。
 *
 * <p>信封字段（学生、学年、板块、状态）与 {@code fields} 分开：
 * 前端按 {@code fieldMeta} 渲染表单与权限位，{@code fields} 只提供值，
 * 不再由前端硬编码「哪些字段只读」——那是服务端算出来的结论。</p>
 */
@Data
public class BasicSectionVO {

    /** 学生 ID。 */
    private Long studentId;

    /** 学年。 */
    private String academicYear;

    /** 板块编码，固定为 BASIC。 */
    private String sectionCode;

    /** 板块名称。 */
    private String sectionName;

    /** 板块状态，取值见 ProfileStatusEnum。 */
    private String status;

    /** 字段值，键为字段短名（如 studentNo）；不可见字段不返回，敏感字段默认脱敏。 */
    private Map<String, Object> fields;

    /** 字段渲染与权限位，按 sort_no 排序，仅含当前角色可见的字段。 */
    private List<FieldMetaVO> fieldMeta;

    /** 是否存在任一可编辑字段：由来源、角色、状态三层同时判定（BR-F01）。 */
    private Boolean editable;

    /** 完成度百分比。 */
    private BigDecimal completeness;

    /** 提交次数。 */
    private Integer submitCount;

    /** 撤回次数。 */
    private Integer withdrawCount;

    /** 最近一次提交时间。 */
    private LocalDateTime submittedAt;

    /**
     * 最近一次退回理由（BR-S04：理由对学生可见）。
     *
     * <p>仅在状态为「已退回」时返回；学生重新提交后该值置空，避免上一次的退回意见
     * 被当成当前结论。</p>
     */
    private String reviewReason;

    /** 最近一次退回时间，与 {@link #reviewReason} 同时返回。 */
    private LocalDateTime reviewedAt;
}
