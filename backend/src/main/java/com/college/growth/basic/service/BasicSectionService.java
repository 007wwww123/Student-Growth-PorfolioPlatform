package com.college.growth.basic.service;

import com.college.growth.basic.dto.BasicSectionSaveDTO;
import com.college.growth.basic.vo.BasicSectionVO;

/**
 * 板块一 基本信息（BASIC）业务接口。
 *
 * <p>其他板块只能通过本接口访问 基本信息 的数据，禁止直接操作其数据表。</p>
 */
public interface BasicSectionService {

    /**
     * 查询指定学生某学年的板块详情。
     *
     * @param studentId    学生 ID
     * @param academicYear 学年，如 2025-2026-1；为空时取当前学年
     * @return 板块详情（含字段权限位）
     */
    BasicSectionVO detail(Long studentId, String academicYear);

    /**
     * 保存草稿（S-1-04）。
     *
     * @param studentId 学生 ID
     * @param dto       待保存的字段值
     */
    void save(Long studentId, BasicSectionSaveDTO dto);

    /**
     * 提交板块（S-1-05）。
     *
     * @param studentId    学生 ID
     * @param academicYear 学年
     */
    void submit(Long studentId, String academicYear);
}
