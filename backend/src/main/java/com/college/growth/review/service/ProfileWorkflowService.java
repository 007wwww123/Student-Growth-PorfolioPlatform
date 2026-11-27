package com.college.growth.review.service;

import com.college.growth.common.profile.entity.ArcProfile;

/**
 * 公共档案状态机（BR-S01 – BR-S11）。
 *
 * <p>六个板块的提交、撤回、审核通过、审核退回全部走本接口，板块自身不再直接改
 * {@code arc_profile.status}——否则「谁能改、什么状态能改、日志写几条」会出现六份实现。
 * 板块只负责「内容是否齐全、字段是否合法」这类板块级校验，校验通过后调用本接口流转。</p>
 *
 * <p>所有方法都要求调用方已在事务中（{@code @Transactional} 传播），
 * 状态变更与两条日志必须同生共死（BR-S11）。</p>
 */
public interface ProfileWorkflowService {

    /**
     * 提交：DRAFT / RETURNED 转 SUBMITTED（BR-S02 / BR-S05）。
     *
     * @param profile 板块实例
     * @param reason  提交说明，写日志用；为空时用默认文案
     */
    void submit(ArcProfile profile, String reason);

    /**
     * 撤回：SUBMITTED 转 DRAFT，仅学生本人、全生命周期一次（BR-S06）。
     *
     * @param profile 板块实例
     */
    void withdraw(ArcProfile profile);

    /**
     * 审核通过：SUBMITTED 转 APPROVED，仅该班现任辅导员（BR-S03）。
     *
     * @param profile 板块实例
     * @param comment 审核意见，可空
     */
    void approve(ArcProfile profile, String comment);

    /**
     * 审核退回：SUBMITTED 转 RETURNED，理由不少于 10 字（BR-S04）。
     *
     * @param profile 板块实例
     * @param reason  退回理由，必填
     */
    void reject(ArcProfile profile, String reason);

    /**
     * 重算「六板块是否全部完成」标记（BR-S10）。
     *
     * <p>提交、撤回、审核通过、退回后调用；只更新派生标记，不参与乐观锁。
     * 判定口径与写库动作委托给 {@code CompletionService}，状态机只决定「什么时候重算」。</p>
     *
     * @param studentId    学生 ID
     * @param academicYear 学年
     */
    void refreshCompleted(Long studentId, String academicYear);
}
