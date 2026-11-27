package com.college.growth.dashboard.vo;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 单个板块在本学年范围内的状态分布。
 *
 * <p>五个状态计数之和等于 {@code studentCount}：同一板块每个学生只有一条记录。</p>
 */
@Data
public class SectionProgressVO {

    /** 板块编码，小写（与前端 SectionCode 一致）。 */
    private String sectionCode;

    private String sectionName;

    /** 该板块已建档学生数。 */
    private int studentCount;

    private int draft;

    private int submitted;

    private int returned;

    private int approved;

    private int locked;

    /** 完成率 =（已通过 + 已归档）/ 建档数，百分比一位小数；无建档时为 0。 */
    private BigDecimal completionRate;
}
