package com.college.growth.basic.service.impl;

import com.college.growth.basic.dto.BasicSectionSaveDTO;
import com.college.growth.basic.service.BasicSectionService;
import com.college.growth.basic.vo.BasicSectionVO;
import com.college.growth.profile.ProfileStore;
import com.college.growth.common.enums.SectionCodeEnum;
import org.springframework.stereotype.Service;

@Service
public class BasicSectionServiceImpl implements BasicSectionService {

    private final ProfileStore store;

    public BasicSectionServiceImpl(ProfileStore store) {
        this.store = store;
    }

    @Override
    public BasicSectionVO detail(Long studentId, String academicYear) {
        return store.detail(SectionCodeEnum.BASIC, studentId, academicYear, BasicSectionVO.class);
    }

    @Override
    public void save(Long studentId, BasicSectionSaveDTO dto) {
        store.save(SectionCodeEnum.BASIC, studentId, dto.getAcademicYear(), dto.submittedFields());
    }

    @Override
    public void submit(Long studentId, String academicYear) {
        store.submit(SectionCodeEnum.BASIC, studentId, academicYear);
    }
}
