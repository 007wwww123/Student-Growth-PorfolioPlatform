package com.college.growth.config.field.vo;

import java.util.List;

/** 字段配置页的元数据：类型、来源、校验器、角色。 */
public record FieldConfigMetaVO(List<Item> types, List<Item> sources,
                                List<String> validators, List<String> roles) {

    /** 编码 + 中文名。 */
    public record Item(String code, String label) {
    }
}
