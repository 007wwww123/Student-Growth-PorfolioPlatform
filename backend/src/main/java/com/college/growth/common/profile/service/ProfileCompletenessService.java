package com.college.growth.common.profile.service;

import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.field.FieldValues;
import com.college.growth.common.field.entity.ArcFieldDef;
import com.college.growth.common.field.service.FieldDefService;
import com.college.growth.common.field.validation.validator.DerivedFieldValidator;
import com.college.growth.common.profile.FieldValueCodec;
import com.college.growth.common.profile.entity.ArcProfile;
import com.college.growth.common.profile.entity.ArcProfileItem;
import com.college.growth.common.profile.mapper.ArcProfileMapper;
import com.college.growth.common.result.ResultCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 完成度计算（六板块与审核模块共用）。
 *
 * <p>口径：必填且非推导字段中，已有有效值的比例，保留两位小数。有效值指正式值，
 * 正式值为空时取待确认值——否则「紧急联系电话」在辅导员确认前会一直算作未填，
 * 学生看到的完成度会与提交校验结论互相矛盾。</p>
 *
 * <p><b>为什么单独抽出来</b>：完成度不只在保存时变化。提交、撤回、审核，以及学工系统
 * 导入字段值之后，都需要重算；如果只写在板块保存方法里，其余节点就会留下过期值
 * （待审列表按 {@code arc_profile.completeness} 展示，过期会直接误导辅导员）。
 * 因此计算逻辑只放在本类，调用点由业务节点各自决定。</p>
 */
@Service
public class ProfileCompletenessService {

    private final FieldDefService fieldDefService;

    private final ProfileItemService itemService;

    private final ArcProfileMapper profileMapper;

    /**
     * 子表型板块的完成度扩展点。Spring 在没有实现时注入空集合，
     * 因此本类对 BASIC/ACADEMIC/INTEREST/LIFE/CAREER 的行为与扩展点引入前完全一致。
     */
    private final List<SectionCompletenessProvider> providers;

    /**
     * 保留三参构造签名：审核、板块、完成度的既有单测都显式 {@code new} 本类，
     * 加一个重载比改四个测试文件更稳妥，也避免把「扩展点」变成使用方的必填参数。
     */
    public ProfileCompletenessService(FieldDefService fieldDefService,
                                      ProfileItemService itemService,
                                      ArcProfileMapper profileMapper) {
        this(fieldDefService, itemService, profileMapper, List.of());
    }

    /**
     * Spring 注入用的构造器。没有注册任何扩展实现时 Spring 注入空集合，
     * 因此本类对 BASIC/ACADEMIC/INTEREST/LIFE/CAREER 的行为与扩展点引入前完全一致。
     */
    @Autowired
    public ProfileCompletenessService(FieldDefService fieldDefService,
                                      ProfileItemService itemService,
                                      ArcProfileMapper profileMapper,
                                      List<SectionCompletenessProvider> providers) {
        this.fieldDefService = fieldDefService;
        this.itemService = itemService;
        this.profileMapper = profileMapper;
        this.providers = providers == null ? List.of() : providers;
    }

    /** 找到该板块的扩展实现；未注册时返回 empty，走默认口径。 */
    private Optional<SectionCompletenessProvider> providerFor(ArcProfile profile) {
        if (profile == null || profile.getSectionCode() == null) {
            return Optional.empty();
        }
        return providers.stream()
                .filter(provider -> provider.sectionCode().equalsIgnoreCase(profile.getSectionCode()))
                .findFirst();
    }

    /**
     * 按当前库里的字段值计算完成度，不写库。
     *
     * <p>用于详情展示与状态流转前落值：读接口不应该为了刷新一个百分比去写表。</p>
     */
    public BigDecimal compute(ArcProfile profile) {
        if (profile == null || profile.getId() == null) {
            return BigDecimal.ZERO;
        }
        // MENTOR 一类子表板块的字段值不在 arc_profile_item，交给板块自己的实现计算。
        var provider = providerFor(profile);
        if (provider.isPresent()) {
            return provider.get().compute(profile);
        }
        return compute(profile.getSectionCode(), itemService.loadByProfile(profile.getId()));
    }

    /** 审核只接受正式值；候选值可用于提交，但必须在通过前处理。 */
    public void requireReviewReady(ArcProfile profile) {
        var provider = providerFor(profile);
        if (provider.isPresent()) {
            provider.get().requireReviewReady(profile);
            return;
        }
        var items = itemService.loadByProfile(profile.getId());
        for (var def : fieldDefService.listActive(profile.getSectionCode())) {
            var item = items.get(def.getFieldCode());
            if (item != null && item.hasPending()) {
                throw new BusinessException(ResultCode.STATE_CONFLICT, "请先确认或驳回：" + def.getFieldName());
            }
            if (def.isRequiredFlag() && !DerivedFieldValidator.CODE.equalsIgnoreCase(def.getValidator())
                    && FieldValues.isEmpty(FieldValueCodec.read(def, item))) {
                throw new BusinessException(ResultCode.REQUIRED_FIELD_MISSING, "请退回补填：" + def.getFieldName());
            }
        }
    }

    /** 按已加载的字段值计算完成度，供已经查过 items 的调用方复用，避免重复查询。 */
    public BigDecimal compute(String sectionCode, Map<String, ArcProfileItem> items) {
        List<ArcFieldDef> requiredDefs = fieldDefService.listActive(sectionCode).stream()
                .filter(ArcFieldDef::isRequiredFlag)
                // 推导字段（如专业由班级推导）不落 arc_profile_item，不计入必填口径。
                .filter(def -> !DerivedFieldValidator.CODE.equalsIgnoreCase(def.getValidator()))
                .toList();
        if (requiredDefs.isEmpty()) {
            return BigDecimal.ZERO;
        }
        long filled = requiredDefs.stream()
                .filter(def -> !FieldValues.isEmpty(effectiveValue(def, items.get(def.getFieldCode()))))
                .count();
        return BigDecimal.valueOf(filled * 100.0 / requiredDefs.size())
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 重新计算并写回 {@code arc_profile.completeness}。
     *
     * <p>带乐观锁：影响行数为 0 说明读与写之间有人改过，必须让调用方整体回滚，
     * 不能把半份字段值留在库里。</p>
     */
    public BigDecimal refresh(ArcProfile profile) {
        return refresh(profile, itemService.loadByProfile(profile == null ? null : profile.getId()));
    }

    /**
     * 用调用方已加载的字段值重算并写回。
     *
     * <p>保存接口刚写完一批字段，内存里的 items 才是最新状态；再查一次库既多一次往返，
     * 也会读到与内存不一致的快照（同事务内虽然可见，但没必要冒这个险）。</p>
     */
    public BigDecimal refresh(ArcProfile profile, Map<String, ArcProfileItem> items) {
        BigDecimal percent = providerFor(profile)
                .map(provider -> provider.compute(profile))
                .orElseGet(() -> compute(profile.getSectionCode(), items));
        profile.setCompleteness(percent);
        int rows = profileMapper.updateById(profile);
        if (rows == 0) {
            throw new BusinessException(ResultCode.VERSION_CONFLICT);
        }
        return percent;
    }

    /** 有效值：正式值为空时取待确认值。 */
    private Object effectiveValue(ArcFieldDef def, ArcProfileItem item) {
        Object value = FieldValueCodec.read(def, item);
        if (!FieldValues.isEmpty(value)) {
            return value;
        }
        return FieldValueCodec.readPending(def, item);
    }
}
