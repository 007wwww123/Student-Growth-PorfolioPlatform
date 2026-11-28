package com.college.growth.org.vo;

import com.college.growth.auth.vo.UserVO;
import java.util.List;
import lombok.Data;

/** 组织架构下拉数据：学院 / 专业 / 班级 / 辅导员。 */
@Data
public class OrgOptionsVO {

    private List<CollegeVO> colleges;

    private List<MajorVO> majors;

    private List<ClassVO> classes;

    /** 可选辅导员（角色 COU、状态启用）。 */
    private List<UserVO> counselors;
}
