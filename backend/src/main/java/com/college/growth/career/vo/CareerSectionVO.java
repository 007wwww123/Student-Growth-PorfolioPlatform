package com.college.growth.career.vo;

import com.college.growth.basic.vo.FieldMetaVO;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * 板块详情视图：职业规划（CAREER）。
 *
 * <p>信封字段与 {@code fields} 分开，结构与其余五个板块一致；
 * 前端 {@code SectionShell} 按 {@code fieldMeta} 渲染，不需要为本板块特判。</p>
 */
@Data
public class CareerSectionVO {

    /** 学生 ID。 */
    private Long studentId;

    /** 学年。 */
    private String academicYear;

    /** 板块编码，固定为 CAREER。 */
    private String sectionCode;

    /** 板块名称。 */
    private String sectionName;

    /** 板块状态，取值见 ProfileStatusEnum。 */
    private String status;

    /** 字段值集合，key 为字段短名（如 {@code intention}）。 */
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

    /** 最近一次退回理由（BR-S04），仅在「已退回」状态返回。 */
    private String reviewReason;

    /** 最近一次退回时间，与 {@link #reviewReason} 同时返回。 */
    private LocalDateTime reviewedAt;
}
