package com.college.growth.completion.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.context.UserContext;
import com.college.growth.common.enums.ProfileStatusEnum;
import com.college.growth.common.enums.SectionCodeEnum;
import com.college.growth.common.enums.UserRoleEnum;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.log.entity.SysAuditLog;
import com.college.growth.common.log.mapper.SysAuditLogMapper;
import com.college.growth.common.log.service.AuditLogService;
import com.college.growth.common.org.entity.OrgClass;
import com.college.growth.common.org.service.StudentClassService;
import com.college.growth.common.profile.entity.ArcProfile;
import com.college.growth.common.profile.mapper.ArcProfileMapper;
import com.college.growth.common.result.ResultCode;
import com.college.growth.common.student.entity.StuStudent;
import com.college.growth.common.student.service.StudentMasterService;
import com.college.growth.completion.service.impl.CompletionServiceImpl;
import com.college.growth.completion.vo.CompletionRefreshVO;
import com.college.growth.completion.vo.CompletionStatusVO;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 完成标记单测（BR-S10）。
 *
 * <p>完成判定是全平台唯一一份实现，因此「什么算完成」「什么时候写库」「谁能重算」
 * 三类口径全部压在这里验证；状态机测试只验证「有没有委托过来」。</p>
 */
@ExtendWith(MockitoExtension.class)
class CompletionServiceTest {

    private static final String YEAR = "2025-2026-1";

    private static final long STUDENT_ID = 1001L;

    private static final long COUNSELOR_ID = 9001L;

