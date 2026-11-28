package com.college.growth.common.profile.service;

import com.college.growth.common.profile.entity.ArcProfile;
import java.math.BigDecimal;

/**
 * 板块完成度扩展点：给「字段值不在 {@code arc_profile_item}」的板块留一个统一挂载位。
 *
 * <p>默认的 {@link ProfileCompletenessService} 口径是「必填字段中已有有效值的比例」，
 * 隐含前提是字段值都落在 {@code arc_profile_item}。需求 4.7 的 MENTOR 是例外：
 * 它没有主表字段值，全部内容在 {@code arc_mentor_note} 子表里，一行一次谈话。
 * 若沿用默认口径，MENTOR 完成度恒为 0%，且审核通过时的 {@code requireReviewReady}
 * 会以「必填内容未填写」把板块卡死。</p>
 *
 * <p>因此这里只定义扩展点，不改默认实现的行为：<b>没有注册 provider 的板块，
 * 计算结果与扩展点引入之前完全一致</b>。后续若有其他子表型板块（证书技能等），
 * 按同一接口挂载即可，六板块不需要各写一份完成度分支。</p>
 *
 * <p>这也是「为六个板块最终配齐预留一致结构」的落点之一：结构统一，
 * 差异由板块自己声明，而不是在公共类里堆 {@code if (section == MENTOR)}。</p>
 */
public interface SectionCompletenessProvider {

    /** 该实现服务的板块编码，与 {@code arc_profile.section_code} 一致（大写）。 */
    String sectionCode();

    /** 按板块自身的数据源计算完成度百分比（0–100，两位小数），不写库。 */
    BigDecimal compute(ArcProfile profile);

    /** 审核通过前的就绪判定；不满足时抛业务异常，与默认实现同口径。 */
    void requireReviewReady(ArcProfile profile);
}
