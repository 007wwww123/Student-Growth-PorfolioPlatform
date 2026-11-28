package com.college.growth.mentor.service;

import com.college.growth.mentor.dto.MentorCorrectionDTO;
import com.college.growth.mentor.dto.MentorFeedbackDTO;
import com.college.growth.mentor.dto.MentorNoteCreateDTO;
import com.college.growth.mentor.dto.MentorSectionSaveDTO;
import com.college.growth.mentor.vo.MentorNoteVO;
import com.college.growth.mentor.vo.MentorSectionVO;

/** 板块六：导师寄语（MENTOR）业务接口。 */
public interface MentorSectionService {

    MentorSectionVO detail(Long studentId, String academicYear);

    void save(Long studentId, MentorSectionSaveDTO dto);

    void submit(Long studentId, String academicYear);

    void withdraw(Long studentId, String academicYear);

    MentorNoteVO append(Long studentId, MentorNoteCreateDTO dto);

    /** 归档后追加更正记录：不覆盖原文，只新增一条带原因的更正（BR-S07）。 */
    MentorNoteVO correct(Long studentId, MentorCorrectionDTO dto);

    MentorNoteVO feedback(Long studentId, Long noteId, MentorFeedbackDTO dto);
}
