package com.college.growth.interest.controller;

import com.college.growth.interest.common.InterestConstants;
import com.college.growth.interest.dto.InterestSectionSaveDTO;
import com.college.growth.interest.service.InterestSectionService;
import com.college.growth.interest.vo.InterestSectionVO;
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
 * 板块三 兴趣特长（INTEREST）接口入口。
 *
 * <p>对应需求文档 4.4 字段清单与 2.2 主流程 S-1。当前仅提供路由与返回结构，
 * 具体渲染、校验、状态流转在 Service 层实现。</p>
 */
@RestController
@RequestMapping(InterestConstants.API_BASE_PATH)
public class InterestSectionController {

    private final InterestSectionService interestSectionService;

    public InterestSectionController(InterestSectionService interestSectionService) {
        this.interestSectionService = interestSectionService;
    }

    /** 查询板块详情（S-1-03）：按 arc_field_def 渲染表单，逐字段计算可见与可编辑。 */
    @GetMapping("/{studentId}")
    public Result<InterestSectionVO> detail(@PathVariable Long studentId,
                                            @RequestParam(required = false) String academicYear) {
        return Result.success(interestSectionService.detail(studentId, academicYear));
    }

    /** 保存草稿（S-1-04）：逐字段校验后写入，DRAFT / RETURNED 之外的状态拒绝写入。 */
    @PutMapping("/{studentId}")
    public Result<Void> save(@PathVariable Long studentId,
                             @RequestBody Map<String, Object> fields) {
        InterestSectionSaveDTO dto = new InterestSectionSaveDTO();
        dto.setAcademicYear(DraftInput.bind(dto, fields));
        interestSectionService.save(studentId, dto);
        return Result.success();
    }

    /** 提交板块（S-1-05）：校验必填齐全后由 DRAFT / RETURNED 转为 SUBMITTED。 */
    @PostMapping("/{studentId}/submit")
    public Result<Void> submit(@PathVariable Long studentId,
                               @RequestParam String academicYear) {
        interestSectionService.submit(studentId, academicYear);
        return Result.success();
    }
}
