package com.college.growth.mentor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 归档后追加 MENTOR 更正记录（不覆盖原文）。 */
@Data
public class MentorCorrectionDTO {

    @NotNull(message = "原记录 ID 不能为空")
    private Long noteId;

    /** 学年；为空取当前学年。 */
    private String academicYear;

    @NotBlank(message = "更正原因不能为空")
    @Size(min = 5, max = 255, message = "更正原因 5-255 字")
    private String reason;

    @NotBlank(message = "更正内容不能为空")
    @Size(max = 1000, message = "更正内容最多 1000 字")
    private String advice;
}
