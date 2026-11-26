package com.college.growth.common.org;

/**
 * 组织域常量。
 *
 * <p>学生的班级以学生主数据 {@code stu_student.class_code} 为准；
 * {@link #CLASS_FIELD_CODE} 只作为主数据接入前的存量回退通道。
 * 取班级统一走 {@code StudentClassService}，不要各自查字段值。</p>
 */
public final class OrgConstants {

    /** 班级字段编码：basic.classId，仅在学生主数据缺失班级时回退使用。 */
    public static final String CLASS_FIELD_CODE = "basic.classId";

    private OrgConstants() {
    }
}
