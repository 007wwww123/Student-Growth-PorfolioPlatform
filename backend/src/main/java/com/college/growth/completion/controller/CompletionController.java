package com.college.growth.completion.controller;

import com.college.growth.common.result.Result;
import com.college.growth.completion.common.CompletionConstants;
import com.college.growth.completion.service.CompletionService;
import com.college.growth.completion.vo.CompletionRefreshVO;
import com.college.growth.completion.vo.CompletionStatusVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 完成标记接口（BR-S10）。
 *
 * <p>路径不放在 {@code /sections/{code}} 之下：完成标记是「学生 × 学年」级别的结论，
 * 不属于任何一个板块，挂在板块下会让前端误以为每个板块各有一份标记。</p>
 */
@RestController
@RequestMapping(CompletionConstants.API_BASE_PATH)
public class CompletionController {

    private final CompletionService completionService;

    public CompletionController(CompletionService completionService) {
        this.completionService = completionService;
    }

    /** 读取完成标记与逐板块状态：学生本人 / 本班辅导员 / 持临时授权的管理员。 */
    @GetMapping("/students/{studentId}")
    public Result<CompletionStatusVO> status(@PathVariable Long studentId,
                                             @RequestParam(required = false) String academicYear) {
        return Result.success(completionService.statusOf(studentId, academicYear));
    }

    /**
     * 手动重算完成标记（运维补偿用）。
     *
     * <p>不传 {@code studentId} 表示整学年重算，仅管理员（持临时授权）可执行；
     * 辅导员只能传本班学生。正常流转路径不需要调这个接口。</p>
     */
    @PostMapping("/refresh")
    public Result<CompletionRefreshVO> refresh(@RequestParam(required = false) Long studentId,
                                               @RequestParam(required = false) String academicYear) {
        return Result.success(completionService.manualRefresh(studentId, academicYear));
    }
}
