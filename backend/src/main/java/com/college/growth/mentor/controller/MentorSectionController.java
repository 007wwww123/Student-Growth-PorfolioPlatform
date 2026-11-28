package com.college.growth.mentor.controller;

import com.college.growth.common.result.Result;
import com.college.growth.mentor.common.MentorConstants;
import com.college.growth.mentor.dto.MentorFeedbackDTO;
import com.college.growth.mentor.dto.MentorNoteCreateDTO;
import com.college.growth.mentor.dto.MentorSectionSaveDTO;
import com.college.growth.mentor.service.MentorSectionService;
import com.college.growth.mentor.vo.MentorNoteVO;
import com.college.growth.mentor.vo.MentorSectionVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 板块六 导师寄语（MENTOR）接口入口。
 *
 * <p>前四条路径与其余五个板块完全一致（详情 / 保存 / 提交 / 撤回），
 * 后两条是需求 4.7 的子表能力：辅导员追加谈话、学生反馈一次。</p>
 *
 * <p>权限点：{@code archive:mentor:append} = 本班辅导员；
 * {@code archive:mentor:feedback} = 学生本人。</p>
 */
@RestController
@RequestMapping(MentorConstants.API_BASE_PATH)
public class MentorSectionController {

    private final MentorSectionService mentorSectionService;

    public MentorSectionController(MentorSectionService mentorSectionService) {
        this.mentorSectionService = mentorSectionService;
    }

    /** 查询详情：统一信封 + 按 talkDate 倒序的谈话时间线，字段按角色裁剪。 */
    @GetMapping("/{studentId}")
    public Result<MentorSectionVO> detail(@PathVariable Long studentId,
                                          @RequestParam(required = false) String academicYear) {
        return Result.success(mentorSectionService.detail(studentId, academicYear));
    }

    /**
     * 保存草稿：六板块端点一致性保留。
     *
     * <p>本板块没有主表字段值，只接受空 {@code fields}；非空返回 {@code EC-40003}
     * 并提示改用追加接口。</p>
     */
    @PutMapping("/{studentId}")
    public Result<Void> save(@PathVariable Long studentId,
                             @Valid @RequestBody MentorSectionSaveDTO dto) {
        mentorSectionService.save(studentId, dto);
        return Result.success();
    }

    /** 提交板块：至少一条完整谈话记录后由 DRAFT / RETURNED 转 SUBMITTED。 */
    @PostMapping("/{studentId}/submit")
    public Result<Void> submit(@PathVariable Long studentId,
                               @RequestParam String academicYear) {
        mentorSectionService.submit(studentId, academicYear);
        return Result.success();
    }

    /** 撤回提交（BR-S06：仅本人、全生命周期一次）。 */
    @PostMapping("/{studentId}/withdraw")
    public Result<Void> withdraw(@PathVariable Long studentId,
                                 @RequestParam String academicYear) {
        mentorSectionService.withdraw(studentId, academicYear);
        return Result.success();
    }

    /** 追加一条谈话记录（仅本班辅导员；只追加，不修改不删除）。 */
    @PostMapping("/{studentId}/notes")
    public Result<MentorNoteVO> append(@PathVariable Long studentId,
                                       @Valid @RequestBody MentorNoteCreateDTO dto) {
        return Result.success(mentorSectionService.append(studentId, dto));
    }

    /** 对一条谈话提交学生反馈（仅本人，每条仅一次，提交后锁定）。 */
    @PostMapping("/{studentId}/notes/{noteId}/feedback")
    public Result<MentorNoteVO> feedback(@PathVariable Long studentId,
                                         @PathVariable Long noteId,
                                         @Valid @RequestBody MentorFeedbackDTO dto) {
        return Result.success(mentorSectionService.feedback(studentId, noteId, dto));
    }
}
