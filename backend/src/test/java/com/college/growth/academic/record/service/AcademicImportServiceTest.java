package com.college.growth.academic.record.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.college.growth.academic.record.entity.ArcCertificate;
import com.college.growth.academic.record.entity.ArcRank;
import com.college.growth.academic.record.mapper.ArcCertificateMapper;
import com.college.growth.academic.record.mapper.ArcRankMapper;
import com.college.growth.academic.record.service.AcademicImportParser.CertRow;
import com.college.growth.academic.record.service.AcademicImportParser.ParseResult;
import com.college.growth.academic.record.service.AcademicImportParser.RankRow;
import com.college.growth.academic.record.service.impl.AcademicImportServiceImpl;
import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.context.UserContext;
import com.college.growth.common.enums.UserRoleEnum;
import com.college.growth.common.log.service.AuditLogService;
import com.college.growth.common.org.service.StudentClassService;
import com.college.growth.common.student.entity.StuStudent;
import com.college.growth.common.student.mapper.StuStudentMapper;
import com.college.growth.importer.entity.SysImportTask;
import com.college.growth.importer.mapper.SysImportTaskMapper;
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

/** 成绩/证书导入：覆盖、本班范围、证书去重。 */
@ExtendWith(MockitoExtension.class)
class AcademicImportServiceTest {

    static {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, StuStudent.class);
        TableInfoHelper.initTableInfo(assistant, ArcRank.class);
        TableInfoHelper.initTableInfo(assistant, ArcCertificate.class);
        TableInfoHelper.initTableInfo(assistant, SysImportTask.class);
    }

    @Mock
    private AcademicImportParser parser;

    @Mock
    private StuStudentMapper studentMapper;

    @Mock
    private ArcRankMapper rankMapper;

    @Mock
    private ArcCertificateMapper certificateMapper;

    @Mock
    private SysImportTaskMapper taskMapper;

    @Mock
    private StudentClassService studentClassService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private PlatformTransactionManager transactionManager;

    private AcademicImportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AcademicImportServiceImpl(parser, studentMapper, rankMapper, certificateMapper,
                taskMapper, studentClassService, auditLogService, new ObjectMapper(), transactionManager);
        UserContext.set(new CurrentUser(9003L, UserRoleEnum.ADMIN, false));
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        lenient().when(taskMapper.insert(any(SysImportTask.class))).thenAnswer(invocation -> {
            SysImportTask task = invocation.getArgument(0);
            task.setId(99L);
            return 1;
        });
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("rank import upserts one student-term row")
    void rankImportUpserts() {
        when(parser.parseRanks(any())).thenReturn(parse(rankRow()));
        when(studentMapper.selectList(any())).thenReturn(List.of(student()));
        when(studentMapper.selectOne(any())).thenReturn(student());
        when(rankMapper.selectOne(any())).thenReturn(null);

        ImportResultVO result = service.executeRanks(file());
        assertEquals(1, result.getSuccessRows());
        assertEquals(0, result.getFailedRows());
        assertEquals("SUCCESS", result.getStatus());
    }

    @Test
    @DisplayName("counselor cannot import other class rank")
    void counselorScopeEnforced() {
        UserContext.set(new CurrentUser(9001L, UserRoleEnum.COU, false));
        when(parser.parseRanks(any())).thenReturn(parse(rankRow()));
        when(studentMapper.selectList(any())).thenReturn(List.of(student()));
        when(studentClassService.isCounselorOf(any(), any(), any())).thenReturn(false);

        ImportPreviewVO preview = service.previewRanks(file());
        assertEquals(1, preview.getInvalidRows());
        assertEquals(0, preview.getValidRows());
    }

    @Test
    @DisplayName("duplicate certificate rejected in preview")
    void duplicateCertificateRejected() {
        when(parser.parseCertificates(any())).thenReturn(parse(certRow()));
        when(studentMapper.selectList(any())).thenReturn(List.of(student()));
        when(certificateMapper.selectCount(any())).thenReturn(1L);

        ImportPreviewVO preview = service.previewCertificates(file());
        assertEquals(1, preview.getInvalidRows());
    }

    @Test
    @DisplayName("certificate import inserts PENDING row")
    void certificateImportInserts() {
        when(parser.parseCertificates(any())).thenReturn(parse(certRow()));
        when(studentMapper.selectList(any())).thenReturn(List.of(student()));
        when(studentMapper.selectOne(any())).thenReturn(student());
        when(certificateMapper.selectCount(any())).thenReturn(0L);

        ImportResultVO result = service.executeCertificates(file());
        assertEquals(1, result.getSuccessRows());
        assertEquals(99L, result.getTaskId());
    }

    private MockMultipartFile file() {
        return new MockMultipartFile("file", "x.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1});
    }

    private <T> ParseResult<T> parse(T row) {
        ParseResult<T> result = new ParseResult<>();
        result.setHeaders(List.of("x"));
        result.setRows(new ArrayList<>(List.of(row)));
        return result;
    }

    private RankRow rankRow() {
        RankRow row = new RankRow();
        row.setRowNo(2);
        row.setStudentNo("2024010101");
        row.setTerm("2025-2026-1");
        row.setClassRank("8");
        row.setMajorRank("35");
        row.setGpa("3.62");
        return row;
    }

    private CertRow certRow() {
        CertRow row = new CertRow();
        row.setRowNo(2);
        row.setStudentNo("2024010101");
        row.setCertType("外语");
        row.setCertName("CET-4");
        row.setScore("528");
        row.setObtainDate("2026-06-14");
        return row;
    }

    private StuStudent student() {
        StuStudent student = new StuStudent();
        student.setId(1001L);
        student.setStudentNo("2024010101");
        student.setName("Test");
        student.setEnrollmentYear(2024);
        student.setClassCode("RJ2401");
        student.setStatus(1);
        return student;
    }
}
