package com.college.growth.dashboard.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 看板统计查询（复杂聚合走 XML，见 {@code resources/mapper/dashboard/DashboardMapper.xml}）。
 *
 * <p>两条查询共用同一个数据范围与班级取值口径，避免「总数」与「板块分布」对不上：
 * 班级一律取 {@code COALESCE(stu_student.class_code, basic.classId)}，
 * 与 {@code StudentClassService} 的优先级一致。</p>
 */
@Mapper
public interface DashboardMapper {

    /**
     * 按班级聚合：学生数、完成数、各状态板块数。
     *
     * @param academicYear 学年
     * @param counselorId  辅导员 ID，非空时只统计其带班
     * @param classCode    班级编码，非空时只统计该班
     */
    List<DashboardClassRow> selectClassRows(@Param("academicYear") String academicYear,
                                            @Param("counselorId") Long counselorId,
                                            @Param("classCode") String classCode);

    /**
     * 按「板块 × 状态」聚合。
     *
     * @param academicYear 学年
     * @param counselorId  辅导员 ID，非空时只统计其带班
     * @param classCode    班级编码，非空时只统计该班
     */
    List<DashboardSectionRow> selectSectionRows(@Param("academicYear") String academicYear,
                                                @Param("counselorId") Long counselorId,
                                                @Param("classCode") String classCode);
}
