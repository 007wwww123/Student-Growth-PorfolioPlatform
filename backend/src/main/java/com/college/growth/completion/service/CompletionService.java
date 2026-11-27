package com.college.growth.completion.service;

import com.college.growth.common.profile.entity.ArcProfile;
import com.college.growth.completion.vo.CompletionRefreshVO;
import com.college.growth.completion.vo.CompletionStatusVO;
import java.util.List;

/**
 * 完成标记判定与刷新（BR-S10：「六板块全部完成」才算档案完成）。
 *
 * <p>这是全平台唯一一份完成判定实现。状态机在每次流转后调 {@link #refresh}，
 * 定时任务调 {@link #refreshAcademicYear}，管理端手动重算调 {@link #manualRefresh}，
 * 三种入口共用同一套口径，避免「审核过了但标记没翻」这类只在某个入口出现的问题。</p>
 *
 * <p>标记是派生数据，不参与乐观锁：它不随用户内容变化，并发查看不应冲突。</p>
 */
public interface CompletionService {

    /**
     * 纯判定：给定某学生某学年的全部板块实例，是否六板块全部完成。
     *
     * <p>完成口径为 {@code APPROVED} 与 {@code LOCKED} 两种状态——
     * 已归档是「已完成」之后的终态，不能因为归档把它算回未完成。</p>
     *
     * @param profiles 某学生某学年的板块实例（可含重复板块，按编码去重）
     * @return 六个板块按编码去重后全部完成才为 true
     */
    boolean isCompleted(List<ArcProfile> profiles);

    /**
     * 重算并落库某学生的完成标记。
     *
     * <p>只在标记发生变化时写库，因此可以被状态机在每个流转节点安全调用，
     * 不会因为一次提交就无谓地刷新 {@code updated_at}。</p>
     *
     * @return 标记是否发生变化
     */
    boolean refresh(Long studentId, String academicYear);

    /**
     * 读取某学生某学年的完成标记与逐板块状态。
     *
     * <p>数据范围：学生只能看本人，辅导员只能看本班，管理员需持临时授权。</p>
     */
    CompletionStatusVO statusOf(Long studentId, String academicYear);

    /**
     * 管理端手动重算。
     *
     * <p>{@code studentId} 为空表示整学年重算，此时只有持临时授权的管理员可以调用；
     * 辅导员只能重算本班单个学生。每次调用写一条审计日志。</p>
     */
    CompletionRefreshVO manualRefresh(Long studentId, String academicYear);

    /**
     * 整学年重算，供定时任务调用。
     *
     * <p>定时任务没有请求上下文，因此不写审计日志，只打应用日志。</p>
     */
    CompletionRefreshVO refreshAcademicYear(String academicYear, String trigger);
}
