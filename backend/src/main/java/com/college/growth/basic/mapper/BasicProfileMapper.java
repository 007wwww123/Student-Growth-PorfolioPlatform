package com.college.growth.basic.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.college.growth.basic.entity.BasicProfile;
import org.apache.ibatis.annotations.Mapper;

/**
 * 基本信息板块数据访问。
 *
 * <p>只允许在本板块的 Service 实现中调用；复杂查询写在
 * {@code resources/mapper/basic/BasicProfileMapper.xml}，禁止在 Service 里拼接 SQL。</p>
 */
@Mapper
public interface BasicProfileMapper extends BaseMapper<BasicProfile> {
}
