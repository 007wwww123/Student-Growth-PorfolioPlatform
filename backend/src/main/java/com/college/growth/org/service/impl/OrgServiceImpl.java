package com.college.growth.org.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.college.growth.auth.entity.SysUser;
import com.college.growth.auth.mapper.SysUserMapper;
import com.college.growth.auth.vo.UserVO;
import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.context.UserContext;
import com.college.growth.common.enums.UserRoleEnum;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.log.service.AuditLogService;
import com.college.growth.common.org.entity.OrgClass;
import com.college.growth.common.org.entity.OrgCollege;
import com.college.growth.common.org.entity.OrgMajor;
import com.college.growth.common.org.mapper.OrgClassMapper;
import com.college.growth.common.org.mapper.OrgCollegeMapper;
import com.college.growth.common.org.mapper.OrgMajorMapper;
import com.college.growth.common.result.ResultCode;
import com.college.growth.common.student.entity.StuStudent;
import com.college.growth.common.student.mapper.StuStudentMapper;
import com.college.growth.common.util.TextUtils;
import com.college.growth.org.dto.ClassSaveDTO;
import com.college.growth.org.dto.CollegeSaveDTO;
import com.college.growth.org.dto.MajorSaveDTO;
import com.college.growth.org.service.OrgService;
import com.college.growth.org.vo.ClassVO;
import com.college.growth.org.vo.CollegeVO;
import com.college.growth.org.vo.MajorVO;
import com.college.growth.org.vo.OrgOptionsVO;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 组织架构维护实现。
 *
 * <p>三条约束：</p>
 * <ol>
 *   <li>停用有下级的节点会被拒绝（学院有专业、专业有班级、班级有在读学生），
 *       避免留下「有学生但班级不可见」的孤儿数据；</li>
 *   <li>班级的 {@code major_name} 始终由专业带出，保持 {@code basic.majorId} 的推导口径；</li>
 *   <li>辅导员必须是启用状态的 COU 账号，绑定后立即决定该班审核权。</li>
 * </ol>
 */
@Service
public class OrgServiceImpl implements OrgService {

    private static final Logger log = LoggerFactory.getLogger(OrgServiceImpl.class);

    private static final int ENABLED = 1;

    private static final int DISABLED = 0;

    private final OrgCollegeMapper collegeMapper;

    private final OrgMajorMapper majorMapper;

    private final OrgClassMapper classMapper;

    private final SysUserMapper userMapper;

    private final StuStudentMapper studentMapper;

    private final AuditLogService auditLogService;

    public OrgServiceImpl(OrgCollegeMapper collegeMapper, OrgMajorMapper majorMapper,
                          OrgClassMapper classMapper, SysUserMapper userMapper,
                          StuStudentMapper studentMapper, AuditLogService auditLogService) {
        this.collegeMapper = collegeMapper;
        this.majorMapper = majorMapper;
        this.classMapper = classMapper;
        this.userMapper = userMapper;
        this.studentMapper = studentMapper;
        this.auditLogService = auditLogService;
    }

