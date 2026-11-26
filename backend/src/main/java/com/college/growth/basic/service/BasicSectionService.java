package com.college.growth.basic.service;

import com.college.growth.basic.dto.BasicSectionSaveDTO;
import com.college.growth.basic.vo.BasicSectionVO;
import com.college.growth.basic.vo.SensitiveFieldVO;
import com.college.growth.common.profile.vo.PendingFieldVO;

/**
 * 板块一 基本信息（BASIC）业务接口。
 *
 * <p>其他板块只能通过本接口访问 基本信息 的数据，禁止直接操作其数据表。
 * 状态流转（提交 / 撤回）与待确认区处理不在本模块内实现，而是委托公共审核模块，
 * 保证六个板块的状态机只有一份。</p>
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
     * 提交板块（S-1-05）：板块级校验通过后交公共状态机流转。
     *
     * @param studentId    学生 ID
     * @param academicYear 学年
     */
    void submit(Long studentId, String academicYear);

    /**
     * 撤回提交（S-1-09 / BR-S06）：仅学生本人，全生命周期一次。
     *
     * @param studentId    学生 ID
     * @param academicYear 学年
     */
    void withdraw(Long studentId, String academicYear);

    /**
     * 确认待确认值（BR-F06）：仅该班现任辅导员。
     *
     * @param studentId    学生 ID
     * @param fieldCode    字段编码，如 basic.phone
     * @param academicYear 学年
     * @return 处理结果（敏感字段为脱敏值）
     */
    PendingFieldVO confirmField(Long studentId, String fieldCode, String academicYear);

    /**
     * 驳回待确认值（BR-F06）：丢弃新值，保留旧值。
     *
     * @param studentId    学生 ID
     * @param fieldCode    字段编码
     * @param academicYear 学年
     */
    void rejectField(Long studentId, String fieldCode, String academicYear);

    /**
     * 查看敏感字段明文（BR-D05）：仅辅导员或持临时授权的管理员，调用即写审计。
     *
     * @param studentId    学生 ID
     * @param fieldCode    字段编码，如 basic.idCard
     * @param academicYear 学年
     * @return 明文值
     */
    SensitiveFieldVO reveal(Long studentId, String fieldCode, String academicYear);
}
