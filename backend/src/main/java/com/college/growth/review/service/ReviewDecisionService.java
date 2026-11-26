package com.college.growth.review.service;

import com.college.growth.review.dto.ReviewDecisionDTO;

/**
 * 审核结论（S-1-07），六个板块共用。
 *
 * <p>本接口只做「定位板块实例 + 交状态机执行」，审核规则（谁能审、什么状态能审、
 * 理由多长）全部在 {@link ProfileWorkflowService} 里，避免板块各写一套。
 * 六个板块陆续接入时，本接口不需要改。</p>
 */
public interface ReviewDecisionService {

    /**
     * 审核通过。
     *
     * @param sectionCode 板块编码，如 basic
     * @param studentId   学生 ID
     * @param dto         学年与审核意见
     */
    void approve(String sectionCode, Long studentId, ReviewDecisionDTO dto);

    /**
     * 审核退回。
     *
     * @param sectionCode 板块编码，如 basic
     * @param studentId   学生 ID
     * @param dto         学年与退回理由
     */
    void reject(String sectionCode, Long studentId, ReviewDecisionDTO dto);
}
