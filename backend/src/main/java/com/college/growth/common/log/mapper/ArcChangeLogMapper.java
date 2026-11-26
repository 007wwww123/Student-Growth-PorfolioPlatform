package com.college.growth.common.log.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.college.growth.common.log.entity.ArcChangeLog;
import org.apache.ibatis.annotations.Mapper;

/** 变更日志数据访问。 */
@Mapper
public interface ArcChangeLogMapper extends BaseMapper<ArcChangeLog> {
}
