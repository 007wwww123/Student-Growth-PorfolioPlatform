package com.college.growth.mentor.dto;

import lombok.Data;

/**
 * 学生反馈入参（F-11 / {@code archive:mentor:feedback}）。
 *
 * <p>一条谈话只接受一条反馈，提交后锁定，不随板块重提解锁（需求 4.7）。
 * 长度与必填由 Service 按 {@code arc_field_def} 的 {@code mentor.feedback} 定义校验，
 * 这里不重复声明规则，避免 DTO 与字段定义出现两份口径。</p>
 */
@Data
public class MentorFeedbackDTO {

    /** 学年，为空时取当前学年；用于确认记录归属，避免跨学年误改。 */
    private String academicYear;

    /** 学生反馈与承诺，不超过 500 字。 */
    private String feedback;
}
