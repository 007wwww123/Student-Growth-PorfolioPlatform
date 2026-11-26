package com.college.growth.interest.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.college.growth.interest.entity.InterestProfile;
import org.apache.ibatis.annotations.Mapper;

/**
 * 兴趣特长板块数据访问。
 *
 * <p>只允许在本板块的 Service 实现中调用；复杂查询写在
 * {@code resources/mapper/interest/InterestProfileMapper.xml}，禁止在 Service 里拼接 SQL。</p>
 */
@Mapper
public interface InterestProfileMapper extends BaseMapper<InterestProfile> {
}
