package com.college.growth.archive.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/**
 * 批量归档入参（S-1-11）。
 *
 * <p>入参是「待归档学生清单」而非「整个班」：毕业名单由辅导员核对后提交，
 * 名单里的人必须全部满足六板块通过，否则整批拒绝（EX-12）。
 * 用学生 ID 而不是学号，是因为学号在库里只是唯一键，档案按学生 ID 关联。</p>
 */
@Data
public class ArchiveBatchDTO {

    /** 学年，如 2025-2026-1；为空取当前学年。 */
    private String academicYear;

    /** 待归档学生 ID 集合，最多 200 人。 */
    @NotEmpty(message = "请至少选择一名学生")
    @Size(max = 200, message = "单次最多归档 200 名学生")
    private List<Long> studentIds;
}
