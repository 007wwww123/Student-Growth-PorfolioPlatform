package com.college.growth.common.field.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.college.growth.common.field.entity.ArcFieldDef;
import org.apache.ibatis.annotations.Mapper;

/**
 * 字段定义数据访问。
 *
 * <p>读取入口统一走 {@code FieldDefService}，业务代码不要直接注入本 Mapper，
 * 否则缓存与排序口径会散落各处。</p>
 */
@Mapper
public interface ArcFieldDefMapper extends BaseMapper<ArcFieldDef> {
}
