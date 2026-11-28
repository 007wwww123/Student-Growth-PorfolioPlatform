package com.college.growth.academic.record.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.college.growth.academic.record.dto.CertificateSaveDTO;
import com.college.growth.academic.record.dto.EventSaveDTO;
import com.college.growth.academic.record.dto.VerifyDTO;
import com.college.growth.academic.record.entity.ArcCertificate;
import com.college.growth.academic.record.entity.ArcEvent;
import com.college.growth.academic.record.entity.ArcRank;
import com.college.growth.academic.record.mapper.ArcCertificateMapper;
import com.college.growth.academic.record.mapper.ArcEventMapper;
import com.college.growth.academic.record.mapper.ArcRankMapper;
import com.college.growth.academic.record.service.impl.AcademicRecordServiceImpl;
import com.college.growth.academic.record.vo.CertificateVO;
import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.context.UserContext;
import com.college.growth.common.enums.UserRoleEnum;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.log.service.AuditLogService;
import com.college.growth.common.org.service.StudentClassService;
import com.college.growth.common.result.ResultCode;
import com.college.growth.common.student.entity.StuStudent;
import com.college.growth.common.student.mapper.StuStudentMapper;
import java.time.LocalDate;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 学业成果：本人维护、同名去重、认定权限、认定后锁定。 */
@ExtendWith(MockitoExtension.class)
class AcademicRecordServiceTest {

    static {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ArcRank.class);
        TableInfoHelper.initTableInfo(assistant, ArcCertificate.class);
        TableInfoHelper.initTableInfo(assistant, ArcEvent.class);
        TableInfoHelper.initTableInfo(assistant, StuStudent.class);
    }

    @Mock
    private ArcRankMapper rankMapper;

    @Mock
    private ArcCertificateMapper certificateMapper;

    @Mock
    private ArcEventMapper eventMapper;

    @Mock
    private StuStudentMapper studentMapper;

    @Mock
    private StudentClassService studentClassService;

    @Mock
    private AuditLogService auditLogService;

    private AcademicRecordServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AcademicRecordServiceImpl(rankMapper, certificateMapper, eventMapper,
                studentMapper, studentClassService, auditLogService);
        UserContext.set(new CurrentUser(1001L, UserRoleEnum.STU, false));
        StuStudent student = new StuStudent();
        student.setId(1001L);
        student.setStudentNo("2024010101");
        student.setEnrollmentYear(2024);
        lenient().when(studentMapper.selectById(1001L)).thenReturn(student);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("student creates own certificate as PENDING")
    void createCertificateAsStudent() {
        when(certificateMapper.selectCount(any())).thenReturn(0L);
        CertificateVO vo = service.createCertificate(1001L, certificateDto("大学英语四级（CET-4）"));
        assertEquals("PENDING", vo.getVerifyStatus());
        assertEquals(1001L, vo.getStudentId());
    }

    @Test
    @DisplayName("duplicate certificate name rejected")
    void duplicateCertificateRejected() {
        when(certificateMapper.selectCount(any())).thenReturn(1L);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createCertificate(1001L, certificateDto("大学英语四级（CET-4）")));
        assertEquals(ResultCode.STATE_CONFLICT.getCode(), ex.getResultCode().getCode());
    }

    @Test
    @DisplayName("student cannot maintain other student record")
    void cannotMaintainOtherStudent() {
        UserContext.set(new CurrentUser(1002L, UserRoleEnum.STU, false));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createCertificate(1001L, certificateDto("计算机二级")));
        assertEquals(ResultCode.PERMISSION_DENIED.getCode(), ex.getResultCode().getCode());
    }

    @Test
    @DisplayName("counselor verifies certificate as PASSED")
    void counselorVerifiesCertificate() {
        UserContext.set(new CurrentUser(9001L, UserRoleEnum.COU, false));
        when(studentClassService.isCounselorOf(1001L, com.college.growth.common.util.AcademicYearUtils.current(), 9001L))
                .thenReturn(true);
        ArcCertificate entity = new ArcCertificate();
        entity.setId(9L);
        entity.setStudentId(1001L);
        entity.setCertName("大学英语四级（CET-4）");
        entity.setVerifyStatus("PENDING");
        when(certificateMapper.selectById(9L)).thenReturn(entity);

        VerifyDTO dto = new VerifyDTO();
        dto.setStatus("PASSED");
        CertificateVO vo = service.verifyCertificate(1001L, 9L, dto);

        assertEquals("PASSED", vo.getVerifyStatus());
        assertEquals(9001L, vo.getVerifyUserId());
    }

    @Test
    @DisplayName("student cannot verify")
    void studentCannotVerify() {
        VerifyDTO dto = new VerifyDTO();
        dto.setStatus("PASSED");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.verifyCertificate(1001L, 9L, dto));
        assertEquals(ResultCode.DATA_SCOPE_DENIED.getCode(), ex.getResultCode().getCode());
    }

    @Test
    @DisplayName("verified certificate cannot be edited")
    void verifiedCertificateLocked() {
        ArcCertificate entity = new ArcCertificate();
        entity.setId(9L);
        entity.setStudentId(1001L);
        entity.setVerifyStatus("PASSED");
        when(certificateMapper.selectById(9L)).thenReturn(entity);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateCertificate(1001L, 9L, certificateDto("改个名字")));
        assertEquals(ResultCode.STATE_CONFLICT.getCode(), ex.getResultCode().getCode());
    }

    @Test
    @DisplayName("event end date before start rejected")
    void eventDateRangeValidated() {
        EventSaveDTO dto = new EventSaveDTO();
        dto.setEventType("COMPETITION");
        dto.setTitle("全国大学生数学建模竞赛");
        dto.setLevel("NATIONAL");
        dto.setStartDate(LocalDate.now().minusDays(2));
        dto.setEndDate(LocalDate.now().minusDays(5));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createEvent(1001L, dto));
        assertEquals(ResultCode.PARAM_INVALID.getCode(), ex.getResultCode().getCode());
    }

    private CertificateSaveDTO certificateDto(String name) {
        CertificateSaveDTO dto = new CertificateSaveDTO();
        dto.setCertType("外语");
        dto.setCertName(name);
        dto.setScore("528");
        dto.setObtainDate(LocalDate.now().minusDays(10));
        return dto;
    }
}
