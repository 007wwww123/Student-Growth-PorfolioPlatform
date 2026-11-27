package com.college.growth.archive.vo;

import java.util.List;
import lombok.Data;

/**
 * 单个学生的归档结果。
 *
 * <p>{@code missingSections} 是给人看的板块名清单，前端直接渲染为「学业发展、职业规划」，
 * 不需要再去查字典；码值在 {@link ArchiveSectionStatusVO} 里。</p>
 */
@Data
public class ArchiveStudentResultVO {

    private Long studentId;

    private String studentNo;

    private String studentName;

    private String className;

    /** ARCHIVED / SKIPPED / INCOMPLETE。 */
    private String status;

    /** 未完成的板块名称，仅 INCOMPLETE 时有值。 */
    private List<String> missingSections;
}
