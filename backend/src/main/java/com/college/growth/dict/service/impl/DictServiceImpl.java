package com.college.growth.dict.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.college.growth.common.field.entity.ArcFieldDef;
import com.college.growth.common.field.mapper.ArcFieldDefMapper;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.result.ResultCode;
import com.college.growth.common.util.TextUtils;
import com.college.growth.dict.dto.DictItemSaveDTO;
import com.college.growth.dict.entity.SysDict;
import com.college.growth.dict.entity.SysDictItem;
import com.college.growth.dict.mapper.SysDictItemMapper;
import com.college.growth.dict.mapper.SysDictMapper;
import com.college.growth.dict.service.DictService;
import com.college.growth.dict.vo.DictVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 数据字典（F-15 / AC-008）。
 *
 * <p>停用不删除：字段 options_json 同步为 disabled=true，Enum/StringList 校验拒绝
 * 新值，历史值仍能按 label 显示。</p>
 */
@Service
public class DictServiceImpl implements DictService {

    private final SysDictMapper dictMapper;

    private final SysDictItemMapper itemMapper;

    private final ArcFieldDefMapper fieldDefMapper;

    private final ObjectMapper objectMapper;

    public DictServiceImpl(SysDictMapper dictMapper, SysDictItemMapper itemMapper,
                           ArcFieldDefMapper fieldDefMapper, ObjectMapper objectMapper) {
        this.dictMapper = dictMapper;
        this.itemMapper = itemMapper;
        this.fieldDefMapper = fieldDefMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<DictVO> list() {
        return dictMapper.selectList(Wrappers.<SysDict>lambdaQuery()
                        .eq(SysDict::getStatus, 1).orderByAsc(SysDict::getId))
                .stream().map(this::toVO).toList();
    }

    @Override
    public DictVO get(String code) {
        return toVO(requireDict(code));
    }

    @Override
    @Transactional
    public DictVO addItem(String code, DictItemSaveDTO dto) {
        SysDict dict = requireDict(code);
        long exists = itemMapper.selectCount(Wrappers.<SysDictItem>lambdaQuery()
                .eq(SysDictItem::getDictId, dict.getId())
                .eq(SysDictItem::getItemValue, dto.getValue().trim()));
        if (exists > 0) {
            throw new BusinessException(ResultCode.STATE_CONFLICT, "字典项已存在：" + dto.getValue());
        }
        SysDictItem item = new SysDictItem();
        item.setDictId(dict.getId());
        item.setItemValue(dto.getValue().trim());
        item.setItemLabel(dto.getLabel().trim());
        item.setSortNo(dto.getSortNo() == null ? 0 : dto.getSortNo());
        item.setStatus(1);
        item.setBuiltin(0);
        itemMapper.insert(item);
        sync(dict);
        return toVO(dict);
    }

    @Override
    @Transactional
    public DictVO updateItem(String code, Long itemId, DictItemSaveDTO dto) {
        SysDict dict = requireDict(code);
        SysDictItem item = requireItem(dict, itemId);
        item.setItemLabel(dto.getLabel().trim());
        item.setSortNo(dto.getSortNo() == null ? item.getSortNo() : dto.getSortNo());
        itemMapper.updateById(item);
        sync(dict);
        return toVO(dict);
    }

    @Override
    @Transactional
    public DictVO setStatus(String code, Long itemId, Integer status) {
        SysDict dict = requireDict(code);
        SysDictItem item = requireItem(dict, itemId);
        item.setStatus(status != null && status == 0 ? 0 : 1);
        itemMapper.updateById(item);
        sync(dict);
        return toVO(dict);
    }

    private void sync(SysDict dict) {
        if (TextUtils.isBlank(dict.getFieldCode())) {
            return;
        }
        ArcFieldDef def = fieldDefMapper.selectOne(Wrappers.<ArcFieldDef>lambdaQuery()
                .eq(ArcFieldDef::getFieldCode, dict.getFieldCode()).last("limit 1"));
        if (def == null) {
            return;
        }
        List<SysDictItem> items = itemMapper.selectList(Wrappers.<SysDictItem>lambdaQuery()
                .eq(SysDictItem::getDictId, dict.getId())
                .orderByAsc(SysDictItem::getSortNo).orderByAsc(SysDictItem::getId));
        List<DictVO.Item> options = new ArrayList<>();
        for (SysDictItem item : items) {
            DictVO.Item option = new DictVO.Item();
            option.setValue(item.getItemValue());
            option.setLabel(item.getItemLabel());
            option.setStatus(item.getStatus());
            option.setDisabled(item.getStatus() != null && item.getStatus() == 0);
            options.add(option);
        }
        try {
            def.setOptionsJson(objectMapper.writeValueAsString(options));
            fieldDefMapper.updateById(def);
        } catch (Exception ex) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "字典选项同步失败");
        }
    }

    private SysDict requireDict(String code) {
        SysDict dict = TextUtils.isBlank(code) ? null : dictMapper.selectOne(Wrappers.<SysDict>lambdaQuery()
                .eq(SysDict::getDictCode, code).eq(SysDict::getStatus, 1).last("limit 1"));
        if (dict == null) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "字典不存在：" + code);
        }
        return dict;
    }

    private SysDictItem requireItem(SysDict dict, Long itemId) {
        SysDictItem item = itemId == null ? null : itemMapper.selectById(itemId);
        if (item == null || !dict.getId().equals(item.getDictId())) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "字典项不存在：" + itemId);
        }
        return item;
    }

    private DictVO toVO(SysDict dict) {
        DictVO vo = new DictVO();
        vo.setId(dict.getId());
        vo.setCode(dict.getDictCode());
        vo.setName(dict.getDictName());
        vo.setFieldCode(dict.getFieldCode());
        List<DictVO.Item> items = itemMapper.selectList(Wrappers.<SysDictItem>lambdaQuery()
                        .eq(SysDictItem::getDictId, dict.getId())
                        .orderByAsc(SysDictItem::getSortNo).orderByAsc(SysDictItem::getId))
                .stream().map(item -> {
                    DictVO.Item view = new DictVO.Item();
                    view.setId(item.getId());
                    view.setValue(item.getItemValue());
                    view.setLabel(item.getItemLabel());
                    view.setSortNo(item.getSortNo());
                    view.setStatus(item.getStatus());
                    view.setDisabled(item.getStatus() != null && item.getStatus() == 0);
                    return view;
                }).toList();
        vo.setItems(items);
        return vo;
    }
}
