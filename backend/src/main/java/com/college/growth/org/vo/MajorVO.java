package com.college.growth.org.vo;

import lombok.Data;

/** 专业视图。 */
@Data
public class MajorVO {

    private Long id;

    private Long collegeId;

    private String collegeName;

    private String majorCode;

    private String majorName;

    private Integer status;
}
