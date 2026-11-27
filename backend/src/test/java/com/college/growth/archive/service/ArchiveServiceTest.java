package com.college.growth.archive.service;

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
import com.college.growth.archive.dto.ArchiveBatchDTO;
import com.college.growth.archive.service.impl.ArchiveServiceImpl;
import com.college.growth.archive.vo.ArchiveCandidateVO;
import com.college.growth.archive.vo.ArchiveResultVO;
import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.context.UserContext;
import com.college.growth.common.enums.ProfileStatusEnum;
import com.college.growth.common.enums.UserRoleEnum;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.log.entity.ArcChangeLog;
import com.college.growth.common.log.entity.SysAuditLog;
import com.college.growth.common.log.mapper.ArcChangeLogMapper;
import com.college.growth.common.log.mapper.SysAuditLogMapper;
import com.college.growth.common.log.service.AuditLogService;
import com.college.growth.common.log.service.ChangeLogService;
import com.college.growth.common.org.entity.OrgClass;
import com.college.growth.common.org.mapper.OrgClassMapper;
import com.college.growth.common.org.service.StudentClassService;
import com.college.growth.common.profile.entity.ArcProfile;
import com.college.growth.common.profile.mapper.ArcProfileMapper;
import com.college.growth.common.result.ResultCode;
import com.college.growth.common.student.entity.StuStudent;
import com.college.growth.common.student.mapper.StuStudentMapper;
import java.math.BigDecimal;
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
 * 批量归档单测（S-1-11 / EX-12）。
 *
 * <p>核心断言只有两条：整批校验（一个人没完成就谁都不动）与终态写入
 * （六板块 LOCKED、完成标记保持为真、每块一条归档日志）。</p>
 */
@ExtendWith(MockitoExtension.class)
class ArchiveServiceTest {

    private static final String YEAR = "2025-2026-1";

    private static final long COUNSELOR_ID = 9001L;

    private static final long STUDENT_ID = 1001L;

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
    private ArcChangeLogMapper changeLogMapper;

    @Mock
    private SysAuditLogMapper auditLogMapper;

    @Mock
    private StudentClassService studentClassService;

    private ArchiveServiceImpl service;

    private final List<ArcProfile> updated = new ArrayList<>();

    @BeforeEach
    void setUp() {
        UserContext.set(new CurrentUser(COUNSELOR_ID, UserRoleEnum.COU, false));
        lenient().when(profileMapper.updateById(any(ArcProfile.class))).thenAnswer(invocation -> {
            updated.add(invocation.getArgument(0));
            return 1;
        });
        service = new ArchiveServiceImpl(profileMapper, studentMapper, orgClassMapper,
                new ChangeLogService(changeLogMapper), new AuditLogService(auditLogMapper),
                studentClassService);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("候选人：六板块通过可归档，缺失或未通过进未完成清单")
    void listCandidatesMarksIncompleteSections() {
        when(orgClassMapper.selectList(any())).thenReturn(List.of(orgClass("RJ2401", "软件2401", COUNSELOR_ID)));
        when(studentMapper.selectList(any())).thenReturn(List.of(
                student(STUDENT_ID, "2024010101", "李明", "RJ2401"),
                student(1002L, "2024010102", "王芳", "RJ2401")));
        when(profileMapper.selectList(any())).thenReturn(List.of(
                profile(STUDENT_ID, "BASIC", ProfileStatusEnum.APPROVED),
                profile(STUDENT_ID, "ACADEMIC", ProfileStatusEnum.APPROVED),
                profile(STUDENT_ID, "INTEREST", ProfileStatusEnum.APPROVED),
                profile(STUDENT_ID, "LIFE", ProfileStatusEnum.APPROVED),
                profile(STUDENT_ID, "CAREER", ProfileStatusEnum.APPROVED),
                profile(STUDENT_ID, "MENTOR", ProfileStatusEnum.APPROVED),
                profile(1002L, "BASIC", ProfileStatusEnum.RETURNED)));

        List<ArchiveCandidateVO> candidates = service.listCandidates(YEAR, null);

        assertEquals(2, candidates.size());
        ArchiveCandidateVO first = candidates.get(0);
        assertEquals(1001L, first.getStudentId());
        assertTrue(first.isArchivable());
        assertFalse(first.isArchived());
        assertTrue(first.getIncompleteSections().isEmpty());
        assertEquals(6, first.getApprovedCount());

        ArchiveCandidateVO second = candidates.get(1);
        assertFalse(second.isArchivable());
        assertEquals(1, second.getSectionCount());
        // 基本信息被退回 + 其余五块未建档，六块都不算完成
        assertEquals(6, second.getIncompleteSections().size());
        assertEquals("已退回", second.getIncompleteSections().get(0).getStatusLabel());
        assertEquals("未建档", second.getIncompleteSections().get(1).getStatusLabel());
    }

    @Test
    @DisplayName("候选人：已归档学生标记为 archived，仍可重复提交（幂等）")
    void listCandidatesMarksArchived() {
        when(orgClassMapper.selectList(any())).thenReturn(List.of(orgClass("RJ2401", "软件2401", COUNSELOR_ID)));
        when(studentMapper.selectList(any())).thenReturn(List.of(student(STUDENT_ID, "2024010101", "李明", "RJ2401")));
        when(profileMapper.selectList(any())).thenReturn(lockedProfiles(STUDENT_ID));

        List<ArchiveCandidateVO> candidates = service.listCandidates(YEAR, null);

        assertEquals(1, candidates.size());
        assertTrue(candidates.get(0).isArchived());
        assertTrue(candidates.get(0).isArchivable());
        assertEquals(6, candidates.get(0).getLockedCount());
    }

    @Test
    @DisplayName("候选人：学生角色 403，跨班筛选 403")
    void listCandidatesRejectsOutOfScope() {
        UserContext.set(new CurrentUser(STUDENT_ID, UserRoleEnum.STU, false));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.listCandidates(YEAR, null));
        assertEquals(ResultCode.DATA_SCOPE_DENIED, ex.getResultCode());

        UserContext.set(new CurrentUser(COUNSELOR_ID, UserRoleEnum.COU, false));
        when(orgClassMapper.selectList(any())).thenReturn(List.of(orgClass("RJ2401", "软件2401", COUNSELOR_ID)));
        BusinessException cross = assertThrows(BusinessException.class,
                () -> service.listCandidates(YEAR, "DZ2401"));
        assertEquals(ResultCode.DATA_SCOPE_DENIED, cross.getResultCode());
    }

