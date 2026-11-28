package com.college.growth.life.controller;

import com.college.growth.common.result.Result;
import com.college.growth.life.common.LifeConstants;
import com.college.growth.life.dto.LifeSectionSaveDTO;
import com.college.growth.life.service.LifeSectionService;
import com.college.growth.life.vo.LifeSectionVO;
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
 * 板块四 生活心理（LIFE）接口入口。
 *
 * <p>路径与板块一至三完全一致（{@code /sections/life/{studentId}}），
 * 前端 {@code SectionShell} 按 {@code code} 复用同一套页面与请求封装：
 * 查详情（S-1-03）→ 存草稿（S-1-04）→ 提交（S-1-05）→ 撤回（S-1-09）。
 * 差异只在字段清单，字段由 {@code arc_field_def} 下发。</p>
 */
@RestController
@RequestMapping(LifeConstants.API_BASE_PATH)
public class LifeSectionController {

    private final LifeSectionService lifeSectionService;

    public LifeSectionController(LifeSectionService lifeSectionService) {
        this.lifeSectionService = lifeSectionService;
    }

    /** 查询板块详情（S-1-03）：按 arc_field_def 渲染表单，逐字段计算可见与可编辑。 */
    @GetMapping("/{studentId}")
    public Result<LifeSectionVO> detail(@PathVariable Long studentId,
                                        @RequestParam(required = false) String academicYear) {
        return Result.success(lifeSectionService.detail(studentId, academicYear));
    }

    /** 保存草稿（S-1-04）：逐字段校验后写入，DRAFT / RETURNED 之外的状态拒绝写入。 */
    @PutMapping("/{studentId}")
    public Result<Void> save(@PathVariable Long studentId,
                             @Valid @RequestBody LifeSectionSaveDTO dto) {
        lifeSectionService.save(studentId, dto);
        return Result.success();
    }

    /** 提交板块（S-1-05）：校验必填齐全后由 DRAFT / RETURNED 转为 SUBMITTED。 */
    @PostMapping("/{studentId}/submit")
    public Result<Void> submit(@PathVariable Long studentId,
                               @RequestParam(required = false) String academicYear) {
        lifeSectionService.submit(studentId, academicYear);
        return Result.success();
    }

    /** 撤回提交（S-1-09）：仅学生本人，全生命周期一次；第二次返回 EC-40903。 */
    @PostMapping("/{studentId}/withdraw")
    public Result<Void> withdraw(@PathVariable Long studentId,
                                 @RequestParam(required = false) String academicYear) {
        lifeSectionService.withdraw(studentId, academicYear);
        return Result.success();
    }
}
