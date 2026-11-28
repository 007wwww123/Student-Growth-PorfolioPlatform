package com.college.growth.ledger.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 班级台账查询（SQL 见 {@code resources/mapper/ledger/LedgerMapper.xml}）。
 *
 * <p>明细列表刻意手写 count 与 list 两条语句：列表的 FROM 是 {@code arc_profile}
 * 与「未建档学生」的 UNION ALL，MyBatis-Plus 的分页插件会自己派生 count SQL，
 * 在 UNION 场景下容易数错行（尤其是未建档行），而 AC-020 要求「筛出几行，计数就是几」。
 * 两条语句共用同一段 WHERE 片段，从结构上保证计数与行集同源。</p>
 */
@Mapper
public interface LedgerMapper {

    /**
     * 按班级聚合：在册学生、建档学生、完成学生与各状态板块数。
     *
     * @param academicYear 学年
     * @param counselorId  辅导员 ID，非空时只统计其带班（含班级状态为启用）
     * @return 按班级分组的统计行；无学生时返回空列表
     */
    List<LedgerClassStatRow> selectClassStats(@Param("academicYear") String academicYear,
                                              @Param("counselorId") Long counselorId);

    /**
     * 统计台账明细行数（AC-020：与列表使用同一过滤条件）。
     *
     * @param academicYear 学年
     * @param counselorId  辅导员 ID，非空时只统计其带班
     * @param classCode    班级编码，为空表示不限；{@code 未分班} 视为一个合法桶
     * @param status       状态，为空表示全部；UNARCHIVED 表示未建档行
     * @param keyword      姓名或学号模糊匹配，为空表示不限
     * @return 过滤后的总行数
     */
    long countTasks(@Param("academicYear") String academicYear,
                    @Param("counselorId") Long counselorId,
                    @Param("classCode") String classCode,
                    @Param("status") String status,
                    @Param("keyword") String keyword);

    /**
     * 分页查询台账明细。
     *
     * @param academicYear 学年
     * @param counselorId  辅导员 ID，非空时只查其带班
     * @param classCode    班级编码，为空表示不限
     * @param status       状态，为空表示全部
     * @param keyword      姓名或学号模糊匹配
     * @param offset       偏移量，从 0 开始
     * @param limit        每页条数
     * @return 明细行
     */
    List<LedgerTaskRow> selectTaskPage(@Param("academicYear") String academicYear,
                                       @Param("counselorId") Long counselorId,
                                       @Param("classCode") String classCode,
                                       @Param("status") String status,
                                       @Param("keyword") String keyword,
                                       @Param("offset") long offset,
                                       @Param("limit") long limit);
}
