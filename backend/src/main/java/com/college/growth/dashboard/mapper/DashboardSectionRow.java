package com.college.growth.dashboard.mapper;

import lombok.Data;

/**
 * 按「板块 × 状态」聚合的一行统计。
 *
 * <p>六板块状态分布与各板块完成率都由这张中间表在内存里拼出来：
 * 一次分组查询换掉六条 COUNT，板块数量变化时不需要改 SQL。</p>
 */
@Data
public class DashboardSectionRow {

    /** 板块编码，大写（数据库口径）。 */
    private String sectionCode;

    private String status;

    /** 该状态下有多少条板块实例。 */
    private Integer profileCount;

    /** 该状态下有多少名学生；同一板块内各状态求和即为该板块建档学生数。 */
    private Integer studentCount;
}
