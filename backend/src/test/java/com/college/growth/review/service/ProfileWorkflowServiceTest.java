package com.college.growth.review.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.context.UserContext;
import com.college.growth.common.enums.ProfileStatusEnum;
import com.college.growth.common.enums.UserRoleEnum;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.field.entity.ArcFieldDef;
import com.college.growth.common.field.service.FieldDefService;
import com.college.growth.common.log.entity.ArcChangeLog;
import com.college.growth.common.log.entity.SysAuditLog;
import com.college.growth.common.log.mapper.ArcChangeLogMapper;
import com.college.growth.common.log.mapper.SysAuditLogMapper;
import com.college.growth.common.log.service.AuditLogService;
import com.college.growth.common.log.service.ChangeLogService;
import com.college.growth.common.org.service.StudentClassService;
import com.college.growth.common.profile.entity.ArcProfile;
import com.college.growth.common.profile.entity.ArcProfileItem;
import com.college.growth.common.profile.mapper.ArcProfileItemMapper;
import com.college.growth.common.profile.mapper.ArcProfileMapper;
import com.college.growth.common.profile.service.ProfileCompletenessService;
import com.college.growth.common.profile.service.ProfileItemService;
import com.college.growth.common.student.service.StudentMasterService;
import com.college.growth.common.result.ResultCode;
import com.college.growth.completion.service.impl.CompletionServiceImpl;
import com.college.growth.review.service.impl.ProfileWorkflowServiceImpl;
import java.math.BigDecimal;
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
 * 公共状态机单测（BR-S02 – BR-S06）。
 *
 * <p>这是六板块共用的唯一状态机实现，因此把「谁能改、什么状态能改、日志写几条」
 * 全部压在这里验证；板块测试只验证「有没有委托过来」。</p>
 */
@ExtendWith(MockitoExtension.class)
class ProfileWorkflowServiceTest {

    private static final String YEAR = "2025-2026-1";

    private static final long STUDENT_ID = 1001L;

    private static final long COUNSELOR_ID = 9001L;

