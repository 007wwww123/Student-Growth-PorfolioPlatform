package com.college.growth.common.org;

/**
 * 组织域常量。
 *
 * <p>学生的班级在 SIS 接入前暂存于基本信息板块的 {@code basic.classId} 字段，
 * 因此这里是「取学生班级」的唯一入口标识；接入教务后改为查学生主数据，
 * 只改 {@code StudentClassService} 一处。</p>
 */
public final class OrgConstants {

    /** 班级字段编码：basic.classId，值可以是班级编码或班级名称。 */
    public static final String CLASS_FIELD_CODE = "basic.classId";

    private OrgConstants() {
    }
}
