package com.college.growth.career.service;

import com.college.growth.career.dto.CareerSectionSaveDTO;
import com.college.growth.career.vo.CareerSectionVO;

/**
 * 板块五 职业规划（CAREER）业务接口。
 *
 * <p>其他板块只能通过本接口访问 职业规划 的数据，禁止直接操作其数据表。</p>
 */
public interface CareerSectionService {

    /**
     * 查询指定学生某学年的板块详情。
     *
     * @param studentId    学生 ID
     * @param academicYear 学年，如 2025-2026-1；为空时取当前学年
     * @return 板块详情（含字段权限位）
     */
    CareerSectionVO detail(Long studentId, String academicYear);

    /**
     * 保存草稿（S-1-04）。
     *
     * @param studentId 学生 ID
     * @param dto       待保存的字段值
     */
    void save(Long studentId, CareerSectionSaveDTO dto);

    /**
     * 提交板块（S-1-05）。
     *
     * @param studentId    学生 ID
     * @param academicYear 学年
     */
    void submit(Long studentId, String academicYear);
}