    static {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                ArcProfile.class);
    }

    @Mock
    private ArcProfileMapper profileMapper;

    @Mock
    private StudentMasterService studentMasterService;

    @Mock
    private StudentClassService studentClassService;

    @Mock
    private SysAuditLogMapper auditLogMapper;

    private CompletionServiceImpl completion;

    @BeforeEach
    void setUp() {
        completion = new CompletionServiceImpl(profileMapper, studentMasterService,
                studentClassService, new AuditLogService(auditLogMapper));
        lenient().when(studentClassService.findClass(anyLong(), anyString())).thenReturn(null);
        lenient().when(studentMasterService.find(anyLong())).thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("判定：六板块全部 APPROVED 算完成")
    void sixApprovedIsCompleted() {
        assertTrue(completion.isCompleted(profiles(ProfileStatusEnum.APPROVED, false)));
    }

    @Test
    @DisplayName("判定：APPROVED 与 LOCKED 混排也算完成（归档不能把标记算回未完成）")
    void approvedWithLockedIsCompleted() {
        List<ArcProfile> list = profiles(ProfileStatusEnum.APPROVED, false);
        list.get(0).setStatus(ProfileStatusEnum.LOCKED.name());
        list.get(1).setStatus(ProfileStatusEnum.LOCKED.name());

        assertTrue(completion.isCompleted(list));
    }

    @Test
    @DisplayName("判定：少建一块（五条记录）不算完成")
    void missingSectionIsNotCompleted() {
        List<ArcProfile> list = profiles(ProfileStatusEnum.APPROVED, false);
        list.remove(0);

        assertFalse(completion.isCompleted(list));
    }

    @Test
    @DisplayName("判定：有一块仍在 SUBMITTED 不算完成")
    void submittedSectionIsNotCompleted() {
        List<ArcProfile> list = profiles(ProfileStatusEnum.APPROVED, false);
        list.get(3).setStatus(ProfileStatusEnum.SUBMITTED.name());

        assertFalse(completion.isCompleted(list));
    }

    @Test
    @DisplayName("判定：同一板块重复建档不会把五块凑成六块")
    void duplicateSectionDoesNotCountTwice() {
        List<ArcProfile> list = new ArrayList<>();
        list.add(profile(STUDENT_ID, SectionCodeEnum.BASIC, ProfileStatusEnum.APPROVED, false));
        list.add(profile(STUDENT_ID, SectionCodeEnum.BASIC, ProfileStatusEnum.APPROVED, false));
        list.add(profile(STUDENT_ID, SectionCodeEnum.ACADEMIC, ProfileStatusEnum.APPROVED, false));
        list.add(profile(STUDENT_ID, SectionCodeEnum.INTEREST, ProfileStatusEnum.APPROVED, false));
        list.add(profile(STUDENT_ID, SectionCodeEnum.LIFE, ProfileStatusEnum.APPROVED, false));
        list.add(profile(STUDENT_ID, SectionCodeEnum.CAREER, ProfileStatusEnum.APPROVED, false));

        assertFalse(completion.isCompleted(list));
    }

    @Test
    @DisplayName("重算：标记由 false 翻成 true 时写库一次")
    void refreshWritesFlagWhenChanged() {
        when(profileMapper.selectList(any())).thenReturn(profiles(ProfileStatusEnum.APPROVED, false));

        boolean changed = completion.refresh(STUDENT_ID, YEAR);

        assertTrue(changed);
        verify(profileMapper, times(1)).update(any(), any());
    }

    @Test
    @DisplayName("重算：标记已一致时不写库，避免刷掉 updated_at")
    void refreshSkipsWriteWhenFlagUnchanged() {
        when(profileMapper.selectList(any())).thenReturn(profiles(ProfileStatusEnum.APPROVED, true));

        boolean changed = completion.refresh(STUDENT_ID, YEAR);

        assertFalse(changed);
        verify(profileMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("重算：学生未建档时不写库")
    void refreshSkipsWriteWhenNoProfile() {
        when(profileMapper.selectList(any())).thenReturn(List.of());

        assertFalse(completion.refresh(STUDENT_ID, YEAR));
        verify(profileMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("读取：逐板块固定六项，缺块显示「未建档」并进未完成清单")
    void statusListsAllSixSections() {
        UserContext.set(new CurrentUser(STUDENT_ID, UserRoleEnum.STU, false));
        ArcProfile basic = profile(STUDENT_ID, SectionCodeEnum.BASIC, ProfileStatusEnum.DRAFT, false);
        when(profileMapper.selectList(any())).thenReturn(List.of(basic));

        CompletionStatusVO vo = completion.statusOf(STUDENT_ID, YEAR);

        assertEquals(6, vo.getSections().size());
        assertEquals(1, vo.getCreatedSectionCount());
        assertEquals(0, vo.getCompletedSectionCount());
        // 已建档但仍是草稿，六块都还没完成
        assertEquals(6, vo.getPendingSections().size());
        assertFalse(vo.isCompleted());
        assertEquals("basic", vo.getSections().get(0).getSectionCode());
        assertEquals("草稿", vo.getSections().get(0).getStatusLabel());
        assertEquals("未建档", vo.getSections().get(1).getStatusLabel());
        assertNull(vo.getSections().get(1).getStatus());
    }

    @Test
    @DisplayName("读取：带出学号、姓名与班级名")
    void statusEnrichesStudentProfile() {
        UserContext.set(new CurrentUser(COUNSELOR_ID, UserRoleEnum.COU, false));
        when(studentClassService.isCounselorOf(STUDENT_ID, YEAR, COUNSELOR_ID)).thenReturn(true);
        when(profileMapper.selectList(any())).thenReturn(profiles(ProfileStatusEnum.APPROVED, true));
        StuStudent student = new StuStudent();
        student.setId(STUDENT_ID);
        student.setStudentNo("2023010101");
        student.setName("张同学");
        student.setClassCode("RJ2401");
        when(studentMasterService.find(STUDENT_ID)).thenReturn(Optional.of(student));
        OrgClass orgClass = new OrgClass();
        orgClass.setClassCode("RJ2401");
        orgClass.setClassName("软件工程2401班");
        when(studentClassService.findClass(STUDENT_ID, YEAR)).thenReturn(orgClass);

        CompletionStatusVO vo = completion.statusOf(STUDENT_ID, YEAR);

        assertTrue(vo.isCompleted());
        assertEquals("张同学", vo.getStudentName());
        assertEquals("2023010101", vo.getStudentNo());
        assertEquals("软件工程2401班", vo.getClassName());
        assertEquals(6, vo.getCompletedSectionCount());
        assertTrue(vo.getPendingSections().isEmpty());
    }

    @Test
    @DisplayName("读取：学生看他人完成标记返回 EC-40302")
    void statusRejectedForOtherStudent() {
        UserContext.set(new CurrentUser(2002L, UserRoleEnum.STU, false));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> completion.statusOf(STUDENT_ID, YEAR));

        assertEquals(ResultCode.DATA_SCOPE_DENIED, ex.getResultCode());
    }

    @Test
    @DisplayName("读取：非本班辅导员返回 EC-40302")
    void statusRejectedForOtherClassCounselor() {
        UserContext.set(new CurrentUser(COUNSELOR_ID, UserRoleEnum.COU, false));
        when(studentClassService.isCounselorOf(STUDENT_ID, YEAR, COUNSELOR_ID)).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> completion.statusOf(STUDENT_ID, YEAR));

        assertEquals(ResultCode.DATA_SCOPE_DENIED, ex.getResultCode());
    }

    @Test
    @DisplayName("读取：管理员无临时授权返回 EC-40302")
    void statusRejectedForAdminWithoutGrant() {
        UserContext.set(new CurrentUser(7001L, UserRoleEnum.ADMIN, false));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> completion.statusOf(STUDENT_ID, YEAR));

        assertEquals(ResultCode.DATA_SCOPE_DENIED, ex.getResultCode());
    }

    @Test
    @DisplayName("手动重算：学生不能触发，返回 EC-40302")
    void manualRefreshRejectedForStudent() {
        UserContext.set(new CurrentUser(STUDENT_ID, UserRoleEnum.STU, false));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> completion.manualRefresh(STUDENT_ID, YEAR));

        assertEquals(ResultCode.DATA_SCOPE_DENIED, ex.getResultCode());
        verify(profileMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("手动重算：整学年重算仅持临时授权的管理员可执行")
    void manualRefreshWholeYearRejectedForCounselor() {
        UserContext.set(new CurrentUser(COUNSELOR_ID, UserRoleEnum.COU, false));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> completion.manualRefresh(null, YEAR));

        assertEquals(ResultCode.DATA_SCOPE_DENIED, ex.getResultCode());
    }

    @Test
    @DisplayName("手动重算：本班辅导员重算单人，写一条 COMPLETION 审计日志")
    void manualRefreshWritesAudit() {
        UserContext.set(new CurrentUser(COUNSELOR_ID, UserRoleEnum.COU, false));
        when(studentClassService.isCounselorOf(STUDENT_ID, YEAR, COUNSELOR_ID)).thenReturn(true);
        when(profileMapper.selectList(any())).thenReturn(profiles(ProfileStatusEnum.APPROVED, false));

        CompletionRefreshVO vo = completion.manualRefresh(STUDENT_ID, YEAR);

        assertEquals(1, vo.getCheckedStudents());
        assertEquals(1, vo.getCompletedStudents());
        assertEquals(1, vo.getChangedStudents());
        assertEquals("MANUAL", vo.getTrigger());
        verify(auditLogMapper).insert(any(SysAuditLog.class));
    }

    @Test
    @DisplayName("整学年重算：统计判定数、完成数与标记变化数，只为变化的学生写库")
    void refreshAcademicYearCountsChanges() {
        List<ArcProfile> rows = new ArrayList<>(profiles(ProfileStatusEnum.APPROVED, false));
        for (SectionCodeEnum section : SectionCodeEnum.values()) {
            rows.add(profile(1002L, section, ProfileStatusEnum.DRAFT, false));
        }
        when(profileMapper.selectList(any())).thenReturn(rows);

        CompletionRefreshVO vo = completion.refreshAcademicYear(YEAR, "SCHEDULED");

        assertEquals(2, vo.getCheckedStudents());
        assertEquals(1, vo.getCompletedStudents());
        assertEquals(1, vo.getChangedStudents());
        verify(profileMapper, times(1)).update(any(), any());
    }

    @Test
    @DisplayName("整学年重算：空学年直接返回全零，不抛异常")
    void refreshAcademicYearHandlesEmptyYear() {
        when(profileMapper.selectList(any())).thenReturn(List.of());

        CompletionRefreshVO vo = completion.refreshAcademicYear(YEAR, "SCHEDULED");

        assertEquals(0, vo.getCheckedStudents());
        assertEquals(0, vo.getCompletedStudents());
        assertEquals(0, vo.getChangedStudents());
    }

    /** 六板块齐全的实例集合，状态统一设置。 */
    private List<ArcProfile> profiles(ProfileStatusEnum status, boolean completed) {
        List<ArcProfile> list = new ArrayList<>();
        for (SectionCodeEnum section : SectionCodeEnum.values()) {
            list.add(profile(STUDENT_ID, section, status, completed));
        }
        return list;
    }

    private ArcProfile profile(Long studentId, SectionCodeEnum section, ProfileStatusEnum status,
                               boolean completed) {
        ArcProfile profile = new ArcProfile();
        profile.setId(studentId * 10 + section.getOrder());
        profile.setStudentId(studentId);
        profile.setAcademicYear(YEAR);
        profile.setSectionCode(section.name());
        profile.setStatus(status.name());
        profile.setCompleted(completed);
        return profile;
    }
}
