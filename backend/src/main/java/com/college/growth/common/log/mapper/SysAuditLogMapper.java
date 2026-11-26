package com.college.growth.common.log.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.college.growth.common.log.entity.SysAuditLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统审计日志数据访问（物理表 {@code sys_audit_log}）。
 */
@Mapper
public interface SysAuditLogMapper extends BaseMapper<SysAuditLog> {
}
