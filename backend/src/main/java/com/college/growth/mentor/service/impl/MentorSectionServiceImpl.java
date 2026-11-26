package com.college.growth.mentor.service.impl;

import com.college.growth.mentor.dto.MentorSectionSaveDTO;
import com.college.growth.mentor.service.MentorSectionService;
import com.college.growth.mentor.vo.MentorSectionVO;
import com.college.growth.profile.ProfileStore;
import com.college.growth.common.enums.SectionCodeEnum;
import org.springframework.stereotype.Service;

@Service
public class MentorSectionServiceImpl implements MentorSectionService {

    private final ProfileStore store;

    public MentorSectionServiceImpl(ProfileStore store) {
        this.store = store;
    }

    @Override
    public MentorSectionVO detail(Long studentId, String academicYear) {
        return store.detail(SectionCodeEnum.MENTOR, studentId, academicYear, MentorSectionVO.class);
    }

    @Override
    public void save(Long studentId, MentorSectionSaveDTO dto) {
        store.save(SectionCodeEnum.MENTOR, studentId, dto.getAcademicYear(), dto.submittedFields());
    }

    @Override
    public void submit(Long studentId, String academicYear) {
        store.submit(SectionCodeEnum.MENTOR, studentId, academicYear);
    }
}
