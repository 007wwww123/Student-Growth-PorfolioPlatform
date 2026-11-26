package com.college.growth.mentor.controller;

import com.college.growth.mentor.common.MentorConstants;
import com.college.growth.mentor.dto.MentorSectionSaveDTO;
import com.college.growth.mentor.service.MentorSectionService;
import com.college.growth.mentor.vo.MentorSectionVO;
import com.college.growth.common.result.Result;
import java.util.Map;
import com.college.growth.common.web.DraftInput;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 板块六 导师寄语（MENTOR）接口入口。
 *
 * <p>对应需求文档 4.7 字段清单与 2.2 主流程 S-1。当前仅提供路由与返回结构，
 * 具体渲染、校验、状态流转在 Service 层实现。</p>
 */
@RestController
@RequestMapping(MentorConstants.API_BASE_PATH)
public class MentorSectionController {

    private final MentorSectionService mentorSectionService;

    public MentorSectionController(MentorSectionService mentorSectionService) {
        this.mentorSectionService = mentorSectionService;
    }

    /** 查询板块详情（S-1-03）：按 arc_field_def 渲染表单，逐字段计算可见与可编辑。 */
    @GetMapping("/{studentId}")
    public Result<MentorSectionVO> detail(@PathVariable Long studentId,
                                            @RequestParam(required = false) String academicYear) {
        return Result.success(mentorSectionService.detail(studentId, academicYear));
    }

    /** 保存草稿（S-1-04）：逐字段校验后写入，DRAFT / RETURNED 之外的状态拒绝写入。 */
    @PutMapping("/{studentId}")
    public Result<Void> save(@PathVariable Long studentId,
                             @RequestBody Map<String, Object> fields) {
        MentorSectionSaveDTO dto = new MentorSectionSaveDTO();
        dto.setAcademicYear(DraftInput.bind(dto, fields));
        mentorSectionService.save(studentId, dto);
        return Result.success();
    }

    /** 提交板块（S-1-05）：校验必填齐全后由 DRAFT / RETURNED 转为 SUBMITTED。 */
    @PostMapping("/{studentId}/submit")
    public Result<Void> submit(@PathVariable Long studentId,
                               @RequestParam String academicYear) {
        mentorSectionService.submit(studentId, academicYear);
        return Result.success();
    }
}
