package com.college.growth.importer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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
import com.college.growth.common.profile.entity.ArcProfile;
import com.college.growth.common.profile.mapper.ArcProfileMapper;
import com.college.growth.common.result.ResultCode;
import com.college.growth.common.student.entity.StuStudent;
import com.college.growth.common.student.mapper.StuStudentMapper;
import com.college.growth.importer.entity.SysImportTask;
import com.college.growth.importer.mapper.SysImportTaskMapper;
import com.college.growth.importer.service.StudentImportParser.ParseResult;
import com.college.growth.importer.service.StudentImportParser.StudentRow;
import com.college.growth.importer.service.impl.StudentImportServiceImpl;
import com.college.growth.importer.vo.ImportPreviewVO;
import com.college.growth.importer.vo.ImportResultVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** 学生导入：表头、行校验、部分成功、COU 本班范围。 */
@ExtendWith(MockitoExtension.class)
class StudentImportServiceTest {

    static {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, StuStudent.class);
        TableInfoHelper.initTableInfo(assistant, SysUser.class);
        TableInfoHelper.initTableInfo(assistant, OrgClass.class);
        TableInfoHelper.initTableInfo(assistant, ArcProfile.class);
        TableInfoHelper.initTableInfo(assistant, SysImportTask.class);
    }

    @Mock
    private StudentImportParser parser;

    @Mock
    private StuStudentMapper studentMapper;

    @Mock
    private SysUserMapper userMapper;

    @Mock
    private OrgClassMapper classMapper;

    @Mock
    private ArcProfileMapper profileMapper;

    @Mock
    private SysImportTaskMapper taskMapper;

    @Mock
    private PasswordHasher passwordHasher;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private PlatformTransactionManager transactionManager;

    private StudentImportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new StudentImportServiceImpl(parser, studentMapper, userMapper, classMapper,
                profileMapper, taskMapper, passwordHasher, auditLogService,
                new ObjectMapper(), transactionManager);
        UserContext.set(new CurrentUser(9003L, UserRoleEnum.ADMIN, false));
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        lenient().when(passwordHasher.hash(any())).thenReturn("HASHED");
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("preview counts valid rows and shows first rows")
    void previewCountsRows() {
        when(parser.parse(any())).thenReturn(parse(row(2, "2024010111", "Alice", "M", "320102200201011232", "RJ2401")));
        when(classMapper.selectList(any())).thenReturn(List.of(orgClass("RJ2401", 9001L)));
        when(studentMapper.selectList(any())).thenReturn(List.of());
        when(userMapper.selectList(any())).thenReturn(List.of());

        ImportPreviewVO preview = service.preview(file());

        assertEquals(1, preview.getTotalRows());
        assertEquals(1, preview.getValidRows());
        assertEquals(0, preview.getInvalidRows());
        assertEquals("2024010111", preview.getRows().get(0).getStudentNo());
    }

    @Test
    @DisplayName("invalid id card is reported")
    void invalidIdCardReported() {
        when(parser.parse(any())).thenReturn(parse(row(2, "2024010111", "Alice", "M", "320102200201011233", "RJ2401")));
        when(classMapper.selectList(any())).thenReturn(List.of(orgClass("RJ2401", 9001L)));
        when(studentMapper.selectList(any())).thenReturn(List.of());
        when(userMapper.selectList(any())).thenReturn(List.of());

        ImportPreviewVO preview = service.preview(file());

        assertEquals(1, preview.getInvalidRows());
        assertTrue(preview.getRows().get(0).getErrors().stream().anyMatch(e -> e.contains("身份证号")));
    }

    @Test
    @DisplayName("counselor cannot import other class")
    void counselorScopeEnforced() {
        UserContext.set(new CurrentUser(9001L, UserRoleEnum.COU, false));
        when(parser.parse(any())).thenReturn(parse(row(2, "2024010111", "Alice", "M", "320102200201011232", "RJ2401")));
        when(classMapper.selectList(any())).thenReturn(List.of(orgClass("RJ2401", 9002L)));
        when(studentMapper.selectList(any())).thenReturn(List.of());
        when(userMapper.selectList(any())).thenReturn(List.of());

        ImportPreviewVO preview = service.preview(file());

        assertEquals(1, preview.getInvalidRows());
        assertTrue(preview.getRows().get(0).getErrors().stream().anyMatch(e -> e.contains("本班")));
    }

    @Test
    @DisplayName("execute imports valid rows and records task")
    void executeImportsRows() {
        UserContext.set(new CurrentUser(9003L, UserRoleEnum.ADMIN, false));
        when(parser.parse(any())).thenReturn(parse(row(2, "2024010111", "Alice", "M", "320102200201011232", "RJ2401")));
        when(classMapper.selectList(any())).thenReturn(List.of(orgClass("RJ2401", 9001L)));
        when(studentMapper.selectList(any())).thenReturn(List.of());
        when(userMapper.selectList(any())).thenReturn(List.of());
        when(taskMapper.insert(any(SysImportTask.class))).thenAnswer(invocation -> {
            SysImportTask task = invocation.getArgument(0);
            task.setId(77L);
            return 1;
        });

        ImportResultVO result = service.execute(file());

        assertEquals(77L, result.getTaskId());
        assertEquals(1, result.getSuccessRows());
        assertEquals(0, result.getFailedRows());
        assertEquals("SUCCESS", result.getStatus());
        verify(studentMapper).insert(any(StuStudent.class));
        verify(userMapper).insert(any(SysUser.class));
    }

    // ------------------------------------------------------------------

    private MockMultipartFile file() {
        return new MockMultipartFile("file", "students.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1});
    }

    private ParseResult parse(StudentRow... rows) {
        ParseResult result = new ParseResult();
        result.setHeaders(StudentImportParser.HEADERS);
        result.setRows(new ArrayList<>(List.of(rows)));
        return result;
    }

    private StudentRow row(int rowNo, String studentNo, String name, String gender,
                           String idCard, String classCode) {
        StudentRow row = new StudentRow();
        row.setRowNo(rowNo);
        row.setStudentNo(studentNo);
        row.setName(name);
        row.setGender(gender);
        row.setNation("汉族");
        row.setIdCard(idCard);
        row.setNativePlace("江苏");
        row.setClassCode(classCode);
        row.setEnrollmentYear("2024");
        return row;
    }

    private OrgClass orgClass(String code, Long counselorId) {
        OrgClass orgClass = new OrgClass();
        orgClass.setClassCode(code);
        orgClass.setClassName("软件2401");
        orgClass.setCounselorUserId(counselorId);
        orgClass.setStatus(1);
        orgClass.setGradeYear(2024);
        return orgClass;
    }
}
