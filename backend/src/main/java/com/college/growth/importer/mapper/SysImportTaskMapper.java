package com.college.growth.importer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.college.growth.importer.entity.SysImportTask;
import org.apache.ibatis.annotations.Mapper;

/** 导入任务数据访问。 */
@Mapper
public interface SysImportTaskMapper extends BaseMapper<SysImportTask> {
}
