package com.college.growth.career.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.college.growth.career.entity.CareerProfile;
import org.apache.ibatis.annotations.Mapper;

/**
 * 职业规划板块数据访问。
 *
 * <p>只允许在本板块的 Service 实现中调用；复杂查询写在
 * {@code resources/mapper/career/CareerProfileMapper.xml}，禁止在 Service 里拼接 SQL。</p>
 */
@Mapper
public interface CareerProfileMapper extends BaseMapper<CareerProfile> {
}
