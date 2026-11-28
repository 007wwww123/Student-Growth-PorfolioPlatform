package com.college.growth.life.service;

import com.college.growth.life.dto.LifeSectionSaveDTO;
import com.college.growth.life.vo.LifeSectionVO;

/**
 * 板块四 生活心理（LIFE）业务接口。
 *
 * <p>其他板块只能通过本接口访问 生活心理 的数据，禁止直接操作其数据表。</p>
 */
public interface LifeSectionService {

    /**
     * 查询指定学生某学年的板块详情。
     *
     * @param studentId    学生 ID
     * @param academicYear 学年，如 2025-2026-1；为空时取当前学年
     * @return 板块详情（含字段权限位、完成度与审核痕迹）
     */
    LifeSectionVO detail(Long studentId, String academicYear);

    /**
     * 保存草稿（S-1-04）。
     *
     * @param studentId 学生 ID
     * @param dto       待保存的字段值
     */
    void save(Long studentId, LifeSectionSaveDTO dto);

    /**
     * 提交板块（S-1-05）。
     *
     * @param studentId    学生 ID
     * @param academicYear 学年
     */
    void submit(Long studentId, String academicYear);

    /**
     * 撤回提交（S-1-09）：仅学生本人，SUBMITTED 转 DRAFT，全生命周期一次（BR-S06）。
     *
     * @param studentId    学生 ID
     * @param academicYear 学年
     */
    void withdraw(Long studentId, String academicYear);
}
