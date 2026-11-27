package com.college.growth.enroll.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/**
 * 批量建档入参（S-1-01）。
 *
 * <p>输入只要「学年 + 班级」，学生清单由服务端按学生主数据 {@code stu_student} 取，
 * 不在请求体里传学生 ID：辅导员手上只有班级名单，传 ID 等于要求前端先查一遍，
 * 既多一次请求，也让「浏览器能建档谁」变成了权限漏洞。</p>
 */
@Data
public class BatchCreateDTO {

    /** 学年，如 2025-2026-1；为空取当前学年。 */
    private String academicYear;

    /** 班级编码集合，与 {@code org_class.class_code} 一致。 */
    @NotEmpty(message = "请至少选择一个班级")
    @Size(max = 20, message = "单次最多选择 20 个班级")
    private List<String> classCodes;
}
