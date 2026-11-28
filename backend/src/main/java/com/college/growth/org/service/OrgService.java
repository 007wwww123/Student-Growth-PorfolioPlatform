package com.college.growth.org.service;

import com.college.growth.org.dto.ClassSaveDTO;
import com.college.growth.org.dto.CollegeSaveDTO;
import com.college.growth.org.dto.MajorSaveDTO;
import com.college.growth.org.vo.ClassVO;
import com.college.growth.org.vo.CollegeVO;
import com.college.growth.org.vo.MajorVO;
import com.college.growth.org.vo.OrgOptionsVO;
import java.util.List;

/** 组织架构维护（F-02）：学院、专业、班级与辅导员绑定。 */
public interface OrgService {

    List<CollegeVO> colleges();

    CollegeVO createCollege(CollegeSaveDTO dto);

    CollegeVO updateCollege(Long id, CollegeSaveDTO dto);

    void updateCollegeStatus(Long id, int status);

    List<MajorVO> majors(Long collegeId);

    MajorVO createMajor(MajorSaveDTO dto);

    MajorVO updateMajor(Long id, MajorSaveDTO dto);

    void updateMajorStatus(Long id, int status);

    List<ClassVO> classes(Long collegeId, Long majorId);

    ClassVO createClass(ClassSaveDTO dto);

    ClassVO updateClass(Long id, ClassSaveDTO dto);

    ClassVO assignCounselor(Long id, Long counselorUserId);

    void updateClassStatus(Long id, int status);

    OrgOptionsVO options();
}
