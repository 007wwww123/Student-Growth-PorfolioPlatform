package com.college.growth.life.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.college.growth.life.entity.LifeProfile;
import org.apache.ibatis.annotations.Mapper;

/**
 * 生活心理板块数据访问。
 *
 * <p>只允许在本板块的 Service 实现中调用；复杂查询写在
 * {@code resources/mapper/life/LifeProfileMapper.xml}，禁止在 Service 里拼接 SQL。</p>
 */
@Mapper
public interface LifeProfileMapper extends BaseMapper<LifeProfile> {
}
