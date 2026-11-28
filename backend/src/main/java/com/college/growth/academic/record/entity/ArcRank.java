package com.college.growth.academic.record.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/** 成绩轨迹（{@code arc_rank}，一名学生一学期一行，只读）。 */
@Data
@TableName("arc_rank")
public class ArcRank {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long studentId;

    /** 学期，如 2025-2026-1。 */
    private String academicYear;

    private Integer classRank;

    private Integer majorRank;

    private BigDecimal gpa;

    /** MANUAL / IMPORT。 */
    private String source;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
