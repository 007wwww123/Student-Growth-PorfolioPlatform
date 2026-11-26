package com.college.growth.academic.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.college.growth.academic.entity.AcademicProfile;
import org.apache.ibatis.annotations.Mapper;

/**
 * 学业发展板块数据访问。
 *
 * <p>只允许在本板块的 Service 实现中调用；复杂查询写在
 * {@code resources/mapper/academic/AcademicProfileMapper.xml}，禁止在 Service 里拼接 SQL。</p>
 */
@Mapper
public interface AcademicProfileMapper extends BaseMapper<AcademicProfile> {
}
