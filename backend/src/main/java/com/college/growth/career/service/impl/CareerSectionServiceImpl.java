package com.college.growth.career.service.impl;

import com.college.growth.career.dto.CareerSectionSaveDTO;
import com.college.growth.career.service.CareerSectionService;
import com.college.growth.career.vo.CareerSectionVO;
import com.college.growth.profile.ProfileStore;
import com.college.growth.common.enums.SectionCodeEnum;
import org.springframework.stereotype.Service;

@Service
public class CareerSectionServiceImpl implements CareerSectionService {

    private final ProfileStore store;

    public CareerSectionServiceImpl(ProfileStore store) {
        this.store = store;
    }

    @Override
    public CareerSectionVO detail(Long studentId, String academicYear) {
        return store.detail(SectionCodeEnum.CAREER, studentId, academicYear, CareerSectionVO.class);
    }

    @Override
    public void save(Long studentId, CareerSectionSaveDTO dto) {
        store.save(SectionCodeEnum.CAREER, studentId, dto.getAcademicYear(), dto.submittedFields());
    }

    @Override
    public void submit(Long studentId, String academicYear) {
        store.submit(SectionCodeEnum.CAREER, studentId, academicYear);
    }
}