    @Test
    @DisplayName("归档：名单里有人未完成时整批拒绝，不写入任何一条")
    void archiveRejectsWholeBatchWhenAnyIncomplete() {
        stubSingleStudent();
        when(profileMapper.selectList(any())).thenReturn(List.of(
                profile(STUDENT_ID, "BASIC", ProfileStatusEnum.APPROVED),
                profile(STUDENT_ID, "ACADEMIC", ProfileStatusEnum.SUBMITTED)));

        ArchiveResultVO result = service.archive(dto(List.of(STUDENT_ID)));

        assertTrue(result.isRejected());
        assertEquals(0, result.getArchivedStudents());
        assertEquals(1, result.getStudents().size());
        assertEquals("INCOMPLETE", result.getStudents().get(0).getStatus());
        assertTrue(result.getStudents().get(0).getMissingSections().contains("学业发展"));
        assertTrue(updated.isEmpty());
        verify(changeLogMapper, never()).insert(any(ArcChangeLog.class));
        verify(auditLogMapper, never()).insert(any(SysAuditLog.class));
    }

    @Test
    @DisplayName("归档：六板块通过后转 LOCKED，完成标记保持为真，逐块写归档日志")
    void archiveLocksAllSections() {
        stubSingleStudent();
        when(profileMapper.selectList(any())).thenReturn(List.of(
                profile(STUDENT_ID, "BASIC", ProfileStatusEnum.APPROVED),
                profile(STUDENT_ID, "ACADEMIC", ProfileStatusEnum.APPROVED),
                profile(STUDENT_ID, "INTEREST", ProfileStatusEnum.APPROVED),
                profile(STUDENT_ID, "LIFE", ProfileStatusEnum.APPROVED),
                profile(STUDENT_ID, "CAREER", ProfileStatusEnum.APPROVED),
                profile(STUDENT_ID, "MENTOR", ProfileStatusEnum.APPROVED)));

        ArchiveResultVO result = service.archive(dto(List.of(STUDENT_ID)));

        assertFalse(result.isRejected());
        assertEquals(1, result.getArchivedStudents());
        assertEquals(6, result.getArchivedProfiles());
        assertEquals("ARCHIVED", result.getStudents().get(0).getStatus());
        assertEquals(6, updated.size());
        assertTrue(updated.stream().allMatch(item -> "LOCKED".equals(item.getStatus())));
        assertTrue(updated.stream().allMatch(item -> Boolean.TRUE.equals(item.getCompleted())));
        verify(changeLogMapper, times(6)).insert(any(ArcChangeLog.class));
        verify(auditLogMapper, times(1)).insert(any(SysAuditLog.class));
    }

