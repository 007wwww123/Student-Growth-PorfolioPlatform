package com.college.growth.export.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.college.growth.export.entity.SysExportTask;
import org.apache.ibatis.annotations.Mapper;

/** 导出任务访问。 */
@Mapper
public interface SysExportTaskMapper extends BaseMapper<SysExportTask> {
}
