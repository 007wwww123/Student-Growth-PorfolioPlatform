package com.college.growth.common.field.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.college.growth.common.field.entity.ArcFieldDef;
import com.college.growth.common.field.mapper.ArcFieldDefMapper;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 字段定义读取入口。
 *
 * <p>只返回启用字段（BR-F03：停用字段不渲染、不计必填，但历史值仍可查）。
 * 后续加缓存时只改这里，业务代码不感知。</p>
 */
@Service
public class FieldDefService {

    private final ArcFieldDefMapper fieldDefMapper;

    public FieldDefService(ArcFieldDefMapper fieldDefMapper) {
        this.fieldDefMapper = fieldDefMapper;
    }

    /** 按板块取启用字段，按 sort_no 升序。 */
    public List<ArcFieldDef> listActive(String sectionCode) {
        return fieldDefMapper.selectList(Wrappers.<ArcFieldDef>lambdaQuery()
                .eq(ArcFieldDef::getSectionCode, sectionCode)
                .eq(ArcFieldDef::getStatus, 1)
                .orderByAsc(ArcFieldDef::getSortNo));
    }

    /** 按字段编码取定义（含停用字段，用于历史值展示与敏感字段查看）。 */
    public ArcFieldDef getByCode(String fieldCode) {
        return fieldDefMapper.selectOne(Wrappers.<ArcFieldDef>lambdaQuery()
                .eq(ArcFieldDef::getFieldCode, fieldCode)
                .last("limit 1"));
    }
}
