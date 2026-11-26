package com.college.growth.common.profile.service;

import com.college.growth.common.profile.vo.PendingFieldVO;

/**
 * 待确认区处理（BR-F06），六个板块共用。
 *
 * <p>STU_CFM 类字段由学生修改后进 {@code pending_*}：确认则替换正式值，
 * 驳回则丢弃新值、保留旧值。两种处理都要写变更日志与审计，
 * 且只有该班现任辅导员可以操作。</p>
 */
public interface PendingFieldService {

    /**
     * 确认待确认值：新值生效、旧值被覆盖。
     *
     * @param studentId    学生 ID
     * @param sectionCode  板块编码，如 BASIC
     * @param fieldCode    字段编码，如 basic.phone
     * @param academicYear 学年
     * @return 处理结果（敏感字段为脱敏值）
     */
    PendingFieldVO confirm(Long studentId, String sectionCode, String fieldCode, String academicYear);

    /**
     * 驳回待确认值：丢弃新值，对外仍返回旧值。
     *
     * @param studentId    学生 ID
     * @param sectionCode  板块编码
     * @param fieldCode    字段编码
     * @param academicYear 学年
     */
    void reject(Long studentId, String sectionCode, String fieldCode, String academicYear);
}
