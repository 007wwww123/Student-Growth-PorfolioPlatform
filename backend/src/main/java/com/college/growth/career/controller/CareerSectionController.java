package com.college.growth.career.controller;

import com.college.growth.career.common.CareerConstants;
import com.college.growth.career.dto.CareerSectionSaveDTO;
import com.college.growth.career.service.CareerSectionService;
import com.college.growth.career.vo.CareerSectionVO;
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
 * 板块五 职业规划（CAREER）接口入口。
 *
 * <p>路径与其余板块一致（{@code /sections/career/{studentId}}），前端复用同一套页面外壳与请求封装：
 * 查详情（S-1-03）→ 存草稿（S-1-04）→ 提交（S-1-05）→ 撤回（S-1-09）。</p>
 *
 * <p>注意：本板块只记录「规划意图」，毕业去向的最终值以就业系统为准，不作为统计口径（需求 4.6）。</p>
 */
@RestController
@RequestMapping(CareerConstants.API_BASE_PATH)
public class CareerSectionController {

    private final CareerSectionService careerSectionService;

    public CareerSectionController(CareerSectionService careerSectionService) {
        this.careerSectionService = careerSectionService;
    }

    /** 查询板块详情（S-1-03）：按 arc_field_def 渲染表单，逐字段计算可见与可编辑。 */
    @GetMapping("/{studentId}")
    public Result<CareerSectionVO> detail(@PathVariable Long studentId,
                                          @RequestParam(required = false) String academicYear) {
        return Result.success(careerSectionService.detail(studentId, academicYear));
    }

    /** 保存草稿（S-1-04）：逐字段校验后写入，DRAFT / RETURNED 之外的状态拒绝写入。 */
    @PutMapping("/{studentId}")
    public Result<Void> save(@PathVariable Long studentId,
                             @Valid @RequestBody CareerSectionSaveDTO dto) {
        careerSectionService.save(studentId, dto);
        return Result.success();
    }

    /** 提交板块（S-1-05）：校验必填齐全后由 DRAFT / RETURNED 转为 SUBMITTED。 */
    @PostMapping("/{studentId}/submit")
    public Result<Void> submit(@PathVariable Long studentId,
                               @RequestParam(required = false) String academicYear) {
        careerSectionService.submit(studentId, academicYear);
        return Result.success();
    }

    /** 撤回提交（S-1-09）：仅学生本人，全生命周期一次；第二次返回 EC-40903。 */
    @PostMapping("/{studentId}/withdraw")
    public Result<Void> withdraw(@PathVariable Long studentId,
                                 @RequestParam(required = false) String academicYear) {
        careerSectionService.withdraw(studentId, academicYear);
        return Result.success();
    }
}
