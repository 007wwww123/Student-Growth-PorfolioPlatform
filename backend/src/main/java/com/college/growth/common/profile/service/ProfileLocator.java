package com.college.growth.common.profile.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.profile.entity.ArcProfile;
import com.college.growth.common.profile.mapper.ArcProfileMapper;
import com.college.growth.common.result.ResultCode;
import org.springframework.stereotype.Service;

/**
 * 板块实例定位：按「学生 + 板块 + 学年」取唯一一条记录。
 *
 * <p>三个条件是 {@code uk_profile_student_section_year} 的全部组成，缺一个就可能读到别的板块；
 * 因此所有需要定位实例的地方都必须走本类，不要各自拼条件。</p>
 */
@Service
public class ProfileLocator {

    private final ArcProfileMapper profileMapper;

    public ProfileLocator(ArcProfileMapper profileMapper) {
        this.profileMapper = profileMapper;
    }

    /** 查找；不存在返回 null。 */
    public ArcProfile find(Long studentId, String sectionCode, String academicYear) {
        return profileMapper.selectOne(Wrappers.<ArcProfile>lambdaQuery()
                .eq(ArcProfile::getStudentId, studentId)
                .eq(ArcProfile::getSectionCode, sectionCode)
                .eq(ArcProfile::getAcademicYear, academicYear)
                .last("limit 1"));
    }

    /** 查找；不存在抛 {@code EC-40002}，提示已建档状态。 */
    public ArcProfile require(Long studentId, String sectionCode, String academicYear, String sectionLabel) {
        ArcProfile profile = find(studentId, sectionCode, academicYear);
        if (profile == null) {
            throw new BusinessException(ResultCode.PARAM_MISSING,
                    "该学生本学年「" + sectionLabel + "」板块尚未建档");
        }
        return profile;
    }
}
