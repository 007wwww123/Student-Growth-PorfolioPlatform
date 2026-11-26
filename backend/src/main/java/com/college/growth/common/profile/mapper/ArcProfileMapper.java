package com.college.growth.common.profile.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.college.growth.common.profile.entity.ArcProfile;
import org.apache.ibatis.annotations.Mapper;

/**
 * 板块实例数据访问（六板块共用）。
 *
 * <p>板块实现与审核模块都通过本 Mapper 读写 {@code arc_profile}；
 * 复杂查询（如待审列表）写在 {@code resources/mapper/review/} 下的 XML，禁止在 Service 拼 SQL。</p>
 */
@Mapper
public interface ArcProfileMapper extends BaseMapper<ArcProfile> {
}
