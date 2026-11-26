package com.college.growth.mentor.dto;

import java.time.LocalDate;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import com.college.growth.common.web.DraftInput;

/**
 * 板块保存入参：导师寄语（MENTOR）。
 *
 * <p>字段校验以需求文档 4.7 为准，规则标识存放在 {@code arc_field_def.validator}，
 * 由校验器注册表按标识执行（BR-F02），此处仅保留接口层的长度与必填兜底。</p>
 */
@Data
public class MentorSectionSaveDTO extends DraftInput {

    /** 学年，如 2025-2026-1。 */
    @Size(max = 16, message = "学年长度不能超过 16")
    private String academicYear;

    /** 谈话日期，不晚于今天且不早于入学日期（mentor.talkDate）。 */
    private LocalDate talkDate;

    /** 谈话主题，命中字典 talk_topic（mentor.topic）。 */
    private String topic;

    /** 建议与寄语，20–1000 字，学生可见（mentor.advice）。 */
    @Size(min = 20, max = 1000, message = "建议与寄语为 20–1000 字")
    private String advice;

    /** 重要标记：0 无 / 1 ★ / 2 ★★（mentor.starLevel）。 */
    @Min(value = 0, message = "重要标记取值 0–2")
    @Max(value = 2, message = "重要标记取值 0–2")
    private Integer starLevel;

    /** 学生反馈与承诺，不超过 500 字，仅可提交一次（mentor.feedback）。 */
    @Size(max = 500, message = "学生反馈不超过 500 字")
    private String feedback;
}
