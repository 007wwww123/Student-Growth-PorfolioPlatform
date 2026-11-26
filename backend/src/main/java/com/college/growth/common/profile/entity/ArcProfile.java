package com.college.growth.common.profile.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 板块实例（物理表 {@code arc_profile}）：一个学生 × 一个板块 × 一个学年一条记录。
 *
 * <p>六个板块共用本实体，以 {@code section_code} 区分。任何查询都必须同时带
 * {@code student_id}、{@code section_code}、{@code academic_year} 三个条件，
 * 否则会把别的板块数据读进来。</p>
 *
 * <p>放在公共包而不是某个板块下，是因为审核模块要对六个板块统一做状态流转：
 * 若实体留在 BASIC 包内，审核模块就得反向依赖板块实现，状态机也就无法共用。</p>
 */
@Data
@TableName("arc_profile")
public class ArcProfile {

    /** 主键，雪花 ID。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 学生 ID。 */
    private Long studentId;

    /** 学年，如 2025-2026-1。 */
    private String academicYear;

    /** 板块编码，大写，如 BASIC。 */
    private String sectionCode;

    /** 状态，取值见 {@code ProfileStatusEnum}。 */
    private String status;

    /** 六板块是否全部 APPROVED（BR-S10），由审核流程维护，禁止前端计算。 */
    private Boolean completed;

    /** 完成度百分比：已填必填项 / 必填项总数，只统计启用字段。 */
    private BigDecimal completeness;

    /** 提交次数（BR-S05）。 */
    private Integer submitCount;

    /** 撤回次数，全生命周期上限 1（BR-S06）。 */
    private Integer withdrawCount;

    /** 最近一次审核人。 */
    private Long reviewerId;

    /** 最近一次审核时间。 */
    private LocalDateTime reviewedAt;

    /** 最近一次提交时间。 */
    private LocalDateTime submittedAt;

    /**
     * 乐观锁版本号。
     *
     * <p>由 MyBatis-Plus 的乐观锁插件维护：更新时自动带 {@code version = 旧值} 并把版本加一，
     * 影响行数为 0 即说明有人先改过，调用方据此返回 {@code EC-40907}。
     * 业务代码不要手工 {@code setVersion(getVersion() + 1)}，否则会双重自增。</p>
     */
    @Version
    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
