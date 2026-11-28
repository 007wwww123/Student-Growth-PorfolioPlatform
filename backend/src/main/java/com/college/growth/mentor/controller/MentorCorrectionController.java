package com.college.growth.mentor.controller;

import com.college.growth.common.result.Result;
import com.college.growth.mentor.dto.MentorCorrectionDTO;
import com.college.growth.mentor.service.MentorSectionService;
import com.college.growth.mentor.vo.MentorNoteVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 归档后 MENTOR 更正入口（只追加，不覆盖原文）。 */
@RestController
@RequestMapping("/sections/mentor")
public class MentorCorrectionController {

    private final MentorSectionService mentorSectionService;

    public MentorCorrectionController(MentorSectionService mentorSectionService) {
        this.mentorSectionService = mentorSectionService;
    }

    @PostMapping("/{studentId}/corrections")
    public Result<MentorNoteVO> correct(@PathVariable Long studentId,
                                        @Valid @RequestBody MentorCorrectionDTO dto) {
        return Result.success(mentorSectionService.correct(studentId, dto));
    }
}
