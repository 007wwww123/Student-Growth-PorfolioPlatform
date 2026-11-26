package com.college.growth.common.profile.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.college.growth.common.profile.entity.ArcProfileItem;
import com.college.growth.common.profile.mapper.ArcProfileItemMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 字段值持久化：读取与写入 {@code arc_profile_item}。
 *
 * <p>写入刻意不用 {@code updateById}：MyBatis-Plus 默认忽略 null，
 * 「清空字段」「丢弃待确认值」都会失效。因此逐列显式 set，保证 null 能落到库里。</p>
 */
@Service
public class ProfileItemService {

    private final ArcProfileItemMapper itemMapper;

    public ProfileItemService(ArcProfileItemMapper itemMapper) {
        this.itemMapper = itemMapper;
    }

    /** 按板块实例加载全部字段值，键为字段编码。 */
    public Map<String, ArcProfileItem> loadByProfile(Long profileId) {
        Map<String, ArcProfileItem> items = new LinkedHashMap<>();
        if (profileId == null) {
            return items;
        }
        List<ArcProfileItem> rows = itemMapper.selectList(Wrappers.<ArcProfileItem>lambdaQuery()
                .eq(ArcProfileItem::getProfileId, profileId));
        for (ArcProfileItem row : rows) {
            items.put(row.getFieldCode(), row);
        }
        return items;
    }

    /** 取单个字段值；不存在返回 null。 */
    public ArcProfileItem find(Long profileId, String fieldCode) {
        return itemMapper.selectOne(Wrappers.<ArcProfileItem>lambdaQuery()
                .eq(ArcProfileItem::getProfileId, profileId)
                .eq(ArcProfileItem::getFieldCode, fieldCode)
                .last("limit 1"));
    }

    /** 新增或更新一行字段值。 */
    public void save(ArcProfileItem item) {
        if (item.getId() == null) {
            itemMapper.insert(item);
            return;
        }
        itemMapper.update(null, Wrappers.<ArcProfileItem>lambdaUpdate()
                .eq(ArcProfileItem::getId, item.getId())
                .set(ArcProfileItem::getValueText, item.getValueText())
                .set(ArcProfileItem::getValueJson, item.getValueJson())
                .set(ArcProfileItem::getPendingText, item.getPendingText())
                .set(ArcProfileItem::getPendingJson, item.getPendingJson())
                .set(ArcProfileItem::getConfirmStatus, item.getConfirmStatus())
                .set(ArcProfileItem::getConfirmedBy, item.getConfirmedBy())
                .set(ArcProfileItem::getConfirmedAt, item.getConfirmedAt())
                .set(ArcProfileItem::getUpdatedBy, item.getUpdatedBy()));
    }
}
