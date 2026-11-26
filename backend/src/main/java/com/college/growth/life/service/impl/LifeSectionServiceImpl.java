package com.college.growth.life.service.impl;

import com.college.growth.life.dto.LifeSectionSaveDTO;
import com.college.growth.life.service.LifeSectionService;
import com.college.growth.life.vo.LifeSectionVO;
import com.college.growth.profile.ProfileStore;
import com.college.growth.common.enums.SectionCodeEnum;
import org.springframework.stereotype.Service;

@Service
public class LifeSectionServiceImpl implements LifeSectionService {

    private final ProfileStore store;

    public LifeSectionServiceImpl(ProfileStore store) {
        this.store = store;
    }

    @Override
    public LifeSectionVO detail(Long studentId, String academicYear) {
        return store.detail(SectionCodeEnum.LIFE, studentId, academicYear, LifeSectionVO.class);
    }

    @Override
    public void save(Long studentId, LifeSectionSaveDTO dto) {
        store.save(SectionCodeEnum.LIFE, studentId, dto.getAcademicYear(), dto.submittedFields());
    }

    @Override
    public void submit(Long studentId, String academicYear) {
        store.submit(SectionCodeEnum.LIFE, studentId, academicYear);
    }
}
