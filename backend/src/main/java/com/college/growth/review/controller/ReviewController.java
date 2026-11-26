package com.college.growth.review.controller;

import com.college.growth.common.result.PageResult;
import com.college.growth.common.result.Result;
import com.college.growth.review.common.ReviewConstants;
import com.college.growth.review.dto.ReviewDecisionDTO;
import com.college.growth.review.service.ReviewDecisionService;
import com.college.growth.review.service.ReviewTaskService;
import com.college.growth.review.vo.ReviewTaskVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 档案审核接口（S-1-06 / S-1-07），六个板块共用一套。
 *
 * <p>路径刻意不放在 {@code /sections/basic} 之下：审核是跨板块能力，路径带板块参数即可，
 * 否则六个板块要各复制一遍审核接口。板块差异只体现为 {@code sectionCode} 参数。</p>
 *
 * <p>骨架期当前用户仍由请求头注入（{@code X-User-Id} / {@code X-User-Role}），
 * 不是鉴权手段；接入统一认证后由会话决定辅导员身份。</p>
 */
@RestController
@RequestMapping(ReviewConstants.API_BASE_PATH)
public class ReviewController {

    private final ReviewTaskService reviewTaskService;

    private final ReviewDecisionService reviewDecisionService;

    public ReviewController(ReviewTaskService reviewTaskService,
                            ReviewDecisionService reviewDecisionService) {
        this.reviewTaskService = reviewTaskService;
        this.reviewDecisionService = reviewDecisionService;
    }

    /** 待审列表（S-1-06）：辅导员只看本班；管理员需持临时授权。 */
    @GetMapping("/tasks")
    public Result<PageResult<ReviewTaskVO>> tasks(
            @RequestParam(required = false) String sectionCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String classId,
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) Long pageNum,
            @RequestParam(required = false) Long pageSize) {
        return Result.success(reviewTaskService.listTasks(sectionCode, status, classId,
                academicYear, pageNum, pageSize));
    }

    /** 审核通过（S-1-07 / BR-S03）：仅该班现任辅导员，意见可空。 */
    @PostMapping("/{code}/{studentId}/approve")
    public Result<Void> approve(@PathVariable("code") String sectionCode,
                                @PathVariable Long studentId,
                                @Valid @RequestBody(required = false) ReviewDecisionDTO dto) {
        reviewDecisionService.approve(sectionCode, studentId, dto);
        return Result.success();
    }

    /** 审核退回（S-1-07 / BR-S04）：理由必填且不少于 10 字，学生可见。 */
    @PostMapping("/{code}/{studentId}/reject")
    public Result<Void> reject(@PathVariable("code") String sectionCode,
                               @PathVariable Long studentId,
                               @Valid @RequestBody(required = false) ReviewDecisionDTO dto) {
        reviewDecisionService.reject(sectionCode, studentId, dto);
        return Result.success();
    }
}
