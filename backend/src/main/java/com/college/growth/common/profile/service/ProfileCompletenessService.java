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

    public ProfileCompletenessService(FieldDefService fieldDefService,
                                      ProfileItemService itemService,
                                      ArcProfileMapper profileMapper) {
        this.fieldDefService = fieldDefService;
        this.itemService = itemService;
        this.profileMapper = profileMapper;
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
        return compute(profile.getSectionCode(), itemService.loadByProfile(profile.getId()));
    }

    /** 按已加载的字段值计算完成度，供已经查过 items 的调用方复用，避免重复查询。 */
    public BigDecimal compute(String sectionCode, Map<String, ArcProfileItem> items) {
        List<ArcFieldDef> requiredDefs = fieldDefService.listActive(sectionCode).stream()
                .filter(ArcFieldDef::isRequired)
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
        BigDecimal percent = compute(profile.getSectionCode(), items);
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
