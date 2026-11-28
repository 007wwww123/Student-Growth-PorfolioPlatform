package com.college.growth.life.vo;

import com.college.growth.basic.vo.FieldMetaVO;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * 板块详情视图：生活心理（LIFE）。
 *
 * <p>信封字段（学生、学年、板块、状态、计数）与 {@code fields} 分开：
 * 前端按 {@code fieldMeta} 渲染表单与权限位，{@code fields} 只提供值。
 * 结构与板块一至三保持一致，六个板块的详情接口共用同一套前端外壳。</p>
 */
@Data
public class LifeSectionVO {

    /** 学生 ID。 */
    private Long studentId;

    /** 学年。 */
    private String academicYear;

    /** 板块编码，固定为 LIFE。 */
    private String sectionCode;

    /** 板块名称。 */
    private String sectionName;

    /** 板块状态，取值见 ProfileStatusEnum。 */
    private String status;

    /** 字段值集合，key 为字段短名（如 {@code stressScore}）；当前角色不可见的字段不返回。 */
    private Map<String, Object> fields;

    /** 字段渲染与权限位，按 sort_no 排序，仅含当前角色可见的字段。 */
    private List<FieldMetaVO> fieldMeta;

    /** 是否可编辑：由来源标记、角色、状态三层同时判定（BR-F01）。 */
    private Boolean editable;

    /** 完成度百分比，按必填且启用字段现算。 */
    private BigDecimal completeness;

    /** 提交次数。 */
    private Integer submitCount;

    /** 撤回次数，全生命周期上限 1（BR-S06）。 */
    private Integer withdrawCount;

    /** 最近一次提交时间。 */
    private LocalDateTime submittedAt;

    /** 最近一次退回理由（BR-S04：理由对学生可见），仅在状态为「已退回」时返回。 */
    private String reviewReason;

    /** 最近一次退回时间，与 {@link #reviewReason} 同时返回。 */
    private LocalDateTime reviewedAt;
}
