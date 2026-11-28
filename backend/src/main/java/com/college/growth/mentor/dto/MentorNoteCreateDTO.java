package com.college.growth.mentor.dto;

import java.time.LocalDate;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 追加谈话记录入参（F-11 / {@code archive:mentor:append}）。
 *
 * <p>每次追加一条记录，不提供「修改 / 删除」字段：需求 4.7 明确记录只可追加（BR-S07）。</p>
 *
 * <p><b>这里刻意不写字段级校验注解</b>：必填、长度、枚举、日期区间全部由
 * {@code arc_field_def} 的校验器与板块规则执行，统一返回 {@code EC-42201 /
 * EC-42202}。若 DTO 上再写一份 {@code @Size}，超长值会先被 Bean Validation
 * 拦成 400 {@code EC-40001}，同一个字段在两条链路上出现两种错误码，
 * 也会让「字段定义是唯一来源」（BR-F02）失效。</p>
 */
@Data
public class MentorNoteCreateDTO {

    /** 学年，如 2025-2026-1；为空取当前学年（信封参数，不是字段值）。 */
    @Size(max = 16, message = "学年长度不能超过 16")
    private String academicYear;

    /** 谈话日期（mentor.talkDate）：不晚于今天且不早于入学日期。 */
    private LocalDate talkDate;

    /** 谈话主题（mentor.topic），命中字典 talk_topic 的英文码。 */
    private String topic;

    /** 建议与寄语（mentor.advice），20–1000 字，学生可见。 */
    private String advice;

    /** 重要标记：0 无 / 1 ★ / 2 ★★（mentor.starLevel）。 */
    private Integer starLevel;

    /** 内部备注（mentor.internalRemark），不超过 500 字，学生端不返回。 */
    private String internalRemark;
}
