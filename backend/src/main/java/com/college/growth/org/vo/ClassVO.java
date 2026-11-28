package com.college.growth.org.vo;

import lombok.Data;

/** 班级视图（含学院/专业名称、辅导员、学生数）。 */
@Data
public class ClassVO {

    private Long id;

    private String classCode;

    private String className;

    private Long collegeId;

    private String collegeName;

    private Long majorId;

    private String majorName;

    private Integer gradeYear;

    private Long counselorUserId;

    private String counselorName;

    private Integer status;

    /** 在读学生数。 */
    private Integer studentCount;
}
