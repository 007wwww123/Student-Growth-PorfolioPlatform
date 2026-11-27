package com.college.growth.enroll.vo;

import java.util.List;
import lombok.Data;

/**
 * 批量建档结果汇总（S-1-01）。
 *
 * <p>重复执行必须是安全的：已存在的板块一律跳过、不覆盖（EX-01），
 * 因此同一个班级点第二遍会得到 {@code createdProfiles = 0}、{@code skippedStudents = 总人数}。
 * 前端据此提示「已建档，无需重复操作」，而不是报错。</p>
 */
@Data
public class BatchCreateResultVO {

    private String academicYear;

    /** 本次处理的班级编码。 */
    private List<String> classCodes;

    /** 选中班级的在读学生总数。 */
    private int totalStudents;

    /** 本次新建了至少一个板块的学生数。 */
    private int createdStudents;

    /** 六板块均已存在、整人跳过的学生数。 */
    private int skippedStudents;

    /** 本次新建的板块实例总数。 */
    private int createdProfiles;

    /** 已存在并跳过的板块实例总数。 */
    private int existingProfiles;

    /** 逐学生结果，便于前端展开核对。 */
    private List<StudentEnrollItemVO> students;
}
