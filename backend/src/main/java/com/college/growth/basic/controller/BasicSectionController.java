package com.college.growth.basic.controller;

import com.college.growth.basic.common.BasicConstants;
import com.college.growth.basic.dto.BasicSectionSaveDTO;
import com.college.growth.basic.service.BasicSectionService;
import com.college.growth.basic.vo.BasicSectionVO;
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
 * 板块一 基本信息（BASIC）接口入口。
 *
 * <p>对应需求文档 4.2 字段清单与 2.2 主流程 S-1。当前仅提供路由与返回结构，
 * 具体渲染、校验、状态流转在 Service 层实现。</p>
 */
@RestController
@RequestMapping(BasicConstants.API_BASE_PATH)
public class BasicSectionController {

    private final BasicSectionService basicSectionService;

    public BasicSectionController(BasicSectionService basicSectionService) {
        this.basicSectionService = basicSectionService;
    }

    /** 查询板块详情（S-1-03）：按 arc_field_def 渲染表单，逐字段计算可见与可编辑。 */
    @GetMapping("/{studentId}")
    public Result<BasicSectionVO> detail(@PathVariable Long studentId,
                                            @RequestParam(required = false) String academicYear) {
        return Result.success(basicSectionService.detail(studentId, academicYear));
    }

    /** 保存草稿（S-1-04）：逐字段校验后写入，DRAFT / RETURNED 之外的状态拒绝写入。 */
    @PutMapping("/{studentId}")
    public Result<Void> save(@PathVariable Long studentId,
                             @Valid @RequestBody BasicSectionSaveDTO dto) {
        basicSectionService.save(studentId, dto);
        return Result.success();
    }

    /** 提交板块（S-1-05）：校验必填齐全后由 DRAFT / RETURNED 转为 SUBMITTED。 */
    @PostMapping("/{studentId}/submit")
    public Result<Void> submit(@PathVariable Long studentId,
                               @RequestParam String academicYear) {
        basicSectionService.submit(studentId, academicYear);
        return Result.success();
    }
}
