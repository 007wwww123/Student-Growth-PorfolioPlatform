package com.college.growth.enroll.vo;

import lombok.Data;

/**
 * 可建档班级行：一名辅导员带班的「班级 × 学年」建档概况。
 *
 * <p>{@code enrolledStudentCount} 的口径是「六个板块实例都已存在」，
 * 不要求已填写或已通过——本页只负责生成空档案，填写进度由总览页看板展示。</p>
 */
@Data
public class ClassEnrollVO {

    /** 班级编码。 */
    private String classCode;

    /** 班级名称，如 软件2401。 */
    private String className;

    /** 专业名称。 */
    private String majorName;

    /** 年级，四位年份。 */
    private Integer gradeYear;

    /** 本班在读学生数（取学生主数据，不含休学、退学）。 */
    private int studentCount;

    /** 六板块实例齐全的学生数。 */
    private int enrolledStudentCount;

    /** 还有板块缺失的学生数。 */
    private int missingStudentCount;

    /** 是否已全部建档；为 true 时按钮置灰，服务端仍会按「已存在跳过」兜底。 */
    private boolean enrolled;
}
