package com.college.growth.mentor.service;

import com.college.growth.mentor.dto.MentorFeedbackDTO;
import com.college.growth.mentor.dto.MentorNoteCreateDTO;
import com.college.growth.mentor.dto.MentorSectionSaveDTO;
import com.college.growth.mentor.vo.MentorNoteVO;
import com.college.growth.mentor.vo.MentorSectionVO;

/**
 * 板块六 导师寄语（MENTOR）业务接口。
 *
 * <p>前四个方法与其余五个板块一致（详情 / 保存 / 提交 / 撤回），保证公共外壳、
 * 审核列表、权限配置都按同一套端点建模；后两个是需求 4.7 的子表能力
 * （辅导员追加谈话、学生反馈一次）。其他模块只能通过本接口访问 MENTOR 数据。</p>
 */
public interface MentorSectionService {

    /**
     * 查询指定学生某学年的板块详情：统一信封 + 按 talkDate 倒序的谈话时间线。
     *
     * @param studentId    学生 ID
     * @param academicYear 学年，如 2025-2026-1；为空时取当前学年
     */
    MentorSectionVO detail(Long studentId, String academicYear);

    /**
     * 保存草稿（六板块端点一致性保留）。
     *
     * <p>MENTOR 没有主表字段值，因此只接受空 {@code fields}；非空时提示改用追加接口。</p>
     */
    void save(Long studentId, MentorSectionSaveDTO dto);

    /** 提交板块：至少一条完整谈话记录后，由 DRAFT / RETURNED 转 SUBMITTED。 */
    void submit(Long studentId, String academicYear);

    /** 撤回提交（BR-S06：仅本人、全生命周期一次）。 */
    void withdraw(Long studentId, String academicYear);

    /**
     * 追加一条谈话记录（仅本班辅导员，{@code archive:mentor:append}）。
     *
     * <p>只追加，不提供修改/删除入口（BR-S07）。</p>
     */
    MentorNoteVO append(Long studentId, MentorNoteCreateDTO dto);

    /**
     * 对一条谈话提交学生反馈（仅本人、每条一次，{@code archive:mentor:feedback}）。
     */
    MentorNoteVO feedback(Long studentId, Long noteId, MentorFeedbackDTO dto);
}
