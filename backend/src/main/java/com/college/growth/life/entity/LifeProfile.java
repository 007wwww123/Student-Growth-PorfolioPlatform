package com.college.growth.life.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 板块实例：生活心理（LIFE）。
 *
 * <p>物理表 {@code arc_profile}，六个板块共用一张表，以 {@code section_code} 区分。
 * 本实体只承载 LIFE 板块，所有查询必须显式带上 {@code section_code = 'LIFE'}
 * 与 {@code student_id} 条件，禁止跨板块读取。</p>
 */
@Data
@TableName("arc_profile")
public class LifeProfile {

    /** 主键，雪花 ID。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 学生 ID。 */
    private Long studentId;

    /** 学年，如 2025-2026-1。 */
    private String academicYear;

    /** 板块编码，固定为 LIFE。 */
    private String sectionCode;

    /** 状态，取值见 ProfileStatusEnum。 */
    private String status;

    /** 是否计入整体完成度（六板块均 APPROVED 时档案整体完成）。 */
    private Boolean completed;

    /** 提交次数（BR-S05）。 */
    private Integer submitCount;

    /** 撤回次数，全生命周期上限为 1（BR-S06）。 */
    private Integer withdrawCount;

    /** 最近一次审核人。 */
    private Long reviewerId;

    /** 最近一次审核时间。 */
    private LocalDateTime reviewedAt;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
