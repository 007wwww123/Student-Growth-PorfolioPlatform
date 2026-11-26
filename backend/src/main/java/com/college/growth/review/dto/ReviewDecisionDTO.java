package com.college.growth.review.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 审核结论入参。
 *
 * <p>通过时用 {@code comment}（可空），退回时用 {@code reason}（必填且不少于 10 字）。
 * 长度在 Service 里按需求文档校验，以便返回 {@code EC-42203} 而不是通用的参数错误。</p>
 */
@Data
public class ReviewDecisionDTO {

    /** 学年，如 2025-2026-1；为空取当前学年。 */
    private String academicYear;

    /** 审核意见，通过时可空。 */
    @Size(max = 255, message = "审核意见不能超过 255 字")
    private String comment;

    /** 退回理由，退回时必填，不少于 10 字，学生可见。 */
    @Size(max = 255, message = "退回理由不能超过 255 字")
    private String reason;
}
