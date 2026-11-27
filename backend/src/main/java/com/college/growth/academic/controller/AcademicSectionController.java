package com.college.growth.academic.controller;

import com.college.growth.academic.common.AcademicConstants;
import com.college.growth.academic.dto.AcademicSectionSaveDTO;
import com.college.growth.academic.service.AcademicSectionService;
import com.college.growth.academic.vo.AcademicSectionVO;
import com.college.growth.common.result.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 板块二 学业发展（ACADEMIC）接口入口。
 *
 * <p>字段渲染、校验与状态流转由 Service 层处理。</p>
 */
@RestController
@RequestMapping(AcademicConstants.API_BASE_PATH)
public class AcademicSectionController {

    private final AcademicSectionService academicSectionService;

    public AcademicSectionController(AcademicSectionService academicSectionService) {
        this.academicSectionService = academicSectionService;
    }

    /** 查询板块详情（S-1-03）：按 arc_field_def 渲染表单，逐字段计算可见与可编辑。 */
    @GetMapping("/{studentId}")
    public Result<AcademicSectionVO> detail(@PathVariable Long studentId,
                                            @RequestParam(required = false) String academicYear) {
        return Result.success(academicSectionService.detail(studentId, academicYear));
    }

    /** 保存草稿（S-1-04）：逐字段校验后写入，DRAFT / RETURNED 之外的状态拒绝写入。 */
    @PutMapping("/{studentId}")
    public Result<Void> save(@PathVariable Long studentId,
                             @Valid @RequestBody AcademicSectionSaveDTO dto) {
        academicSectionService.save(studentId, dto);
        return Result.success();
    }

    /** 提交板块（S-1-05）：校验必填齐全后由 DRAFT / RETURNED 转为 SUBMITTED。 */
    @PostMapping("/{studentId}/submit")
    public Result<Void> submit(@PathVariable Long studentId,
                               @RequestParam(required = false) String academicYear) {
        academicSectionService.submit(studentId, academicYear);
        return Result.success();
    }

    @PostMapping("/{studentId}/withdraw")
    public Result<Void> withdraw(@PathVariable Long studentId,
                                 @RequestParam(required = false) String academicYear) {
        academicSectionService.withdraw(studentId, academicYear);
        return Result.success();
    }
}
