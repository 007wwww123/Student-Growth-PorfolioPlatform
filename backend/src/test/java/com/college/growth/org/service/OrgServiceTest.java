package com.college.growth.org.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.college.growth.auth.entity.SysUser;
import com.college.growth.auth.mapper.SysUserMapper;
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
import com.college.growth.org.dto.CollegeSaveDTO;
import com.college.growth.org.service.impl.OrgServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 组织架构维护单测：编码唯一、停用保护、辅导员角色校验。 */
@ExtendWith(MockitoExtension.class)
class OrgServiceTest {

    static {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, OrgCollege.class);
        TableInfoHelper.initTableInfo(assistant, OrgMajor.class);
        TableInfoHelper.initTableInfo(assistant, OrgClass.class);
        TableInfoHelper.initTableInfo(assistant, SysUser.class);
        TableInfoHelper.initTableInfo(assistant, StuStudent.class);
    }

    @Mock
    private OrgCollegeMapper collegeMapper;

    @Mock
    private OrgMajorMapper majorMapper;

    @Mock
    private OrgClassMapper classMapper;

    @Mock
    private SysUserMapper userMapper;

    @Mock
    private StuStudentMapper studentMapper;

    @Mock
    private AuditLogService auditLogService;

    private OrgServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrgServiceImpl(collegeMapper, majorMapper, classMapper, userMapper,
                studentMapper, auditLogService);
        UserContext.set(new CurrentUser(9003L, UserRoleEnum.ADMIN, false));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("学院编码重复时拒绝新增")
    void createCollegeRejectsDuplicateCode() {
        when(collegeMapper.selectOne(any())).thenReturn(college(3001L, "ZJGCS"));
        CollegeSaveDTO dto = new CollegeSaveDTO();
        dto.setCollegeCode("ZJGCS");
        dto.setCollegeName("卓越工程师学院");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.createCollege(dto));
        assertEquals(ResultCode.PARAM_INVALID.getCode(), ex.getResultCode().getCode());
    }

    @Test
    @DisplayName("班级有在读学生时不能停用")
    void cannotDisableClassWithStudents() {
        OrgClass clazz = new OrgClass();
        clazz.setId(2001L);
        clazz.setClassCode("RJ2401");
        clazz.setStatus(1);
        when(classMapper.selectById(2001L)).thenReturn(clazz);
        when(studentMapper.selectCount(any())).thenReturn(2L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateClassStatus(2001L, 0));
        assertEquals(ResultCode.STATE_CONFLICT.getCode(), ex.getResultCode().getCode());
    }

    @Test
    @DisplayName("专业下有启用班级时不能停用")
    void cannotDisableMajorWithClasses() {
        OrgMajor major = new OrgMajor();
        major.setId(3101L);
        major.setStatus(1);
        when(majorMapper.selectById(3101L)).thenReturn(major);
        when(classMapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateMajorStatus(3101L, 0));
        assertEquals(ResultCode.STATE_CONFLICT.getCode(), ex.getResultCode().getCode());
    }

    @Test
    @DisplayName("只能绑定角色为辅导员的启用账号")
    void assignCounselorRequiresCounselorAccount() {
        OrgClass clazz = new OrgClass();
        clazz.setId(2001L);
        when(classMapper.selectById(2001L)).thenReturn(clazz);
        SysUser student = new SysUser();
        student.setId(1001L);
        student.setRole(UserRoleEnum.STU.name());
        student.setStatus(1);
        when(userMapper.selectById(1001L)).thenReturn(student);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.assignCounselor(2001L, 1001L));
        assertEquals(ResultCode.PARAM_INVALID.getCode(), ex.getResultCode().getCode());
    }

    private OrgCollege college(Long id, String code) {
        OrgCollege college = new OrgCollege();
        college.setId(id);
        college.setCollegeCode(code);
        college.setCollegeName("卓越工程师学院");
        college.setStatus(1);
        return college;
    }
}
