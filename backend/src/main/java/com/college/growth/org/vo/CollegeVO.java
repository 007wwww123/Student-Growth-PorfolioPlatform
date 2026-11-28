package com.college.growth.org.vo;

import lombok.Data;

/** 学院视图。 */
@Data
public class CollegeVO {

    private Long id;

    private String collegeCode;

    private String collegeName;

    private Integer status;

    /** 下属专业数。 */
    private Integer majorCount;
}