    @Override
    public List<CollegeVO> colleges() {
        List<OrgCollege> colleges = collegeMapper.selectList(Wrappers.<OrgCollege>lambdaQuery()
                .orderByAsc(OrgCollege::getCollegeCode));
        Map<Long, Long> majorCounts = majorMapper.selectList(null).stream()
                .collect(Collectors.groupingBy(OrgMajor::getCollegeId, Collectors.counting()));
        List<CollegeVO> result = new ArrayList<>(colleges.size());
        for (OrgCollege college : colleges) {
            result.add(toVO(college, majorCounts.getOrDefault(college.getId(), 0L).intValue()));
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CollegeVO createCollege(CollegeSaveDTO dto) {
        requireAdmin();
        String code = requireCode(dto.getCollegeCode(), "学院编码");
        if (findCollegeByCode(code, null) != null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "学院编码已存在：" + code);
        }
        OrgCollege college = new OrgCollege();
        college.setCollegeCode(code);
        college.setCollegeName(dto.getCollegeName().trim());
        college.setStatus(ENABLED);
        collegeMapper.insert(college);
        audit(AuditLogService.ACTION_CREATE, college.getId(), "新增学院：" + college.getCollegeName());
        log.info("新增学院 id={} code={}", college.getId(), code);
        return toVO(college, 0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CollegeVO updateCollege(Long id, CollegeSaveDTO dto) {
        requireAdmin();
        OrgCollege college = requireCollege(id);
        String code = requireCode(dto.getCollegeCode(), "学院编码");
        OrgCollege sameCode = findCollegeByCode(code, id);
        if (sameCode != null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "学院编码已存在：" + code);
        }
        college.setCollegeCode(code);
        college.setCollegeName(dto.getCollegeName().trim());
        collegeMapper.updateById(college);
        audit(AuditLogService.ACTION_UPDATE, id, "修改学院：" + college.getCollegeName());
        return toVO(college, (int) majorMapper.selectCount(
                Wrappers.<OrgMajor>lambdaQuery().eq(OrgMajor::getCollegeId, id)).longValue());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCollegeStatus(Long id, int status) {
        requireAdmin();
        OrgCollege college = requireCollege(id);
        if (status == DISABLED) {
            long majors = majorMapper.selectCount(Wrappers.<OrgMajor>lambdaQuery()
                    .eq(OrgMajor::getCollegeId, id).eq(OrgMajor::getStatus, ENABLED));
            if (majors > 0) {
                throw new BusinessException(ResultCode.STATE_CONFLICT,
                        "学院下仍有 " + majors + " 个启用专业，请先停用专业");
            }
        }
        college.setStatus(status);
        collegeMapper.updateById(college);
        audit(status == ENABLED ? AuditLogService.ACTION_ENABLE : AuditLogService.ACTION_DISABLE,
                id, "学院状态：" + status);
    }

    @Override
    public List<MajorVO> majors(Long collegeId) {
        List<OrgMajor> majors = majorMapper.selectList(Wrappers.<OrgMajor>lambdaQuery()
                .eq(collegeId != null, OrgMajor::getCollegeId, collegeId)
                .orderByAsc(OrgMajor::getMajorCode));
        Map<Long, String> collegeNames = collegeNames();
        return majors.stream().map(item -> toVO(item, collegeNames)).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MajorVO createMajor(MajorSaveDTO dto) {
        requireAdmin();
        OrgCollege college = requireCollege(dto.getCollegeId());
        String code = requireCode(dto.getMajorCode(), "专业编码");
        if (findMajorByCode(code, null) != null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "专业编码已存在：" + code);
        }
        OrgMajor major = new OrgMajor();
        major.setCollegeId(college.getId());
        major.setMajorCode(code);
        major.setMajorName(dto.getMajorName().trim());
        major.setStatus(ENABLED);
        majorMapper.insert(major);
        audit(AuditLogService.ACTION_CREATE, major.getId(), "新增专业：" + major.getMajorName());
        return toVO(major, Map.of(college.getId(), college.getCollegeName()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MajorVO updateMajor(Long id, MajorSaveDTO dto) {
        requireAdmin();
        OrgMajor major = requireMajor(id);
        OrgCollege college = requireCollege(dto.getCollegeId());
        String code = requireCode(dto.getMajorCode(), "专业编码");
        if (findMajorByCode(code, id) != null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "专业编码已存在：" + code);
        }
        major.setCollegeId(college.getId());
        major.setMajorCode(code);
        major.setMajorName(dto.getMajorName().trim());
        majorMapper.updateById(major);
        // 班级冗余的专业名与学院同步，避免 basic.majorId 推导口径分叉
        classMapper.update(null, Wrappers.<OrgClass>lambdaUpdate()
                .eq(OrgClass::getMajorId, id)
                .set(OrgClass::getCollegeId, college.getId())
                .set(OrgClass::getMajorName, major.getMajorName()));
        audit(AuditLogService.ACTION_UPDATE, id, "修改专业：" + major.getMajorName());
        return toVO(major, Map.of(college.getId(), college.getCollegeName()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMajorStatus(Long id, int status) {
        requireAdmin();
        OrgMajor major = requireMajor(id);
        if (status == DISABLED) {
            long classes = classMapper.selectCount(Wrappers.<OrgClass>lambdaQuery()
                    .eq(OrgClass::getMajorId, id).eq(OrgClass::getStatus, ENABLED));
            if (classes > 0) {
                throw new BusinessException(ResultCode.STATE_CONFLICT,
                        "专业下仍有 " + classes + " 个启用班级，请先停用班级");
            }
        }
        major.setStatus(status);
        majorMapper.updateById(major);
        audit(status == ENABLED ? AuditLogService.ACTION_ENABLE : AuditLogService.ACTION_DISABLE,
                id, "专业状态：" + status);
    }

    @Override
    public List<ClassVO> classes(Long collegeId, Long majorId) {
        CurrentUser user = UserContext.get();
        List<OrgClass> classes = classMapper.selectList(Wrappers.<OrgClass>lambdaQuery()
                .eq(collegeId != null, OrgClass::getCollegeId, collegeId)
                .eq(majorId != null, OrgClass::getMajorId, majorId)
                .eq(user.isCounselor(), OrgClass::getCounselorUserId, user.userId())
                .orderByAsc(OrgClass::getClassCode));
        return assembleClasses(classes);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ClassVO createClass(ClassSaveDTO dto) {
        requireAdmin();
        String code = requireCode(dto.getClassCode(), "班级编码");
        OrgClass sameCode = classMapper.selectOne(Wrappers.<OrgClass>lambdaQuery()
                .eq(OrgClass::getClassCode, code).last("limit 1"));
        if (sameCode != null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "班级编码已存在：" + code);
        }
        OrgMajor major = requireMajor(dto.getMajorId());
        OrgClass orgClass = new OrgClass();
        apply(orgClass, dto, major);
        orgClass.setStatus(ENABLED);
        classMapper.insert(orgClass);
        audit(AuditLogService.ACTION_CREATE, orgClass.getId(), "新增班级：" + orgClass.getClassName());
        return assembleClasses(List.of(orgClass)).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ClassVO updateClass(Long id, ClassSaveDTO dto) {
        requireAdmin();
        OrgClass orgClass = requireClass(id);
        String code = requireCode(dto.getClassCode(), "班级编码");
        OrgClass sameCode = classMapper.selectOne(Wrappers.<OrgClass>lambdaQuery()
                .eq(OrgClass::getClassCode, code).ne(OrgClass::getId, id).last("limit 1"));
        if (sameCode != null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "班级编码已存在：" + code);
        }
        OrgMajor major = requireMajor(dto.getMajorId());
        Long oldCounselor = orgClass.getCounselorUserId();
        apply(orgClass, dto, major);
        classMapper.updateById(orgClass);
        audit(AuditLogService.ACTION_UPDATE, id, "修改班级：" + orgClass.getClassName());
        if (!Objects.equals(oldCounselor, orgClass.getCounselorUserId())) {
            audit(AuditLogService.ACTION_ASSIGN, id,
                    "辅导员变更：" + oldCounselor + " -> " + orgClass.getCounselorUserId());
        }
        return assembleClasses(List.of(orgClass)).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ClassVO assignCounselor(Long id, Long counselorUserId) {
        requireAdmin();
        OrgClass orgClass = requireClass(id);
        SysUser counselor = requireCounselor(counselorUserId);
        orgClass.setCounselorUserId(counselor.getId());
        classMapper.updateById(orgClass);
        audit(AuditLogService.ACTION_ASSIGN, id, "绑定辅导员：" + counselor.getDisplayName());
        return assembleClasses(List.of(orgClass)).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateClassStatus(Long id, int status) {
        requireAdmin();
        OrgClass orgClass = requireClass(id);
        if (status == DISABLED) {
            Long students = studentMapper.selectCount(Wrappers.<StuStudent>lambdaQuery()
                    .eq(StuStudent::getClassCode, orgClass.getClassCode())
                    .eq(StuStudent::getStatus, 1));
            if (students != null && students > 0) {
                throw new BusinessException(ResultCode.STATE_CONFLICT,
                        "班级下仍有 " + students + " 名在读学生，不能停用");
            }
        }
        orgClass.setStatus(status);
        classMapper.updateById(orgClass);
        audit(status == ENABLED ? AuditLogService.ACTION_ENABLE : AuditLogService.ACTION_DISABLE,
                id, "班级状态：" + status);
    }

    @Override
    public OrgOptionsVO options() {
        OrgOptionsVO vo = new OrgOptionsVO();
        vo.setColleges(colleges());
        vo.setMajors(majors(null));
        vo.setClasses(classes(null, null));
        List<SysUser> counselors = userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getRole, UserRoleEnum.COU.name())
                .eq(SysUser::getStatus, ENABLED)
                .orderByAsc(SysUser::getUsername));
        CurrentUser user = UserContext.get();
        List<UserVO> counselorVOs = counselors.stream()
                .filter(item -> !user.isCounselor() || item.getId().equals(user.userId()))
                .map(item -> new UserVO(item.getId(), item.getUsername(), item.getDisplayName(),
                        item.getRole(), UserRoleEnum.COU.getLabel(), item.getStudentId(),
                        item.getTempGrant() != null && item.getTempGrant() == 1,
                        item.getMustChangePassword() != null && item.getMustChangePassword() == 1))
                .collect(Collectors.toList());
        vo.setCounselors(counselorVOs);
        return vo;
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    private void apply(OrgClass orgClass, ClassSaveDTO dto, OrgMajor major) {
        orgClass.setClassCode(dto.getClassCode().trim());
        orgClass.setClassName(dto.getClassName().trim());
        orgClass.setCollegeId(major.getCollegeId());
        orgClass.setMajorId(major.getId());
        orgClass.setMajorName(major.getMajorName());
        orgClass.setGradeYear(dto.getGradeYear());
        if (dto.getCounselorUserId() != null) {
            orgClass.setCounselorUserId(requireCounselor(dto.getCounselorUserId()).getId());
        }
    }

    private List<ClassVO> assembleClasses(List<OrgClass> classes) {
        if (classes.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = classes.stream().map(OrgClass::getCounselorUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> counselorNames = userIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, SysUser::getDisplayName));
        Map<Long, String> collegeNames = collegeNames();
        Map<Long, String> majorNames = majorMapper.selectList(null).stream()
                .collect(Collectors.toMap(OrgMajor::getId, OrgMajor::getMajorName));
        List<String> classCodes = classes.stream().map(OrgClass::getClassCode).toList();
        Map<String, Integer> studentCounts = new HashMap<>();
        if (!classCodes.isEmpty()) {
            for (StuStudent student : studentMapper.selectList(Wrappers.<StuStudent>lambdaQuery()
                    .in(StuStudent::getClassCode, classCodes)
                    .eq(StuStudent::getStatus, 1))) {
                studentCounts.merge(student.getClassCode(), 1, Integer::sum);
            }
        }
        List<ClassVO> result = new ArrayList<>(classes.size());
        for (OrgClass orgClass : classes) {
            ClassVO vo = new ClassVO();
            vo.setId(orgClass.getId());
            vo.setClassCode(orgClass.getClassCode());
            vo.setClassName(orgClass.getClassName());
            vo.setCollegeId(orgClass.getCollegeId());
            vo.setCollegeName(orgClass.getCollegeId() == null ? null : collegeNames.get(orgClass.getCollegeId()));
            vo.setMajorId(orgClass.getMajorId());
            vo.setMajorName(orgClass.getMajorId() == null
                    ? orgClass.getMajorName() : majorNames.getOrDefault(orgClass.getMajorId(), orgClass.getMajorName()));
            vo.setGradeYear(orgClass.getGradeYear());
            vo.setCounselorUserId(orgClass.getCounselorUserId());
            vo.setCounselorName(counselorNames.get(orgClass.getCounselorUserId()));
            vo.setStatus(orgClass.getStatus());
            vo.setStudentCount(studentCounts.getOrDefault(orgClass.getClassCode(), 0));
            result.add(vo);
        }
        return result;
    }

    private Map<Long, String> collegeNames() {
        return collegeMapper.selectList(null).stream()
                .collect(Collectors.toMap(OrgCollege::getId, OrgCollege::getCollegeName));
    }

    private CollegeVO toVO(OrgCollege college, int majorCount) {
        CollegeVO vo = new CollegeVO();
        vo.setId(college.getId());
        vo.setCollegeCode(college.getCollegeCode());
        vo.setCollegeName(college.getCollegeName());
        vo.setStatus(college.getStatus());
        vo.setMajorCount(majorCount);
        return vo;
    }

    private MajorVO toVO(OrgMajor major, Map<Long, String> collegeNames) {
        MajorVO vo = new MajorVO();
        vo.setId(major.getId());
        vo.setCollegeId(major.getCollegeId());
        vo.setCollegeName(collegeNames.get(major.getCollegeId()));
        vo.setMajorCode(major.getMajorCode());
        vo.setMajorName(major.getMajorName());
        vo.setStatus(major.getStatus());
        return vo;
    }

    private OrgCollege requireCollege(Long id) {
        OrgCollege college = id == null ? null : collegeMapper.selectById(id);
        if (college == null) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "学院不存在：" + id);
        }
        return college;
    }

    private OrgMajor requireMajor(Long id) {
        OrgMajor major = id == null ? null : majorMapper.selectById(id);
        if (major == null) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "专业不存在：" + id);
        }
        return major;
    }

    private OrgClass requireClass(Long id) {
        OrgClass orgClass = id == null ? null : classMapper.selectById(id);
        if (orgClass == null) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "班级不存在：" + id);
        }
        return orgClass;
    }

    private SysUser requireCounselor(Long userId) {
        SysUser user = userId == null ? null : userMapper.selectById(userId);
        if (user == null || !UserRoleEnum.COU.name().equals(user.getRole())
                || user.getStatus() == null || user.getStatus() != ENABLED) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "辅导员账号不存在或不可用：" + userId);
        }
        return user;
    }

    private OrgCollege findCollegeByCode(String code, Long excludeId) {
        return collegeMapper.selectOne(Wrappers.<OrgCollege>lambdaQuery()
                .eq(OrgCollege::getCollegeCode, code)
                .ne(excludeId != null, OrgCollege::getId, excludeId)
                .last("limit 1"));
    }

    private OrgMajor findMajorByCode(String code, Long excludeId) {
        return majorMapper.selectOne(Wrappers.<OrgMajor>lambdaQuery()
                .eq(OrgMajor::getMajorCode, code)
                .ne(excludeId != null, OrgMajor::getId, excludeId)
                .last("limit 1"));
    }

    private String requireCode(String code, String label) {
        if (TextUtils.isBlank(code)) {
            throw new BusinessException(ResultCode.PARAM_MISSING, label + "不能为空");
        }
        return code.trim();
    }

    private void requireAdmin() {
        if (UserContext.get().role() != UserRoleEnum.ADMIN) {
            throw new BusinessException(ResultCode.PERMISSION_DENIED, "仅管理员可以维护组织架构");
        }
    }

    private void audit(String action, Long id, String detail) {
        auditLogService.record(AuditLogService.BIZ_ORG, AuditLogService.TARGET_ORG, id,
                null, null, null, action, detail);
    }
}
