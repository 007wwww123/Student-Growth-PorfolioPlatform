package com.college.growth.mentor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.college.growth.mentor.entity.MentorProfile;
import org.apache.ibatis.annotations.Mapper;

/**
 * 导师寄语板块数据访问。
 *
 * <p>只允许在本板块的 Service 实现中调用；复杂查询写在
 * {@code resources/mapper/mentor/MentorProfileMapper.xml}，禁止在 Service 里拼接 SQL。</p>
 */
@Mapper
public interface MentorProfileMapper extends BaseMapper<MentorProfile> {
}
