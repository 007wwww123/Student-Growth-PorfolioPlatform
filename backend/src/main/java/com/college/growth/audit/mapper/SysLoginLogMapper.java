package com.college.growth.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.college.growth.audit.entity.SysLoginLog;
import org.apache.ibatis.annotations.Mapper;

/** 登录日志访问。 */
@Mapper
public interface SysLoginLogMapper extends BaseMapper<SysLoginLog> {
}
