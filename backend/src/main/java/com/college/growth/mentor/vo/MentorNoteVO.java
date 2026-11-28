package com.college.growth.mentor.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 时间线上的一条谈话记录（需求 4.7）。
 *
 * <p>学生端与辅导员端共用本 VO，但字段按角色裁剪：</p>
 * <ul>
 *   <li>学生端<b>不返回</b> {@code internalRemark} 与 {@code counselorId}，
 *       作者只显示 {@code authorLabel}（固定「辅导员」）；</li>
 *   <li>辅导员端返回内部备注与撰写人 ID。</li>
 * </ul>
 *
 * <p>裁剪发生在服务端组装 VO 时，不是前端隐藏——响应体里根本没有这两个键，
 * 抓包也拿不到内部备注（需求 7.x 与 AC-040）。</p>
 */
@Data
public class MentorNoteVO {

    /** 记录 ID，学生反馈时作为路径参数。 */
    private Long id;

    /** 更正的原记录 ID；空表示普通记录。 */
    private Long correctsNoteId;

    /** 更正原因。 */
    private String correctionReason;

    /** 谈话日期，时间线按此倒序。 */
    private LocalDate talkDate;

    /** 谈话主题，字典英文码，如 ACADEMIC。 */
    private String topic;

    /** 主题中文标签，来自 {@code arc_field_def.options_json}，避免前端再存一份字典。 */
    private String topicLabel;

    /** 建议与寄语，学生可见。 */
    private String advice;

    /** 重要标记：0 无 / 1 ★ / 2 ★★。 */
    private Integer starLevel;

    /** 学生反馈与承诺；为空表示尚未反馈。 */
    private String feedback;

    /** 反馈提交时间，非空即锁定。 */
    private LocalDateTime feedbackAt;

    /** 当前登录学生是否还能对该条提交反馈（仅学生端有意义）。 */
    private Boolean canFeedback;

    /** 作者显示名：学生端固定「辅导员」，不暴露真实姓名。 */
    private String authorLabel;

    /** 追加时间。 */
    private LocalDateTime createdAt;

    /** 辅导员内部备注：仅 COU 可见，学生端不下发。 */
    private String internalRemark;

    /** 撰写辅导员 ID：仅 COU 可见，学生端不下发。 */
    private Long counselorId;
}
