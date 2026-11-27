package com.college.growth.completion.vo;

import java.util.List;
import lombok.Data;

/**
 * 某学生某学年的完成标记（BR-S10）。
 *
 * <p>审核详情页用它回答两个问题：「这名学生六板块走到哪一步」「能不能毕业归档」。
 * 标记本身以 {@code arc_profile.completed} 为真源，本 VO 只是把它连同逐板块状态一起摊开，
 * 前端不自行统计，避免两边口径漂移。</p>
 */
@Data
public class CompletionStatusVO {

    private Long studentId;

    /** 学号，取自学生主数据。 */
    private String studentNo;

    /** 姓名，取自学生主数据。 */
    private String studentName;

    /** 班级编码，取自学生主数据。 */
    private String classCode;

    /** 班级名称，取自 org_class；未分班时为空。 */
    private String className;

    private String academicYear;

    /** 六板块是否全部完成：库里的派生标记，不是前端计算的。 */
    private boolean completed;

    /** 已建档板块数（0–6）。 */
    private int createdSectionCount;

    /** 已完成板块数（APPROVED / LOCKED，0–6）。 */
    private int completedSectionCount;

    /** 未完成板块中文名，按板块顺序；全部完成时为空数组。 */
    private List<String> pendingSections;

    /** 逐板块状态，固定六项，缺块也会出现（状态为「未建档」）。 */
    private List<CompletionSectionVO> sections;
}
