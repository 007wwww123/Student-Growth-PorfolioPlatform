package com.college.growth.dict.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.college.growth.dict.entity.SysDict;
import org.apache.ibatis.annotations.Mapper;

/** 字典类型访问。 */
@Mapper
public interface SysDictMapper extends BaseMapper<SysDict> {
}
