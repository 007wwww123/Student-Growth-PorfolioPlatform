package com.college.growth.dashboard.controller;

import com.college.growth.common.result.Result;
import com.college.growth.dashboard.common.DashboardConstants;
import com.college.growth.dashboard.service.DashboardService;
import com.college.growth.dashboard.vo.DashboardOverviewVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 首页进度聚合接口（需求未编号，按「首页进度聚合」实现）。
 *
 * <p>一个端点返回首页所需全部数字：学生数、完成数、待审数、六板块状态分布、班级完成率。
 * 数据范围由服务端按角色收口，前端不传「看谁的数据」这类参数——传了也不作数。</p>
 */
@RestController
@RequestMapping(DashboardConstants.API_BASE_PATH)
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /** 汇总本学年进度；辅导员只统计带班，管理员需持临时授权。 */
    @GetMapping("/overview")
    public Result<DashboardOverviewVO> overview(
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) String classCode) {
        return Result.success(dashboardService.overview(academicYear, classCode));
    }
}
