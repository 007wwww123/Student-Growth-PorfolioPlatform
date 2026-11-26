package com.college.growth.review.service;

import com.college.growth.common.result.PageResult;
import com.college.growth.review.vo.ReviewTaskVO;

/**
 * 待审列表查询（S-1-06），六个板块共用。
 *
 * <p>数据范围：辅导员只能看到自己带班学生的板块，管理员需持临时授权才能查看；
 * 越权一律返回 {@code EC-40302}，不返回「空列表」——空列表会被误读成「没有待办」。</p>
 */
public interface ReviewTaskService {

    /**
     * 分页查询待审板块。
     *
     * @param sectionCode  板块编码，空表示六个板块全查
     * @param status       状态，空默认 SUBMITTED
     * @param classId      班级编码，空表示不限
     * @param academicYear 学年，空取当前学年
     * @param pageNum      页码，从 1 开始
     * @param pageSize     每页条数，1–100
     * @return 分页结果
     */
    PageResult<ReviewTaskVO> listTasks(String sectionCode, String status, String classId,
                                       String academicYear, Long pageNum, Long pageSize);
}
