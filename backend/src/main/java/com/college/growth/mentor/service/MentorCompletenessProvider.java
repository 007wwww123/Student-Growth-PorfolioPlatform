package com.college.growth.mentor.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.profile.entity.ArcProfile;
import com.college.growth.common.profile.service.SectionCompletenessProvider;
import com.college.growth.common.result.ResultCode;
import com.college.growth.mentor.common.MentorConstants;
import com.college.growth.mentor.entity.ArcMentorNote;
import com.college.growth.mentor.mapper.ArcMentorNoteMapper;
import com.college.growth.mentor.util.MentorSectionUtils;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * MENTOR 板块完成度扩展实现。
 *
 * <p>需求 4.7：本板块没有主表字段值，全部内容在 {@code arc_mentor_note} 子表。
 * 默认完成度按 {@code arc_profile_item} 里的必填字段统计，对 MENTOR 恒为 0%，
 * 且 {@code requireReviewReady} 会在审核通过时抛「请退回补填」，把板块卡死。</p>
 *
 * <p>口径：只要存在<b>至少一条必填齐全</b>的谈话记录即视为 100%，否则 0%。
 * 板块级完成度不是「谈话条数的平均值」——一年谈几次由辅导员决定，
 * 用条数做分母会让多谈话反而拉低完成度。</p>
 *
 * <p>通过 {@link SectionCompletenessProvider} 挂载，未注册该实现时公共类的行为零变化，
 * 其余五个板块不受影响。</p>
 */
@Service
public class MentorCompletenessProvider implements SectionCompletenessProvider {

    private static final BigDecimal FULL = new BigDecimal("100.00");

    private final ArcMentorNoteMapper notes;

    public MentorCompletenessProvider(ArcMentorNoteMapper notes) {
        this.notes = notes;
    }

    @Override
    public String sectionCode() {
        return MentorConstants.SECTION_CODE;
    }

    @Override
    public BigDecimal compute(ArcProfile profile) {
        if (profile == null || profile.getId() == null) {
            return BigDecimal.ZERO;
        }
        List<ArcMentorNote> rows = notes.selectList(Wrappers.<ArcMentorNote>lambdaQuery()
                .eq(ArcMentorNote::getProfileId, profile.getId()));
        return rows.stream().anyMatch(MentorSectionUtils::isRequiredComplete) ? FULL : BigDecimal.ZERO;
    }

    @Override
    public void requireReviewReady(ArcProfile profile) {
        if (compute(profile).compareTo(FULL) < 0) {
            throw new BusinessException(ResultCode.REQUIRED_FIELD_MISSING,
                    "请退回补填：导师寄语（至少一条完整谈话记录）");
        }
    }
}
