package com.college.growth.interest.service.impl;

import com.college.growth.interest.dto.InterestSectionSaveDTO;
import com.college.growth.interest.service.InterestSectionService;
import com.college.growth.interest.vo.InterestSectionVO;
import com.college.growth.profile.ProfileStore;
import com.college.growth.common.enums.SectionCodeEnum;
import org.springframework.stereotype.Service;

@Service
public class InterestSectionServiceImpl implements InterestSectionService {

    private final ProfileStore store;

    public InterestSectionServiceImpl(ProfileStore store) {
        this.store = store;
    }

    @Override
    public InterestSectionVO detail(Long studentId, String academicYear) {
        return store.detail(SectionCodeEnum.INTEREST, studentId, academicYear, InterestSectionVO.class);
    }

    @Override
    public void save(Long studentId, InterestSectionSaveDTO dto) {
        store.save(SectionCodeEnum.INTEREST, studentId, dto.getAcademicYear(), dto.submittedFields());
    }

    @Override
    public void submit(Long studentId, String academicYear) {
        store.submit(SectionCodeEnum.INTEREST, studentId, academicYear);
    }
}
