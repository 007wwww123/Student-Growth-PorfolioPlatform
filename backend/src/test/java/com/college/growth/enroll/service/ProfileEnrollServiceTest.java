package com.college.growth.enroll.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.context.UserContext;
import com.college.growth.common.enums.UserRoleEnum;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.log.entity.SysAuditLog;
import com.college.growth.common.log.mapper.SysAuditLogMapper;
import com.college.growth.common.log.service.AuditLogService;
import com.college.growth.common.org.entity.OrgClass;
import com.college.growth.common.org.mapper.OrgClassMapper;
import com.college.growth.common.profile.entity.ArcProfile;
import com.college.growth.common.profile.mapper.ArcProfileMapper;
import com.college.growth.common.result.ResultCode;
import com.college.growth.common.student.entity.StuStudent;
import com.college.growth.common.student.mapper.StuStudentMapper;
import com.college.growth.enroll.dto.BatchCreateDTO;
import com.college.growth.enroll.service.impl.ProfileEnrollServiceImpl;
import com.college.growth.enroll.vo.BatchCreateResultVO;
import com.college.growth.enroll.vo.ClassEnrollVO;
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

/**
 * 批量建档单测（S-1-01 / EX-01）。
 *
 * <p>重点验证三件事：写权限按班级判定、已存在板块不重复创建、重复执行不产生副作用。
 * 逐条插入的具体 SQL 由真库联调验证，这里只验证「插了几条、插的是哪几块」。</p>
 */
@ExtendWith(MockitoExtension.class)
class ProfileEnrollServiceTest {

    private static final String YEAR = "2025-2026-1";

    private static final long COUNSELOR_ID = 9001L;

