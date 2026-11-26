package com.college.growth.review.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.college.growth.common.profile.entity.ArcProfile;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 审核列表查询（复杂 SQL 走 XML，见 {@code resources/mapper/review/ReviewTaskMapper.xml}）。
 *
 * <p>六个板块共用同一条 SQL：板块差异只体现为 {@code section_code} 参数，
 * 不存在「基本信息一套查询、学业发展另一套」的重复实现。</p>
 */
@Mapper
public interface ReviewTaskMapper {

    /**
     * 分页查询待审板块。
     *
     * @param page         分页参数
     * @param sectionCode  板块编码，为空表示六个板块全查
     * @param status       状态，为空默认 SUBMITTED（由 Service 兜底）
     * @param academicYear 学年，为空表示不限
     * @param classId      班级编码，为空表示不限
     * @param counselorId  辅导员 ID，非空时只返回其带班学生的板块（数据范围）
     * @return 分页结果
     */
    IPage<ArcProfile> selectTaskPage(IPage<ArcProfile> page,
                                     @Param("sectionCode") String sectionCode,
                                     @Param("status") String status,
                                     @Param("academicYear") String academicYear,
                                     @Param("classId") String classId,
                                     @Param("counselorId") Long counselorId);

    /**
     * 批量取这些学生本学年的基本信息字段（姓名、班级），用于填充列表。
     *
     * @param studentIds 学生 ID 集合，不能为空
     * @param fieldCodes 字段编码集合
     * @param academicYear 学年
     * @return 字段行，含 profile 所属学生
     */
    List<StudentFieldRow> selectStudentFields(@Param("studentIds") List<Long> studentIds,
                                              @Param("fieldCodes") List<String> fieldCodes,
                                              @Param("academicYear") String academicYear);
}
