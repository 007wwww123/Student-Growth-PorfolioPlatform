package com.college.growth.ledger.controller;

import com.college.growth.common.result.PageResult;
import com.college.growth.common.result.Result;
import com.college.growth.ledger.common.LedgerConstants;
import com.college.growth.ledger.service.LedgerService;
import com.college.growth.ledger.vo.LedgerClassVO;
import com.college.growth.ledger.vo.LedgerRowVO;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 班级台账接口（F-07 / AC-020）。
 *
 * <p>两个端点解决两个问题：{@code /classes} 给顶部班级完成度汇总，
 * {@code /tasks} 给可筛选、可分页的明细。不把两者塞进一个响应，
 * 是因为汇总与明细的筛选维度不同（汇总只按学年，明细还有班级、状态、关键字）。</p>
 *
 * <p>数据范围由服务端按登录身份判定：辅导员只看所带班级，管理员需持临时授权。
 * 前端不传「看谁的数据」这类参数，传了也不作数。</p>
 */
@RestController
@RequestMapping(LedgerConstants.API_BASE_PATH)
public class LedgerController {

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    /** 班级完成度汇总：辅导员看带班，管理员（持授权）看全部。 */
    @GetMapping("/classes")
    public Result<List<LedgerClassVO>> classes(@RequestParam(required = false) String academicYear) {
        return Result.success(ledgerService.listClasses(academicYear));
    }

    /**
     * 台账明细。
     *
     * @param status 允许 ALL / DRAFT / SUBMITTED / RETURNED / APPROVED / LOCKED / UNARCHIVED，
     *               默认 ALL；非法值返回 EC-40003
     */
    @GetMapping("/tasks")
    public Result<PageResult<LedgerRowVO>> tasks(
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) String classCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long pageNum,
            @RequestParam(required = false) Long pageSize) {
        return Result.success(ledgerService.listTasks(academicYear, classCode, status,
                keyword, pageNum, pageSize));
    }
}
