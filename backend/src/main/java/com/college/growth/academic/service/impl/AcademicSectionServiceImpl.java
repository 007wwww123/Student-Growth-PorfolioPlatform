package com.college.growth.academic.service.impl;

import com.college.growth.academic.dto.AcademicSectionSaveDTO;
import com.college.growth.academic.service.AcademicSectionService;
import com.college.growth.academic.vo.AcademicSectionVO;
import com.college.growth.common.enums.SectionCodeEnum;
import com.college.growth.common.profile.service.SectionFormService;
import com.college.growth.common.util.AcademicYearUtils;
import org.springframework.stereotype.Service;

@Service
public class AcademicSectionServiceImpl implements AcademicSectionService {

    private static final SectionCodeEnum SECTION = SectionCodeEnum.ACADEMIC;
    private final SectionFormService forms;

    public AcademicSectionServiceImpl(SectionFormService forms) {
        this.forms = forms;
    }

    @Override
    public AcademicSectionVO detail(Long studentId, String academicYear) {
        String year = AcademicYearUtils.resolve(academicYear);
        var view = forms.detail(studentId, year, SECTION);
        AcademicSectionVO result = new AcademicSectionVO();
        result.setStudentId(studentId);
        result.setAcademicYear(year);
        result.setSectionCode(SECTION.name());
        result.setSectionName(SECTION.getLabel());
        result.setStatus(view.status());
        result.setEditable(view.editable());
        result.setFields(view.fields());
        result.setFieldMeta(view.fieldMeta());
        return result;
    }

    @Override
    public void save(Long studentId, AcademicSectionSaveDTO dto) {
        forms.save(studentId, dto.getAcademicYear(), SECTION, dto.getFields());
    }

    @Override
    public void submit(Long studentId, String academicYear) {
        forms.submit(studentId, academicYear, SECTION);
    }

    @Override
    public void withdraw(Long studentId, String academicYear) {
        forms.withdraw(studentId, academicYear, SECTION);
    }
}
