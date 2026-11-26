package com.college.growth.review.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.college.growth.common.enums.SectionCodeEnum;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.profile.entity.ArcProfile;
import com.college.growth.common.profile.mapper.ArcProfileMapper;
import com.college.growth.common.result.ResultCode;
import com.college.growth.common.util.AcademicYearUtils;
import com.college.growth.review.dto.ReviewDecisionDTO;
import com.college.growth.review.service.ProfileWorkflowService;
import com.college.growth.review.service.ReviewDecisionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 审核结论实现：定位板块实例，其余交给公共状态机。
 *
 * <p>板块编码同时接受 {@code basic}（接口路径）与 {@code BASIC}（数据库），
 * 由 {@link SectionCodeEnum} 统一收敛，避免调用方猜测大小写。</p>
 */
@Service
public class ReviewDecisionServiceImpl implements ReviewDecisionService {

    private final ArcProfileMapper profileMapper;

    private final ProfileWorkflowService workflowService;

    public ReviewDecisionServiceImpl(ArcProfileMapper profileMapper,
                                      ProfileWorkflowService workflowService) {
        this.profileMapper = profileMapper;
        this.workflowService = workflowService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(String sectionCode, Long studentId, ReviewDecisionDTO dto) {
        ArcProfile profile = requireProfile(sectionCode, studentId, dto);
        workflowService.approve(profile, dto == null ? null : dto.getComment());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(String sectionCode, Long studentId, ReviewDecisionDTO dto) {
        ArcProfile profile = requireProfile(sectionCode, studentId, dto);
        workflowService.reject(profile, dto == null ? null : dto.getReason());
    }

    private ArcProfile requireProfile(String sectionCode, Long studentId, ReviewDecisionDTO dto) {
        SectionCodeEnum section = SectionCodeEnum.tryFromCode(sectionCode);
        if (section == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "未知板块编码：" + sectionCode);
        }
        if (studentId == null || studentId <= 0) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "缺少学生 ID");
        }
        String year = AcademicYearUtils.resolve(dto == null ? null : dto.getAcademicYear());
        ArcProfile profile = profileMapper.selectOne(Wrappers.<ArcProfile>lambdaQuery()
                .eq(ArcProfile::getStudentId, studentId)
                .eq(ArcProfile::getSectionCode, section.name())
                .eq(ArcProfile::getAcademicYear, year)
                .last("limit 1"));
        if (profile == null) {
            throw new BusinessException(ResultCode.PARAM_MISSING,
                    "该学生本学年「" + section.getLabel() + "」板块尚未建档");
        }
        return profile;
    }
}
