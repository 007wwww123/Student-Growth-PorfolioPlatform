package com.college.growth.interest.service.impl;

import com.college.growth.common.profile.service.SectionFormService;
import com.college.growth.common.util.AcademicYearUtils;
import com.college.growth.interest.dto.InterestSectionSaveDTO;
import com.college.growth.interest.service.InterestSectionService;
import com.college.growth.interest.vo.InterestSectionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import static com.college.growth.common.enums.SectionCodeEnum.INTEREST;

@Service
@RequiredArgsConstructor
public class InterestSectionServiceImpl implements InterestSectionService {
    private final SectionFormService forms;

    @Override
    public InterestSectionVO detail(Long studentId, String academicYear) {
        String year = AcademicYearUtils.resolve(academicYear);
        var form = forms.detail(studentId, year, INTEREST);
        var vo = new InterestSectionVO();
        vo.setStudentId(studentId);
        vo.setAcademicYear(year);
        vo.setSectionCode(INTEREST.name());
        vo.setSectionName(INTEREST.getLabel());
        vo.setStatus(form.status());
        vo.setFields(form.fields());
        vo.setFieldMeta(form.fieldMeta());
        vo.setEditable(form.editable());
        return vo;
    }

    @Override
    public void save(Long studentId, InterestSectionSaveDTO dto) {
        forms.save(studentId, dto.getAcademicYear(), INTEREST, dto.getFields());
    }

    @Override
    public void submit(Long studentId, String academicYear) {
        forms.submit(studentId, academicYear, INTEREST);
    }

    @Override
    public void withdraw(Long studentId, String academicYear) {
        forms.withdraw(studentId, academicYear, INTEREST);
    }
}
