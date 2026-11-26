package com.college.growth.life.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import com.college.growth.common.web.DraftInput;

/**
 * 板块保存入参：生活心理（LIFE）。
 *
 * <p>字段校验以需求文档 4.5 为准，规则标识存放在 {@code arc_field_def.validator}，
 * 由校验器注册表按标识执行（BR-F02），此处仅保留接口层的长度与必填兜底。</p>
 */
@Data
public class LifeSectionSaveDTO extends DraftInput {

    /** 学年，如 2025-2026-1。 */
    @Size(max = 16, message = "学年长度不能超过 16")
    private String academicYear;

    /** 社团 / 学生工作职务，不超过 64 字，无职务填"无"（life.orgRole）。 */
    @Size(max = 64, message = "职务不超过 64 字")
    private String orgRole;

    /** 参与度：HIGH / MID / LOW（life.orgLevel）。 */
    private String orgLevel;

    /** 作息习惯：EARLY / NORMAL / LATE（life.sleepHabit）。 */
    private String sleepHabit;

    /** 运动频率：NONE / LOW / MID / HIGH（life.exerciseFreq）。 */
    private String exerciseFreq;

    /** 压力自评，整数 1–5，仅学生本人可见（life.stressScore）。 */
    @Min(value = 1, message = "压力自评取值 1–5")
    @Max(value = 5, message = "压力自评取值 1–5")
    private Integer stressScore;

    /** 整体情绪：GOOD / FAIR / LOW（life.mood）。 */
    private String mood;
}
