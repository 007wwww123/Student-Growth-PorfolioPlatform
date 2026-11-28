package com.college.growth.permission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.college.growth.permission.entity.SysRolePermission;
import org.apache.ibatis.annotations.Mapper;

/** 角色权限绑定访问。 */
@Mapper
public interface SysRolePermissionMapper extends BaseMapper<SysRolePermission> {
}
