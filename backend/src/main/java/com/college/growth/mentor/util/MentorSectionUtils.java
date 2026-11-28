package com.college.growth.mentor.util;

import com.college.growth.common.field.FieldOption;
import com.college.growth.common.field.entity.ArcFieldDef;
import com.college.growth.common.util.TextUtils;
import com.college.growth.mentor.common.MentorConstants;
import com.college.growth.mentor.entity.ArcMentorNote;
import com.college.growth.mentor.vo.MentorNoteVO;
import java.time.LocalDate;

/**
 * 导师寄语板块工具方法。
 *
 * <p>单字段的长度、枚举命中仍由 {@code arc_field_def.validator} 的校验器执行（BR-F02），
 * 这里只处理子表结构带来的额外口径：谈话日期区间、必填完整性、按角色裁剪视图。</p>
 */
public final class MentorSectionUtils {

    private MentorSectionUtils() {
    }

    /**
     * 生成板块实例唯一键：板块编码 + 学生 + 学年。
     *
     * <p>建档时用于判重（S-1-01：已存在则跳过，不重复创建）。</p>
     */
    public static String buildSectionKey(Long studentId, String academicYear) {
        return MentorConstants.SECTION_CODE + ":" + studentId + ":" + academicYear;
    }

    /**
     * 校验谈话日期区间：不晚于今天，且不早于入学年份的 1 月 1 日。
     *
     * <p>学生主数据只有 {@code enrollment_year}（没有具体入学日），因此下界按
     * 「入学年 1 月 1 日」取，宁可放宽也不误拦入学当年的暑期谈话。
     * 主数据缺失时只校验「不晚于今天」，避免历史档案无法追加。</p>
     */
    public static boolean isValidTalkDate(LocalDate talkDate, Integer enrollmentYear) {
        if (talkDate == null) {
            return false;
        }
        if (talkDate.isAfter(LocalDate.now())) {
            return false;
        }
        return enrollmentYear == null || !talkDate.isBefore(LocalDate.of(enrollmentYear, 1, 1));
    }

    /** 字段编码取短名，如 {@code mentor.talkDate} → {@code talkDate}。 */
    public static String shortName(ArcFieldDef def) {
        String code = def.getFieldCode();
        return code.substring(code.indexOf('.') + 1);
    }

    /**
     * 一条谈话记录的必填内容是否齐全。
     *
     * <p>对应 {@code arc_field_def} 中 MENTOR 的三个 required=1 字段：
     * talkDate / topic / advice。完成度与审核就绪都以此为准，
     * 而不是去查 {@code arc_profile_item}（那里永远没有值）。</p>
     */
    public static boolean isRequiredComplete(ArcMentorNote note) {
        return note != null && note.getTalkDate() != null
                && !TextUtils.isBlank(note.getTopic())
                && !TextUtils.isBlank(note.getAdvice());
    }

    /** 主题中文标签；字典被清空时回退显示原始码，不抛异常阻断详情。 */
    public static String topicLabelOf(ArcFieldDef topicDef, String topic) {
        if (topic == null || topicDef == null) {
            return topic;
        }
        return FieldOption.parse(topicDef.getOptionsJson()).stream()
                .filter(option -> option.value().equals(topic))
                .map(FieldOption::label)
                .findFirst()
                .orElse(topic);
    }

    /**
     * 组装时间线条目，按角色裁剪字段。
     *
     * @param note       子表记录
     * @param counselor  当前是否辅导员视角
     * @param student    当前是否学生本人视角（决定是否下发 canFeedback）
     * @param topicLabel 主题中文标签
     */
    public static MentorNoteVO toView(ArcMentorNote note, boolean counselor, boolean student,
                                      String topicLabel) {
        MentorNoteVO vo = new MentorNoteVO();
        vo.setId(note.getId());
        vo.setTalkDate(note.getTalkDate());
        vo.setTopic(note.getTopic());
        vo.setTopicLabel(topicLabel);
        vo.setAdvice(note.getAdvice());
        vo.setStarLevel(note.getStarLevel());
        vo.setFeedback(note.getFeedback());
        vo.setFeedbackAt(note.getFeedbackAt());
        vo.setCanFeedback(student && note.getFeedbackAt() == null);
        vo.setAuthorLabel(MentorConstants.AUTHOR_LABEL);
        vo.setCreatedAt(note.getCreatedAt());
        vo.setCorrectsNoteId(note.getCorrectsNoteId());
        vo.setCorrectionReason(note.getCorrectionReason());
        if (counselor) {
            vo.setInternalRemark(note.getInternalRemark());
            vo.setCounselorId(note.getCounselorId());
        }
        return vo;
    }

    /**
     * 追加日志里的新值摘要。
     *
     * <p>只写学生可见的字段，内部备注不进变更日志：日志对所有有查看权的人可见，
     * 写进去等于绕过需求 4.7 的可见性约束。</p>
     */
    public static String noteSummary(ArcMentorNote note) {
        return "谈话日期=" + note.getTalkDate() + "，主题=" + note.getTopic()
                + "，建议长度=" + (note.getAdvice() == null ? 0 : note.getAdvice().length());
    }
}
