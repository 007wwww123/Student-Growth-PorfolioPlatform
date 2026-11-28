package com.college.growth.mentor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 导师谈话记录（物理表 {@code arc_mentor_note}）。
 *
 * <p>一行一次谈话，只追加不修改（BR-S07）：除 {@code feedback}/{@code feedbackAt} 外，
 * 本类没有任何更新入口——发现记录有误时追加一条更正记录，而不是改旧行，
 * 否则「学生当年看到的是什么」就再也无法还原。</p>
 */
@Data
@TableName("arc_mentor_note")
public class ArcMentorNote {

    /** 主键，雪花 ID。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 更正的原记录 ID；空表示普通谈话记录。 */
    private Long correctsNoteId;

    /** 更正原因。 */
    private String correctionReason;

    /** 所属 MENTOR 板块实例（{@code arc_profile.id}）。 */
    private Long profileId;

    /** 学生 ID，冗余一列便于按学生查时间线。 */
    private Long studentId;

    /** 学年，与板块实例一致。 */
    private String academicYear;

    /** 谈话日期。 */
    private LocalDate talkDate;

    /** 谈话主题，命中字典 talk_topic。 */
    private String topic;

    /** 建议与寄语，学生可见。 */
    private String advice;

    /** 重要标记：0 无 / 1 ★ / 2 ★★。 */
    private Integer starLevel;

    /** 学生反馈与承诺；非空即锁定，不再接受第二次提交。 */
    private String feedback;

    /** 学生反馈提交时间。 */
    private LocalDateTime feedbackAt;

    /** 辅导员内部备注，学生端不返回。 */
    private String internalRemark;

    /** 撰写辅导员（{@code sys_user.id}）。 */
    private Long counselorId;

    /** 追加时间。 */
    private LocalDateTime createdAt;

    /** 更新时间，仅学生反馈会触发。 */
    private LocalDateTime updatedAt;
}