    @Test
    @DisplayName("归档：已归档学生跳过，不重复写库")
    void archiveSkipsAlreadyLocked() {
        stubSingleStudent();
        when(profileMapper.selectList(any())).thenReturn(lockedProfiles(STUDENT_ID));

        ArchiveResultVO result = service.archive(dto(List.of(STUDENT_ID)));

        assertFalse(result.isRejected());
        assertEquals(0, result.getArchivedStudents());
        assertEquals(1, result.getSkippedStudents());
        assertTrue(updated.isEmpty());
        verify(changeLogMapper, never()).insert(any(ArcChangeLog.class));
    }

    @Test
    @DisplayName("归档：跨班学生返回 EC-40302")
    void archiveRejectsOtherClass() {
        when(orgClassMapper.selectList(any())).thenReturn(List.of(orgClass("RJ2401", "软件2401", COUNSELOR_ID)));
        when(studentMapper.selectBatchIds(any())).thenReturn(List.of(student(STUDENT_ID, "2024010101", "李明", "DZ2401")));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.archive(dto(List.of(STUDENT_ID))));

        assertEquals(ResultCode.DATA_SCOPE_DENIED, ex.getResultCode());
        assertTrue(updated.isEmpty());
    }

    @Test
    @DisplayName("归档：学生角色 403，空名单 400")
    void archiveValidatesRoleAndPayload() {
        BusinessException empty = assertThrows(BusinessException.class, () -> service.archive(dto(List.of())));
        assertEquals(ResultCode.PARAM_MISSING, empty.getResultCode());

        UserContext.set(new CurrentUser(STUDENT_ID, UserRoleEnum.STU, false));
        BusinessException denied = assertThrows(BusinessException.class,
                () -> service.archive(dto(List.of(STUDENT_ID))));
        assertEquals(ResultCode.DATA_SCOPE_DENIED, denied.getResultCode());
    }

    @Test
    @DisplayName("归档：并发下版本冲突返回 EC-40907")
    void archiveReportsVersionConflict() {
        stubSingleStudent();
        when(profileMapper.selectList(any())).thenReturn(List.of(
                profile(STUDENT_ID, "BASIC", ProfileStatusEnum.APPROVED),
                profile(STUDENT_ID, "ACADEMIC", ProfileStatusEnum.APPROVED),
                profile(STUDENT_ID, "INTEREST", ProfileStatusEnum.APPROVED),
                profile(STUDENT_ID, "LIFE", ProfileStatusEnum.APPROVED),
                profile(STUDENT_ID, "CAREER", ProfileStatusEnum.APPROVED),
                profile(STUDENT_ID, "MENTOR", ProfileStatusEnum.APPROVED)));
        when(profileMapper.updateById(any(ArcProfile.class))).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.archive(dto(List.of(STUDENT_ID))));

        assertEquals(ResultCode.VERSION_CONFLICT, ex.getResultCode());
    }

    private void stubSingleStudent() {
        when(orgClassMapper.selectList(any())).thenReturn(List.of(orgClass("RJ2401", "软件2401", COUNSELOR_ID)));
        when(studentMapper.selectBatchIds(any()))
                .thenReturn(List.of(student(STUDENT_ID, "2024010101", "李明", "RJ2401")));
    }

    private ArchiveBatchDTO dto(List<Long> studentIds) {
        ArchiveBatchDTO dto = new ArchiveBatchDTO();
        dto.setAcademicYear(YEAR);
        dto.setStudentIds(studentIds);
        return dto;
    }

    private OrgClass orgClass(String code, String name, Long counselorId) {
        OrgClass orgClass = new OrgClass();
        orgClass.setClassCode(code);
        orgClass.setClassName(name);
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

    private ArcProfile profile(Long studentId, String sectionCode, ProfileStatusEnum status) {
        ArcProfile profile = new ArcProfile();
        profile.setId(studentId * 10 + sectionCode.length());
        profile.setStudentId(studentId);
        profile.setSectionCode(sectionCode);
        profile.setAcademicYear(YEAR);
        profile.setStatus(status.name());
        profile.setCompleted(false);
        profile.setCompleteness(new BigDecimal("100.00"));
        profile.setVersion(0);
        return profile;
    }

    private List<ArcProfile> lockedProfiles(Long studentId) {
        List<ArcProfile> profiles = new ArrayList<>();
        for (String code : List.of("BASIC", "ACADEMIC", "INTEREST", "LIFE", "CAREER", "MENTOR")) {
            ArcProfile profile = profile(studentId, code, ProfileStatusEnum.LOCKED);
            profile.setCompleted(true);
            profiles.add(profile);
        }
        return profiles;
    }
}
