package com.college.growth.ledger.service;

import com.college.growth.common.result.PageResult;
import com.college.growth.ledger.vo.LedgerClassVO;
import com.college.growth.ledger.vo.LedgerRowVO;
import java.util.List;

/**
 * 班级台账（F-07 / AC-020）。
 *
 * <p>两个查询共用同一套数据范围解析：辅导员只看所带班级，
 * 管理员必须持临时授权，学生与其他角色一律拒绝。
 * 前端不传「看谁的数据」，传了也不作数。</p>
 */
public interface LedgerService {

    /**
     * 班级完成度汇总。
     *
     * @param academicYear 学年，为空取当前学年
     * @return 每个班一行；无学生时也返回班级列表（各项为 0）
     */
    List<LedgerClassVO> listClasses(String academicYear);

    /**
     * 台账明细（学生 × 板块，含未建档行）。
     *
     * @param academicYear 学年，为空取当前学年
     * @param classCode    班级编码，为空表示不限；越权班级返回 EC-40302，班级不存在返回 EC-40003
     * @param status       ALL（默认）/ DRAFT / SUBMITTED / RETURNED / APPROVED / LOCKED / UNARCHIVED
     * @param keyword      姓名或学号模糊匹配
     * @param pageNum      页码，从 1 开始
     * @param pageSize     每页条数，1–100
     * @return 分页结果；total 与筛选条件同源，保证与行数一致
     */
    PageResult<LedgerRowVO> listTasks(String academicYear, String classCode, String status,
                                      String keyword, Long pageNum, Long pageSize);
}
