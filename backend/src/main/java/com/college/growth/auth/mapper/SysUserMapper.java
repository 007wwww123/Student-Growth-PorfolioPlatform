package com.college.growth.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.college.growth.auth.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/** 账号数据访问：登录校验、会话信息回读。 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
