package com.college.growth.mentor.vo;

import com.college.growth.basic.vo.FieldMetaVO;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * 板块详情视图：导师寄语（MENTOR）。
 *
 * <p>信封字段与其余五个板块完全一致（学生、学年、板块、状态、计数、完成度、
 * 审核留痕），前端公共外壳无需为本板块特判；差别只在 {@code notes}：</p>
 *
 * <p>需求 4.7 明确本板块「没有主表字段值，全部内容在子表中」，所以 {@code fields}
 * 恒为空 Map，字段定义仍通过 {@code fieldMeta} 下发（辅导员追加表单与学生反馈框
 * 按同一套控件类型与权限位渲染），真正的谈话内容放在 {@code notes} 时间线里。</p>
 */
@Data
public class MentorSectionVO {

    /** 学生 ID。 */
    private Long studentId;

    /** 学年。 */
    private String academicYear;

    /** 板块编码，固定为 MENTOR。 */
    private String sectionCode;

    /** 板块名称。 */
    private String sectionName;

    /** 板块状态，取值见 ProfileStatusEnum。 */
    private String status;

    /** 主表字段值：恒为空（本板块内容全部在子表），保留字段以维持六板块信封一致。 */
    private Map<String, Object> fields;

    /** 字段渲染与权限位，按 sort_no 排序，仅含当前角色可见的字段。 */
    private List<FieldMetaVO> fieldMeta;

    /** 是否可编辑：由来源标记、角色、状态三层同时判定（BR-F01）。 */
    private Boolean editable;

    /** 完成度百分比：由 MENTOR 扩展点按谈话记录计算。 */
    private BigDecimal completeness;

    /** 提交次数。 */
    private Integer submitCount;

    /** 撤回次数，全生命周期上限 1（BR-S06）。 */
    private Integer withdrawCount;

    /** 最近一次提交时间。 */
    private LocalDateTime submittedAt;

    /** 最近一次退回理由（BR-S04），仅在状态为「已退回」时返回。 */
    private String reviewReason;

    /** 最近一次退回时间，与 {@link #reviewReason} 同时返回。 */
    private LocalDateTime reviewedAt;

    /** 谈话记录时间线，按 talkDate 倒序；字段按当前角色裁剪。 */
    private List<MentorNoteVO> notes;

    /** 当前角色是否可以追加谈话（仅该班辅导员、且板块未归档）。 */
    private Boolean canAppend;
}