    static {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ArcProfile.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                ArcProfileItem.class);
    }

    @Mock
    private ArcProfileMapper profileMapper;

    @Mock
    private ArcChangeLogMapper changeLogMapper;

    @Mock
    private SysAuditLogMapper auditLogMapper;

    @Mock
    private StudentClassService studentClassService;

    @Mock
    private FieldDefService fieldDefService;

    @Mock
    private ArcProfileItemMapper itemMapper;

    @Mock
    private StudentMasterService studentMasterService;

    private ProfileWorkflowServiceImpl workflow;

    @BeforeEach
    void setUp() {
        // 流转节点会重算完成度；这里不关心具体百分比，只保证计算链路不空指针。
        lenient().when(fieldDefService.listActive(anyString())).thenReturn(List.of());
        lenient().when(itemMapper.selectList(any())).thenReturn(List.of());
        workflow = new ProfileWorkflowServiceImpl(profileMapper,
                new ChangeLogService(changeLogMapper), new AuditLogService(auditLogMapper),
                studentClassService,
                new ProfileCompletenessService(fieldDefService, new ProfileItemService(itemMapper),
                        profileMapper),
                new CompletionServiceImpl(profileMapper, studentMasterService, studentClassService,
                        new AuditLogService(auditLogMapper)));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("提交：DRAFT 转 SUBMITTED，提交次数加一，变更日志与审计日志各一条")
    void submitMovesDraftToSubmitted() {
        UserContext.set(new CurrentUser(STUDENT_ID, UserRoleEnum.STU, false));
        ArcProfile profile = profile(ProfileStatusEnum.DRAFT);
        when(profileMapper.updateById(any(ArcProfile.class))).thenReturn(1);

        workflow.submit(profile, null);

        assertEquals(ProfileStatusEnum.SUBMITTED.name(), profile.getStatus());
        assertEquals(1, profile.getSubmitCount());
        assertNotNull(profile.getSubmittedAt());
        verify(changeLogMapper).insert(any(ArcChangeLog.class));
        verify(auditLogMapper).insert(any(SysAuditLog.class));
    }

    @Test
    @DisplayName("提交：已提交状态再提交返回 EC-40902")
    void submitRejectedWhenAlreadySubmitted() {
        UserContext.set(new CurrentUser(STUDENT_ID, UserRoleEnum.STU, false));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> workflow.submit(profile(ProfileStatusEnum.SUBMITTED), null));

        assertEquals(ResultCode.STATE_CONFLICT, ex.getResultCode());
        verify(profileMapper, never()).updateById(any(ArcProfile.class));
    }

    @Test
    @DisplayName("提交：辅导员不能代替学生提交，返回 EC-40302")
    void submitRejectedForCounselor() {
        UserContext.set(new CurrentUser(COUNSELOR_ID, UserRoleEnum.COU, false));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> workflow.submit(profile(ProfileStatusEnum.DRAFT), null));

        assertEquals(ResultCode.DATA_SCOPE_DENIED, ex.getResultCode());
    }

    @Test
    @DisplayName("撤回：本人 SUBMITTED 转 DRAFT，撤回次数加一")
    void withdrawMovesSubmittedToDraft() {
        UserContext.set(new CurrentUser(STUDENT_ID, UserRoleEnum.STU, false));
        ArcProfile profile = profile(ProfileStatusEnum.SUBMITTED);
        when(profileMapper.updateById(any(ArcProfile.class))).thenReturn(1);
        when(profileMapper.selectList(any())).thenReturn(List.of(profile));

        workflow.withdraw(profile);

        assertEquals(ProfileStatusEnum.DRAFT.name(), profile.getStatus());
        assertEquals(1, profile.getWithdrawCount());
        verify(changeLogMapper).insert(any(ArcChangeLog.class));
        verify(auditLogMapper).insert(any(SysAuditLog.class));
    }

    @Test
    @DisplayName("撤回：第二次撤回返回 EC-40903")
    void withdrawRejectedWhenAlreadyUsed() {
        UserContext.set(new CurrentUser(STUDENT_ID, UserRoleEnum.STU, false));
        ArcProfile profile = profile(ProfileStatusEnum.SUBMITTED);
        profile.setWithdrawCount(1);

        BusinessException ex = assertThrows(BusinessException.class, () -> workflow.withdraw(profile));

        assertEquals(ResultCode.WITHDRAW_EXCEEDED, ex.getResultCode());
    }

    @Test
    @DisplayName("撤回：非本人（含辅导员）返回 EC-40302")
    void withdrawRejectedForOthers() {
        UserContext.set(new CurrentUser(2002L, UserRoleEnum.STU, false));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> workflow.withdraw(profile(ProfileStatusEnum.SUBMITTED)));

        assertEquals(ResultCode.DATA_SCOPE_DENIED, ex.getResultCode());
    }

    @Test
    @DisplayName("审核通过：本班辅导员操作，转 APPROVED 并记录审核人")
    void approveByCounselorOfClass() {
        UserContext.set(new CurrentUser(COUNSELOR_ID, UserRoleEnum.COU, false));
        ArcProfile profile = profile(ProfileStatusEnum.SUBMITTED);
        when(studentClassService.isCounselorOf(STUDENT_ID, YEAR, COUNSELOR_ID)).thenReturn(true);
        when(profileMapper.updateById(any(ArcProfile.class))).thenReturn(1);
        when(profileMapper.selectList(any())).thenReturn(List.of(profile));

        workflow.approve(profile, "材料齐全");

        assertEquals(ProfileStatusEnum.APPROVED.name(), profile.getStatus());
        assertEquals(COUNSELOR_ID, profile.getReviewerId());
        assertNotNull(profile.getReviewedAt());
    }

    @Test
    @DisplayName("审核：非本班辅导员返回 EC-40302（BR-S03）")
    void approveRejectedForOtherClass() {
        UserContext.set(new CurrentUser(COUNSELOR_ID, UserRoleEnum.COU, false));
        when(studentClassService.isCounselorOf(STUDENT_ID, YEAR, COUNSELOR_ID)).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> workflow.approve(profile(ProfileStatusEnum.SUBMITTED), null));

        assertEquals(ResultCode.DATA_SCOPE_DENIED, ex.getResultCode());
        verify(profileMapper, never()).updateById(any(ArcProfile.class));
    }

    @Test
    @DisplayName("审核：管理员不参与业务审核，返回 EC-40302")
    void approveRejectedForAdmin() {
        UserContext.set(new CurrentUser(7001L, UserRoleEnum.ADMIN, true));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> workflow.approve(profile(ProfileStatusEnum.SUBMITTED), null));

        assertEquals(ResultCode.DATA_SCOPE_DENIED, ex.getResultCode());
    }

    @Test
    @DisplayName("审核通过：非 SUBMITTED 状态返回 EC-40902")
    void approveRejectedWhenNotSubmitted() {
        UserContext.set(new CurrentUser(COUNSELOR_ID, UserRoleEnum.COU, false));
        when(studentClassService.isCounselorOf(STUDENT_ID, YEAR, COUNSELOR_ID)).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> workflow.approve(profile(ProfileStatusEnum.DRAFT), null));

        assertEquals(ResultCode.STATE_CONFLICT, ex.getResultCode());
    }

    @Test
    @DisplayName("审核退回：理由不足 10 字返回 EC-42203 并带字段明细（BR-S04）")
    void rejectRequiresReason() {
        UserContext.set(new CurrentUser(COUNSELOR_ID, UserRoleEnum.COU, false));
        when(studentClassService.isCounselorOf(STUDENT_ID, YEAR, COUNSELOR_ID)).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> workflow.reject(profile(ProfileStatusEnum.SUBMITTED), "太短"));

        assertEquals(ResultCode.RETURN_REASON_TOO_SHORT, ex.getResultCode());
        assertEquals("reason", ex.getDetails().get(0).getField());
    }

    @Test
    @DisplayName("审核退回：理由合规转 RETURNED，理由写进日志")
    void rejectMovesToReturned() {
        UserContext.set(new CurrentUser(COUNSELOR_ID, UserRoleEnum.COU, false));
        ArcProfile profile = profile(ProfileStatusEnum.SUBMITTED);
        when(studentClassService.isCounselorOf(STUDENT_ID, YEAR, COUNSELOR_ID)).thenReturn(true);
        when(profileMapper.updateById(any(ArcProfile.class))).thenReturn(1);
        when(profileMapper.selectList(any())).thenReturn(List.of(profile));

        workflow.reject(profile, "紧急联系人电话与本人电话重复，请核对后重新提交");

        assertEquals(ProfileStatusEnum.RETURNED.name(), profile.getStatus());
        verify(changeLogMapper).insert(any(ArcChangeLog.class));
    }

    @Test
    @DisplayName("并发审核：乐观锁更新 0 行返回 EC-40907")
    void approveReportsVersionConflict() {
        UserContext.set(new CurrentUser(COUNSELOR_ID, UserRoleEnum.COU, false));
        when(studentClassService.isCounselorOf(STUDENT_ID, YEAR, COUNSELOR_ID)).thenReturn(true);
        when(profileMapper.updateById(any(ArcProfile.class))).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> workflow.approve(profile(ProfileStatusEnum.SUBMITTED), null));

        assertEquals(ResultCode.VERSION_CONFLICT, ex.getResultCode());
    }

    @Test
    @DisplayName("提交时重算完成度：字段值已在库中，百分比随状态流转一起更新")
    void submitRefreshesCompleteness() {
        UserContext.set(new CurrentUser(STUDENT_ID, UserRoleEnum.STU, false));
        ArcProfile profile = profile(ProfileStatusEnum.DRAFT);
        when(profileMapper.updateById(any(ArcProfile.class))).thenReturn(1);
        when(fieldDefService.listActive(anyString())).thenReturn(List.of(requiredDef()));
        when(itemMapper.selectList(any())).thenReturn(List.of(filledItem()));

        workflow.submit(profile, "学生提交");

        assertEquals(new BigDecimal("100.00"), profile.getCompleteness());
    }

    private ArcProfile profile(ProfileStatusEnum status) {
        ArcProfile profile = new ArcProfile();
        profile.setId(3001L);
        profile.setStudentId(STUDENT_ID);
        profile.setAcademicYear(YEAR);
        profile.setSectionCode("BASIC");
        profile.setStatus(status.name());
        profile.setCompleted(false);
        profile.setCompleteness(BigDecimal.ZERO);
        profile.setSubmitCount(0);
        profile.setWithdrawCount(0);
        profile.setVersion(0);
        return profile;
    }

    /** 一个必填、非推导字段，用于验证完成度口径。 */
    @Test
    void approvalRequiresConfirmedRequiredValue() {
        UserContext.set(new CurrentUser(COUNSELOR_ID, UserRoleEnum.COU, false));
        when(studentClassService.isCounselorOf(STUDENT_ID, YEAR, COUNSELOR_ID)).thenReturn(true);
        var item = filledItem();
        item.setConfirmStatus("PENDING");
        when(fieldDefService.listActive(anyString())).thenReturn(List.of(requiredDef()));
        when(itemMapper.selectList(any())).thenReturn(List.of(item));
        assertEquals(ResultCode.STATE_CONFLICT, assertThrows(BusinessException.class,
                () -> workflow.approve(profile(ProfileStatusEnum.SUBMITTED), null)).getResultCode());
        item.setConfirmStatus("REJECTED");
        item.setValueText(null);
        assertEquals(ResultCode.REQUIRED_FIELD_MISSING, assertThrows(BusinessException.class,
                () -> workflow.approve(profile(ProfileStatusEnum.SUBMITTED), null)).getResultCode());
    }

    private ArcFieldDef requiredDef() {
        ArcFieldDef def = new ArcFieldDef();
        def.setFieldCode("basic.emergencyPhone");
        def.setFieldType("TEXT");
        def.setSectionCode("BASIC");
        def.setRequired(1);
        def.setStatus(1);
        def.setValidator("EMERGENCY_TEL");
        return def;
    }

    private ArcProfileItem filledItem() {
        ArcProfileItem item = new ArcProfileItem();
        item.setProfileId(3001L);
        item.setFieldCode("basic.emergencyPhone");
        item.setValueText("13900139000");
        return item;
    }
}
