package com.college.growth.academic.service.impl;

import com.college.growth.academic.dto.AcademicSectionSaveDTO;
import com.college.growth.academic.service.AcademicSectionService;
import com.college.growth.academic.vo.AcademicSectionVO;
import com.college.growth.profile.ProfileStore;
import com.college.growth.common.enums.SectionCodeEnum;
import org.springframework.stereotype.Service;

@Service
public class AcademicSectionServiceImpl implements AcademicSectionService {

    private final ProfileStore store;

    public AcademicSectionServiceImpl(ProfileStore store) {
        this.store = store;
    }

    @Override
    public AcademicSectionVO detail(Long studentId, String academicYear) {
        return store.detail(SectionCodeEnum.ACADEMIC, studentId, academicYear, AcademicSectionVO.class);
    }

    @Override
    public void save(Long studentId, AcademicSectionSaveDTO dto) {
        store.save(SectionCodeEnum.ACADEMIC, studentId, dto.getAcademicYear(), dto.submittedFields());
    }

    @Override
    public void submit(Long studentId, String academicYear) {
        store.submit(SectionCodeEnum.ACADEMIC, studentId, academicYear);
    }
}
