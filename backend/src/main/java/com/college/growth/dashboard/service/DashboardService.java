package com.college.growth.dashboard.service;

import com.college.growth.dashboard.vo.DashboardOverviewVO;

/**
 * 首页进度聚合（需求未编号，按「首页进度聚合」实现）。
 *
 * <p>只读能力，面向辅导员与持临时授权的管理员：学生首页展示的是六个板块入口，
 * 本人进度已在各板块页面与审核详情的完成标记里体现，不再重复一套聚合。</p>
 */
public interface DashboardService {

    /**
     * 汇总本学年进度。
     *
     * @param academicYear 学年，为空取当前学年
     * @param classCode    班级编码，为空表示数据范围内的全部班级
     * @return 首页聚合结果
     */
    DashboardOverviewVO overview(String academicYear, String classCode);
}
