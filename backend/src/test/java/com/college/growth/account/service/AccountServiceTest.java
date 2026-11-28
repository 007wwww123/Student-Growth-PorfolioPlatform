package com.college.growth.account.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.college.growth.account.dto.BatchOpenDTO;
import com.college.growth.account.dto.UserCreateDTO;
import com.college.growth.account.service.impl.AccountServiceImpl;
import com.college.growth.account.vo.BatchOpenResultVO;
import com.college.growth.account.vo.CreateAccountResultVO;
import com.college.growth.auth.entity.SysUser;
import com.college.growth.auth.mapper.SysUserMapper;
import com.college.growth.auth.service.PasswordHasher;
import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.context.UserContext;
import com.college.growth.common.enums.UserRoleEnum;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.log.service.AuditLogService;
import com.college.growth.common.org.entity.OrgClass;
import com.college.growth.common.org.mapper.OrgClassMapper;
import com.college.growth.common.result.ResultCode;
import com.college.growth.common.student.entity.StuStudent;
import com.college.growth.common.student.mapper.StuStudentMapper;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 账号管理单测：学生账号口径、初始密码、重复开通、自停用保护、批量跳过。 */
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    static {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysUser.class);
        TableInfoHelper.initTableInfo(assistant, StuStudent.class);
        TableInfoHelper.initTableInfo(assistant, OrgClass.class);
    }

    @Mock
    private SysUserMapper userMapper;

    @Mock
    private StuStudentMapper studentMapper;

    @Mock
    private OrgClassMapper classMapper;

    @Mock
    private PasswordHasher passwordHasher;

    @Mock
    private AuditLogService auditLogService;

    private AccountServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AccountServiceImpl(userMapper, studentMapper, classMapper,
                passwordHasher, auditLogService);
        UserContext.set(new CurrentUser(9003L, UserRoleEnum.ADMIN, false));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("学生账号 id 等于 studentId，初始密码取身份证后六位")
    void createStudentAccountUsesStudentIdentity() {
        StuStudent student = student(1001L, "2024010101", "320102200001011234");
        when(studentMapper.selectById(1001L)).thenReturn(student);
        when(userMapper.selectById(1001L)).thenReturn(null);
        when(userMapper.selectOne(any())).thenReturn(null);
        when(passwordHasher.hash(anyString())).thenReturn("HASHED");

        UserCreateDTO dto = new UserCreateDTO();
        dto.setRole("STU");
        dto.setStudentId(1001L);
        dto.setDisplayName("李明");

        CreateAccountResultVO result = service.create(dto);

        assertEquals(1001L, result.getId());
        assertEquals("2024010101", result.getUsername());
        assertEquals("011234", result.getInitialPassword());
        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).insert(captor.capture());
        assertEquals("STU", captor.getValue().getRole());
        assertEquals(1001L, captor.getValue().getStudentId());
        assertEquals("HASHED", captor.getValue().getPasswordHash());
    }

    @Test
    @DisplayName("学生已有账号时拒绝重复开通")
    void createStudentAccountRejectsDuplicate() {
        StuStudent student = student(1001L, "2024010101", "320102200001011234");
        when(studentMapper.selectById(1001L)).thenReturn(student);
        when(userMapper.selectById(1001L)).thenReturn(new SysUser());
        UserCreateDTO dto = new UserCreateDTO();
        dto.setRole("STU");
        dto.setStudentId(1001L);
        dto.setDisplayName("李明");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(dto));
        assertEquals(ResultCode.STATE_CONFLICT.getCode(), ex.getResultCode().getCode());
    }

    @Test
    @DisplayName("不能停用当前登录账号")
    void cannotDisableSelf() {
        SysUser self = new SysUser();
        self.setId(9003L);
        self.setUsername("admin");
        self.setRole(UserRoleEnum.ADMIN.name());
        when(userMapper.selectById(9003L)).thenReturn(self);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateStatus(9003L, 0));
        assertEquals(ResultCode.STATE_CONFLICT.getCode(), ex.getResultCode().getCode());
    }

    @Test
    @DisplayName("学生账号不能改角色")
    void cannotChangeStudentRole() {
        SysUser user = new SysUser();
        user.setId(1001L);
        user.setStudentId(1001L);
        user.setUsername("2024010101");
        when(userMapper.selectById(1001L)).thenReturn(user);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.assignRole(1001L, "COU"));
        assertEquals(ResultCode.STATE_CONFLICT.getCode(), ex.getResultCode().getCode());
    }

    @Test
    @DisplayName("批量开通跳过已有账号并返回初始密码")
    void batchOpenSkipsExisting() {
        StuStudent first = student(1001L, "2024010101", "320102200001011234");
        StuStudent second = student(1002L, "2024010102", "320102200001015678");
        when(studentMapper.selectList(any())).thenReturn(List.of(first, second));
        SysUser existing = new SysUser();
        existing.setId(1001L);
        existing.setUsername("2024010101");
        when(userMapper.selectList(any())).thenReturn(List.of(existing));
        when(passwordHasher.hash(anyString())).thenReturn("HASHED");

        BatchOpenDTO dto = new BatchOpenDTO();
        dto.setClassCodes(List.of("RJ2401"));
        BatchOpenResultVO result = service.batchOpen(dto);

        assertEquals(2, result.getTotalStudents());
        assertEquals(1, result.getCreated());
        assertEquals(1, result.getSkipped());
        assertEquals(1, result.getAccounts().size());
        assertEquals("2024010102", result.getAccounts().get(0).getUsername());
        assertTrue(result.getAccounts().get(0).getInitialPassword().length() >= 6);
    }

    private StuStudent student(Long id, String studentNo, String idCard) {
        StuStudent student = new StuStudent();
        student.setId(id);
        student.setStudentNo(studentNo);
        student.setName("学生" + id);
        student.setIdCard(idCard);
        student.setClassCode("RJ2401");
        student.setStatus(1);
        return student;
    }
}
