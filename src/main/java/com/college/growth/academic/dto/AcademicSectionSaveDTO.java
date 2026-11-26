package com.college.growth.academic.dto;

import java.util.List;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 板块保存入参：学业发展（ACADEMIC）。
 *
 * <p>字段校验以需求文档 4.3 为准，规则标识存放在 {@code arc_field_def.validator}，
 * 由校验器注册表按标识执行（BR-F02），此处仅保留接口层的长度与必填兜底。</p>
 */
@Data
public class AcademicSectionSaveDTO {

    /** 学年，如 2025-2026-1。 */
    @Size(max = 16, message = "学年长度不能超过 16")
    private String academicYear;

    /** 短期学业目标，20–200 字（academic.goalShort）。 */
    @Size(min = 20, max = 200, message = "短期学业目标为 20–200 字")
    private String goalShort;

    /** 长期发展目标，20–300 字（academic.goalLong）。 */
    @Size(max = 300, message = "长期发展目标不超过 300 字")
    private String goalLong;

    /** 优势科目，最多 3 项（academic.strongSubjects）。 */
    @Size(max = 3, message = "优势科目最多 3 项")
    private List<String> strongSubjects;

    /** 待提升科目，最多 3 项，不得与优势科目重复（academic.weakSubjects）。 */
    @Size(max = 3, message = "待提升科目最多 3 项")
    private List<String> weakSubjects;

    /** 学科能力认定，辅导员维护，学生只读（academic.abilityNote）。 */
    @Size(max = 300, message = "学科能力认定不超过 300 字")
    private String abilityNote;
}
