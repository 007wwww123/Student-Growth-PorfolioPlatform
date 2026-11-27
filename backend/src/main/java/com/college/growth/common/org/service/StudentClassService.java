package com.college.growth.common.org.service;

import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.result.ResultCode;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.college.growth.common.enums.SectionCodeEnum;
import com.college.growth.common.org.OrgConstants;
import com.college.growth.common.org.entity.OrgClass;
import com.college.growth.common.org.mapper.OrgClassMapper;
import com.college.growth.common.profile.entity.ArcProfile;
import com.college.growth.common.profile.entity.ArcProfileItem;
import com.college.growth.common.profile.mapper.ArcProfileItemMapper;
import com.college.growth.common.profile.mapper.ArcProfileMapper;
import com.college.growth.common.student.service.StudentMasterService;
import com.college.growth.common.util.TextUtils;
import org.springframework.stereotype.Service;

/**
 * 学生班级与审核权解析。
 *
 * <p>班级有两条来源，按优先级取：</p>
 * <ol>
 *   <li><b>学生主数据</b> {@code stu_student.class_code}（首选）：学籍上的班级，权威且不依赖学生填表；</li>
 *   <li><b>基本信息板块</b> {@code basic.classId} 字段值（回退）：主数据接入前建档的历史档案只有这一份，
 *       切源时不能把他们的审核权切断。</li>
 * </ol>
 *
 * <p>回退分支只服务于存量数据，新档案一律以主数据为准。等历史档案补齐后可删除回退，
 * 届时审核模块与六个板块仍不用动——这就是把间接关系收在一处的意义。</p>
 */
@Service
public class StudentClassService {

    private final ArcProfileMapper profileMapper;

    private final ArcProfileItemMapper itemMapper;

    private final OrgClassMapper orgClassMapper;

    private final StudentMasterService studentMasterService;

    public StudentClassService(ArcProfileMapper profileMapper,
                               ArcProfileItemMapper itemMapper,
                               OrgClassMapper orgClassMapper,
                               StudentMasterService studentMasterService) {
        this.profileMapper = profileMapper;
        this.itemMapper = itemMapper;
        this.orgClassMapper = orgClassMapper;
        this.studentMasterService = studentMasterService;
    }

    public void requireStudentScope(Long studentId, String year, CurrentUser user) {
        if (studentId != null && studentId > 0 && (user.isStudent() && studentId.equals(user.userId())
                || user.isCounselor() && isCounselorOf(studentId, year, user.userId())
                || user.isAdminWithGrant())) {
            return;
        }
        throw new BusinessException(ResultCode.DATA_SCOPE_DENIED, "无权访问该学生的档案");
    }

    /**
     * 取学生某学年所属班级。
     *
     * @return 未建档、未填班级或班级已停用时返回 null
     */
    public OrgClass findClass(Long studentId, String academicYear) {
        String classValue = findClassValue(studentId, academicYear);
        if (TextUtils.isBlank(classValue)) {
            return null;
        }
        OrgClass byCode = orgClassMapper.selectOne(Wrappers.<OrgClass>lambdaQuery()
                .eq(OrgClass::getStatus, 1)
                .eq(OrgClass::getClassCode, classValue));
        if (byCode != null) {
            return byCode;
        }
        var byName = orgClassMapper.selectList(Wrappers.<OrgClass>lambdaQuery()
                .eq(OrgClass::getStatus, 1)
                .eq(OrgClass::getClassName, classValue)
                .last("limit 2"));
        return byName.size() == 1 ? byName.get(0) : null;
    }

    /**
     * 取学生某学年所属班级的编码，供待审列表按班筛选。
     *
     * <p>先取学生主数据，命中即返回；主数据缺失或未分班时才回退到 {@code basic.classId} 字段值，
     * 保证主数据接入前后的档案都能正确判定审核权。</p>
     */
    public String findClassValue(Long studentId, String academicYear) {
        String fromMaster = studentMasterService.findClassCode(studentId);
        if (!TextUtils.isBlank(fromMaster)) {
            return fromMaster.trim();
        }
        ArcProfile basic = profileMapper.selectOne(Wrappers.<ArcProfile>lambdaQuery()
                .eq(ArcProfile::getStudentId, studentId)
                .eq(ArcProfile::getSectionCode, SectionCodeEnum.BASIC.name())
                .eq(ArcProfile::getAcademicYear, academicYear)
                .last("limit 1"));
        if (basic == null) {
            return null;
        }
        ArcProfileItem item = itemMapper.selectOne(Wrappers.<ArcProfileItem>lambdaQuery()
                .eq(ArcProfileItem::getProfileId, basic.getId())
                .eq(ArcProfileItem::getFieldCode, OrgConstants.CLASS_FIELD_CODE)
                .last("limit 1"));
        return item == null ? null : item.getValueText();
    }

    /**
     * 当前用户是否该学生的现任辅导员（BR-S03：审核权按班级判定）。
     *
     * <p>无法判定班级时一律返回 false——宁可拒绝审核，也不能让非本班辅导员放行。</p>
     */
    public boolean isCounselorOf(Long studentId, String academicYear, Long userId) {
        if (userId == null || userId <= 0) {
            return false;
        }
        OrgClass orgClass = findClass(studentId, academicYear);
        return orgClass != null && userId.equals(orgClass.getCounselorUserId());
    }
}