    static {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ArcProfile.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), StuStudent.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), OrgClass.class);
    }

    @Mock
    private ArcProfileMapper profileMapper;

    @Mock
    private StuStudentMapper studentMapper;

    @Mock
    private OrgClassMapper orgClassMapper;

    @Mock
    private SysAuditLogMapper auditLogMapper;

    private ProfileEnrollServiceImpl service;

    private final List<ArcProfile> inserted = new ArrayList<>();

    @BeforeEach
    void setUp() {
        UserContext.set(new CurrentUser(COUNSELOR_ID, UserRoleEnum.COU, false));
        lenient().when(profileMapper.insert(any(ArcProfile.class))).thenAnswer(invocation -> {
            inserted.add(invocation.getArgument(0));
            return 1;
        });
        service = new ProfileEnrollServiceImpl(profileMapper, studentMapper, orgClassMapper,
                new AuditLogService(auditLogMapper));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("建档：学生角色返回 EC-40302")
    void batchCreateRejectedForStudent() {
        UserContext.set(new CurrentUser(1001L, UserRoleEnum.STU, false));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.batchCreate(dto(List.of("RJ2401"))));

        assertEquals(ResultCode.DATA_SCOPE_DENIED, ex.getResultCode());
        verify(profileMapper, never()).insert(any(ArcProfile.class));
    }

    @Test
    @DisplayName("建档：管理员是只读授权，不能批量建档")
    void batchCreateRejectedForAdmin() {
        UserContext.set(new CurrentUser(9003L, UserRoleEnum.ADMIN, true));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.batchCreate(dto(List.of("RJ2401"))));

        assertEquals(ResultCode.DATA_SCOPE_DENIED, ex.getResultCode());
    }

    @Test
    @DisplayName("建档：非本班班级返回 EC-40302")
    void batchCreateRejectedForOtherClass() {
        when(orgClassMapper.selectList(any())).thenReturn(List.of(orgClass("RJ2401", "软件2401", 9002L)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.batchCreate(dto(List.of("RJ2401"))));

        assertEquals(ResultCode.DATA_SCOPE_DENIED, ex.getResultCode());
        verify(profileMapper, never()).insert(any(ArcProfile.class));
    }

    @Test
    @DisplayName("建档：班级不存在返回 EC-40002")
    void batchCreateRejectsUnknownClass() {
        when(orgClassMapper.selectList(any())).thenReturn(List.of());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.batchCreate(dto(List.of("RJ9999"))));

        assertEquals(ResultCode.PARAM_MISSING, ex.getResultCode());
    }

    @Test
    @DisplayName("建档：班级下无在读学生返回 EC-40002")
    void batchCreateRejectsEmptyClass() {
        when(orgClassMapper.selectList(any())).thenReturn(List.of(orgClass("RJ2401", "软件2401", COUNSELOR_ID)));
        when(studentMapper.selectList(any())).thenReturn(List.of());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.batchCreate(dto(List.of("RJ2401"))));

        assertEquals(ResultCode.PARAM_MISSING, ex.getResultCode());
    }

    @Test
    @DisplayName("建档：六板块全缺时每人新建 6 条 DRAFT，并写一条学生级审计")
    void batchCreateCreatesSixSectionsPerStudent() {
        when(orgClassMapper.selectList(any())).thenReturn(List.of(orgClass("RJ2401", "软件2401", COUNSELOR_ID)));
        when(studentMapper.selectList(any())).thenReturn(List.of(student(1001L, "2024010101", "李明", "RJ2401")));
        when(profileMapper.selectList(any())).thenReturn(List.of());

        BatchCreateResultVO result = service.batchCreate(dto(List.of("RJ2401")));

        assertEquals(1, result.getTotalStudents());
        assertEquals(1, result.getCreatedStudents());
        assertEquals(0, result.getSkippedStudents());
        assertEquals(6, result.getCreatedProfiles());
        assertEquals(0, result.getExistingProfiles());
        assertEquals(6, inserted.size());
        assertTrue(inserted.stream().allMatch(item -> "DRAFT".equals(item.getStatus())));
        assertTrue(inserted.stream().allMatch(item -> Boolean.FALSE.equals(item.getCompleted())));
        assertTrue(inserted.stream().allMatch(item -> YEAR.equals(item.getAcademicYear())));
        verify(auditLogMapper, times(1)).insert(any(SysAuditLog.class));
    }

    @Test
    @DisplayName("建档：已存在板块跳过，只补缺的部分（EX-01）")
    void batchCreateSkipsExistingSections() {
        when(orgClassMapper.selectList(any())).thenReturn(List.of(orgClass("RJ2401", "软件2401", COUNSELOR_ID)));
        when(studentMapper.selectList(any())).thenReturn(List.of(student(1001L, "2024010101", "李明", "RJ2401")));
        when(profileMapper.selectList(any())).thenReturn(List.of(profile(1001L, "BASIC")));

        BatchCreateResultVO result = service.batchCreate(dto(List.of("RJ2401")));

        assertEquals(5, result.getCreatedProfiles());
        assertEquals(1, result.getExistingProfiles());
        assertEquals(1, result.getCreatedStudents());
        assertEquals(1, result.getStudents().get(0).getSkippedSectionCount());
        assertEquals(List.of("ACADEMIC", "INTEREST", "LIFE", "CAREER", "MENTOR"),
                result.getStudents().get(0).getCreatedSections());
        assertEquals(5, inserted.size());
    }

    @Test
    @DisplayName("建档：六板块齐全时整人跳过，重复执行不产生写入与审计")
    void batchCreateIsIdempotent() {
        when(orgClassMapper.selectList(any())).thenReturn(List.of(orgClass("RJ2401", "软件2401", COUNSELOR_ID)));
        when(studentMapper.selectList(any())).thenReturn(List.of(student(1001L, "2024010101", "李明", "RJ2401")));
        when(profileMapper.selectList(any())).thenReturn(List.of(
                profile(1001L, "BASIC"), profile(1001L, "ACADEMIC"), profile(1001L, "INTEREST"),
                profile(1001L, "LIFE"), profile(1001L, "CAREER"), profile(1001L, "MENTOR")));

        BatchCreateResultVO result = service.batchCreate(dto(List.of("RJ2401")));

        assertEquals(0, result.getCreatedProfiles());
        assertEquals(6, result.getExistingProfiles());
        assertEquals(0, result.getCreatedStudents());
        assertEquals(1, result.getSkippedStudents());
        assertTrue(inserted.isEmpty());
        verify(auditLogMapper, never()).insert(any(SysAuditLog.class));
    }

    @Test
    @DisplayName("建档：单批超过 500 人返回 EC-40003")
    void batchCreateRejectsTooManyStudents() {
        when(orgClassMapper.selectList(any())).thenReturn(List.of(orgClass("RJ2401", "软件2401", COUNSELOR_ID)));
        List<StuStudent> students = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            students.add(student((long) (2000 + i), "2024" + i, "学生" + i, "RJ2401"));
        }
        when(studentMapper.selectList(any())).thenReturn(students);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.batchCreate(dto(List.of("RJ2401"))));

        assertEquals(ResultCode.PARAM_INVALID, ex.getResultCode());
    }

    @Test
    @DisplayName("班级进度：已建成学生与待建档学生分开统计")
    void listClassesCountsEnrollment() {
        when(orgClassMapper.selectList(any())).thenReturn(List.of(orgClass("RJ2401", "软件2401", COUNSELOR_ID)));
        when(studentMapper.selectList(any())).thenReturn(List.of(
                student(1001L, "2024010101", "李明", "RJ2401"),
                student(1002L, "2024010102", "王芳", "RJ2401")));
        when(profileMapper.selectList(any())).thenReturn(List.of(
                profile(1001L, "BASIC"), profile(1001L, "ACADEMIC"), profile(1001L, "INTEREST"),
                profile(1001L, "LIFE"), profile(1001L, "CAREER"), profile(1001L, "MENTOR"),
                profile(1002L, "BASIC")));

        List<ClassEnrollVO> classes = service.listClasses(YEAR);

        assertEquals(1, classes.size());
        assertEquals(2, classes.get(0).getStudentCount());
        assertEquals(1, classes.get(0).getEnrolledStudentCount());
        assertEquals(1, classes.get(0).getMissingStudentCount());
        assertFalse(classes.get(0).isEnrolled());
    }

    private BatchCreateDTO dto(List<String> classCodes) {
        BatchCreateDTO dto = new BatchCreateDTO();
        dto.setAcademicYear(YEAR);
        dto.setClassCodes(classCodes);
        return dto;
    }

    private OrgClass orgClass(String code, String name, Long counselorId) {
        OrgClass orgClass = new OrgClass();
        orgClass.setClassCode(code);
        orgClass.setClassName(name);
        orgClass.setMajorName("软件工程");
        orgClass.setGradeYear(2024);
        orgClass.setCounselorUserId(counselorId);
        orgClass.setStatus(1);
        return orgClass;
    }

    private StuStudent student(Long id, String no, String name, String classCode) {
        StuStudent student = new StuStudent();
        student.setId(id);
        student.setStudentNo(no);
        student.setName(name);
        student.setClassCode(classCode);
        student.setStatus(1);
        return student;
    }

    private ArcProfile profile(Long studentId, String sectionCode) {
        ArcProfile profile = new ArcProfile();
        profile.setStudentId(studentId);
        profile.setSectionCode(sectionCode);
        profile.setAcademicYear(YEAR);
        profile.setStatus("DRAFT");
        return profile;
    }
}
