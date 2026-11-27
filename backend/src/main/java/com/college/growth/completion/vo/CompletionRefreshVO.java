package com.college.growth.completion.vo;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 完成标记重算结果。
 *
 * <p>重算是幂等的：同一份数据重复重算只会让 {@code changedStudents} 变成 0，
 * 校验「有没有被误改」时看这个数即可。</p>
 */
@Data
public class CompletionRefreshVO {

    private String academicYear;

    /** 触发方式：MANUAL 手动 / SCHEDULED 定时。 */
    private String trigger;

    /** 实际参与判定的学生数。 */
    private int checkedStudents;

    /** 重算后判定为「六板块全部完成」的学生数。 */
    private int completedStudents;

    /** 标记发生翻转（false→true 或 true→false）的学生数。 */
    private int changedStudents;

    private LocalDateTime refreshedAt;
}
