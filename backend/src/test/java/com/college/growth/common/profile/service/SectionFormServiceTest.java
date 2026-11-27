package com.college.growth.common.profile.service;

import static com.college.growth.common.enums.SectionCodeEnum.ACADEMIC;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.context.UserContext;
import com.college.growth.common.enums.UserRoleEnum;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.field.entity.ArcFieldDef;
import com.college.growth.common.field.service.FieldDefService;
import com.college.growth.common.field.validation.FieldValidatorRegistry;
import com.college.growth.common.field.validation.validator.TextLengthValidator;
import com.college.growth.common.log.service.ChangeLogService;
import com.college.growth.common.org.service.StudentClassService;
import com.college.growth.common.profile.entity.ArcProfile;
import com.college.growth.common.profile.entity.ArcProfileItem;
import com.college.growth.common.profile.mapper.ArcProfileMapper;
import com.college.growth.common.result.ResultCode;
import com.college.growth.review.service.ProfileWorkflowService;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SectionFormServiceTest {
    private static final long STUDENT = 1001L;
    private static final String YEAR = "2025-2026-1";
    private static final String GOAL = "本学期每周完成两套线性代数练习并记录错题原因";
    private final ProfileLocator locator = mock(ProfileLocator.class);
    private final ArcProfileMapper profiles = mock(ArcProfileMapper.class);
    private final ProfileItemService items = mock(ProfileItemService.class);
    private final FieldDefService defs = mock(FieldDefService.class);
    private final ProfileWorkflowService workflow = mock(ProfileWorkflowService.class);
    private final ChangeLogService history = mock(ChangeLogService.class);
    private final SectionFormService service = new SectionFormService(locator, profiles, items, defs,
            new FieldValidatorRegistry(List.of(new TextLengthValidator())), workflow,
            mock(StudentClassService.class, CALLS_REAL_METHODS), history,
            new ProfileCompletenessService(defs, items, profiles));

    @BeforeEach
    void studentSession() {
        UserContext.set(new CurrentUser(STUDENT, UserRoleEnum.STU, false));
    }

    @AfterEach
    void clearSession() {
        UserContext.clear();
    }

    @Test
    void firstSaveCreatesScopedDraftUpdatesCompletenessAndRecordsChange() {
        var values = new HashMap<String, ArcProfileItem>();
        when(items.loadByProfile(null)).thenReturn(values);
        when(defs.listActive("ACADEMIC")).thenReturn(List.of(goalDef()));
        when(profiles.insert(any(ArcProfile.class))).thenAnswer(call -> {
            call.<ArcProfile>getArgument(0).setId(7L);
            return 1;
        });
        when(profiles.updateById(any(ArcProfile.class))).thenReturn(1);

        service.save(STUDENT, YEAR, ACADEMIC, Map.of("goalShort", GOAL));

        var captured = ArgumentCaptor.forClass(ArcProfile.class);
        verify(profiles).insert(captured.capture());
        var profile = captured.getValue();
        assertEquals(STUDENT, profile.getStudentId());
        assertEquals(YEAR, profile.getAcademicYear());
        assertEquals("ACADEMIC", profile.getSectionCode());
        assertEquals("DRAFT", profile.getStatus());
        assertEquals(new BigDecimal("100.00"), profile.getCompleteness());
        var item = values.get("academic.goalShort");
        assertEquals(7L, item.getProfileId());
        assertEquals(STUDENT, item.getUpdatedBy());
        assertEquals(GOAL, item.getValueText());
        verify(items).save(item);
        verify(history).record(profile, ChangeLogService.BIZ_FIELD_EDIT, "academic.goalShort",
                null, GOAL, null, null, null);
        verify(locator).find(STUDENT, "ACADEMIC", YEAR);
    }

    @Test
    void detailReadsStoredValueAndSubmissionUsesSameProfile() {
        var profile = existing("DRAFT");
        var item = new ArcProfileItem();
        item.setValueText(GOAL);
        when(items.loadByProfile(7L)).thenReturn(Map.of("academic.goalShort", item));
        when(defs.listActive("ACADEMIC")).thenReturn(List.of(goalDef()));

        var view = service.detail(STUDENT, YEAR, ACADEMIC);
        assertEquals(Map.of("goalShort", GOAL), view.fields());
        assertTrue(view.editable());
        assertTrue(view.fieldMeta().get(0).getRequired());
        service.submit(STUDENT, YEAR, ACADEMIC);
        verify(workflow).submit(profile, "学生提交");
    }

    @Test
    void missingRequiredValueBlocksSubmission() {
        existing("DRAFT");
        when(defs.listActive("ACADEMIC")).thenReturn(List.of(goalDef()));
        var error = assertThrows(BusinessException.class, () -> service.submit(STUDENT, YEAR, ACADEMIC));
        assertEquals(ResultCode.REQUIRED_FIELD_MISSING, error.getResultCode());
        assertEquals("academic.goalShort", error.getDetails().get(0).getField());
        verifyNoInteractions(workflow);
    }

    @Test
    void submittedProfileRejectsDraftWrites() {
        existing("SUBMITTED");
        var error = assertThrows(BusinessException.class,
                () -> service.save(STUDENT, YEAR, ACADEMIC, Map.of("goalShort", GOAL)));
        assertEquals(ResultCode.STATE_CONFLICT, error.getResultCode());
        verify(items, never()).save(any());
        verifyNoInteractions(profiles, history);
    }

    @Test
    void studentCannotAccessAnotherStudentsForm() {
        assertEquals(ResultCode.DATA_SCOPE_DENIED, assertThrows(BusinessException.class,
                () -> service.detail(1002L, YEAR, ACADEMIC)).getResultCode());
        assertEquals(ResultCode.DATA_SCOPE_DENIED, assertThrows(BusinessException.class,
                () -> service.save(1002L, YEAR, ACADEMIC, Map.of("goalShort", GOAL))).getResultCode());
        assertEquals(ResultCode.DATA_SCOPE_DENIED, assertThrows(BusinessException.class,
                () -> service.submit(1002L, YEAR, ACADEMIC)).getResultCode());
        verifyNoInteractions(locator, profiles, items, workflow, history);
    }

    private ArcProfile existing(String status) {
        var profile = new ArcProfile();
        profile.setId(7L);
        profile.setStatus(status);
        profile.setStudentId(STUDENT);
        profile.setSectionCode("ACADEMIC");
        profile.setAcademicYear(YEAR);
        when(locator.find(STUDENT, "ACADEMIC", YEAR)).thenReturn(profile);
        return profile;
    }

    private ArcFieldDef goalDef() {
        var def = new ArcFieldDef();
        def.setFieldCode("academic.goalShort");
        def.setFieldType("TEXT");
        def.setRequired(1);
        def.setVisibleRoles("STU,COU");
        def.setSource("STU");
        def.setEditableRoles("STU");
        def.setValidator("TEXT_LEN");
        def.setRuleJson("{\"minLen\":20,\"maxLen\":200}");
        return def;
    }
}
