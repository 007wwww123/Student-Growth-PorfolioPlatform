package com.college.growth.mentor.service.impl;

import com.college.growth.mentor.dto.MentorSectionSaveDTO;
import com.college.growth.mentor.mapper.MentorProfileMapper;
import com.college.growth.mentor.service.MentorSectionService;
import com.college.growth.mentor.vo.MentorSectionVO;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 板块六 导师寄语（MENTOR）业务实现。
 *
 * <p>骨架阶段仅保留方法签名与事务边界，方法体统一抛出"尚未实现"，
 * 便于前端先行联调路由与响应结构。</p>
 */
@Slf4j
@Service
public class MentorSectionServiceImpl implements MentorSectionService {

    private final MentorProfileMapper mentorProfileMapper;

    public MentorSectionServiceImpl(MentorProfileMapper mentorProfileMapper) {
        this.mentorProfileMapper = mentorProfileMapper;
    }

    @Override
    public MentorSectionVO detail(Long studentId, String academicYear) {
        // TODO S-1-03：按 arc_field_def 渲染表单，逐字段计算 visible / editable
        throw new BusinessException(ResultCode.NOT_IMPLEMENTED, "导师寄语板块查询尚未实现");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(Long studentId, MentorSectionSaveDTO dto) {
        // TODO S-1-04：逐字段校验类型、长度、必填、正则，写入 arc_profile_item
        throw new BusinessException(ResultCode.NOT_IMPLEMENTED, "导师寄语板块保存尚未实现");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long studentId, String academicYear) {
        // TODO S-1-05：校验必填齐全，DRAFT / RETURNED 转 SUBMITTED，写变更日志
        throw new BusinessException(ResultCode.NOT_IMPLEMENTED, "导师寄语板块提交尚未实现");
    }
}
