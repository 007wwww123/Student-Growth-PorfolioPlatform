package com.college.growth.common.student.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.college.growth.common.student.entity.StuStudent;
import org.apache.ibatis.annotations.Mapper;

/** 学生主数据访问。 */
@Mapper
public interface StuStudentMapper extends BaseMapper<StuStudent> {
}
