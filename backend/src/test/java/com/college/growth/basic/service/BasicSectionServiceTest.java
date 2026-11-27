package com.college.growth.basic.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.college.growth.basic.common.BasicConstants;
import com.college.growth.basic.dto.BasicSectionSaveDTO;
import com.college.growth.basic.service.impl.BasicSectionServiceImpl;
import com.college.growth.basic.vo.BasicSectionVO;
import com.college.growth.basic.vo.SensitiveFieldVO;
import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.context.UserContext;
import com.college.growth.common.enums.ProfileStatusEnum;
import com.college.growth.common.enums.UserRoleEnum;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.field.entity.ArcFieldDef;
import com.college.growth.common.field.service.FieldDefService;
import com.college.growth.common.field.validation.FieldValidatorRegistry;
import com.college.growth.common.field.validation.validator.DerivedFieldValidator;
import com.college.growth.common.field.validation.validator.EmergencyTelValidator;
import com.college.growth.common.field.validation.validator.EnumValidator;
import com.college.growth.common.field.validation.validator.IdCardValidator;
import com.college.growth.common.field.validation.validator.MobileValidator;
import com.college.growth.common.field.validation.validator.PersonNameValidator;
import com.college.growth.common.field.validation.validator.StudentNoValidator;
import com.college.growth.common.field.validation.validator.TextLengthValidator;
import com.college.growth.common.log.entity.ArcChangeLog;
import com.college.growth.common.log.entity.SysAuditLog;
import com.college.growth.common.log.mapper.ArcChangeLogMapper;
import com.college.growth.common.log.mapper.SysAuditLogMapper;
import com.college.growth.common.log.service.AuditLogService;
import com.college.growth.common.log.service.ChangeLogService;
import com.college.growth.common.org.mapper.OrgClassMapper;
import com.college.growth.common.org.service.StudentClassService;
import com.college.growth.common.profile.entity.ArcProfile;
import com.college.growth.common.profile.entity.ArcProfileItem;
import com.college.growth.common.profile.mapper.ArcProfileItemMapper;
import com.college.growth.common.profile.mapper.ArcProfileMapper;
import com.college.growth.common.profile.service.PendingFieldService;
import com.college.growth.common.profile.service.ProfileCompletenessService;
import com.college.growth.common.profile.service.ProfileItemService;
import com.college.growth.common.profile.service.ProfileLocator;
import com.college.growth.common.profile.vo.PendingFieldVO;
import com.college.growth.common.result.ResultCode;
import com.college.growth.common.student.service.StudentSyncService;
import com.college.growth.review.service.ProfileWorkflowService;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 板块一业务单测：字段权限、校验、待确认区写入、完成度，以及「状态流转委托给公共模块」。
 *
 * <p>状态机自身的规则在 {@code ProfileWorkflowServiceTest} 覆盖；这里用 mock 验证板块一
 * 不再自己改状态——否则六个板块会各长出一份状态机。Mapper 全部 mock，不依赖数据库。</p>
 */
@ExtendWith(MockitoExtension.class)
class BasicSectionServiceTest {

    private static final String YEAR = "2025-2026-1";

    private static final long STUDENT_ID = 1001L;

    private static final long COUNSELOR_ID = 9001L;

    private static final String VALID_ID_CARD = "330106200401010012";

    static {
        // Lambda 条件构造器依赖 MP 实体元数据缓存；纯单测没有 Spring 容器，需手动初始化。
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ArcProfile.class);
        TableInfoHelper.initTableInfo(assistant, ArcProfileItem.class);
    }

    @Mock
    private ArcProfileMapper profileMapper;

    @Mock
    private ArcProfileItemMapper itemMapper;

    @Mock
    private ArcChangeLogMapper changeLogMapper;

    @Mock
    private SysAuditLogMapper auditLogMapper;

    @Mock
    private OrgClassMapper orgClassMapper;

    @Mock
    private FieldDefService fieldDefService;

    @Mock
    private ProfileWorkflowService workflowService;

    @Mock
    private PendingFieldService pendingFieldService;

    @Mock
    private StudentSyncService studentSyncService;

    @Mock
    private StudentClassService studentClassService;

    private BasicSectionServiceImpl service;

    @BeforeEach
    void setUp() {
        FieldValidatorRegistry registry = new FieldValidatorRegistry(List.of(
                new PersonNameValidator(), new StudentNoValidator(), new IdCardValidator(),
                new MobileValidator(), new EnumValidator(), new TextLengthValidator(),
                new EmergencyTelValidator(), new DerivedFieldValidator()));
        service = new BasicSectionServiceImpl(profileMapper, new ProfileLocator(profileMapper),
                new ProfileItemService(itemMapper), orgClassMapper, fieldDefService, registry,
                new ChangeLogService(changeLogMapper), new AuditLogService(auditLogMapper),
                workflowService, pendingFieldService,
                new ProfileCompletenessService(fieldDefService, new ProfileItemService(itemMapper),
                        profileMapper),
                studentSyncService, studentClassService);
        UserContext.set(new CurrentUser(STUDENT_ID, UserRoleEnum.STU, false));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("管理员默认看不到业务字段：只返回空视图")
    void detailReturnsEmptyViewForAdmin() {
        UserContext.set(new CurrentUser(COUNSELOR_ID, UserRoleEnum.ADMIN, false));
        when(fieldDefService.listActive(BasicConstants.SECTION_CODE)).thenReturn(List.of(nameDef()));

        BasicSectionVO vo = service.detail(STUDENT_ID, YEAR);

        assertTrue(vo.getFields().isEmpty());
        assertTrue(vo.getFieldMeta().isEmpty());
        assertFalse(vo.getEditable());
        assertEquals(ProfileStatusEnum.DRAFT.name(), vo.getStatus());
    }

    @Test
    @DisplayName("保存：管理员只能配置字段权限，内容写接口返回 EC-40303")
    void saveRejectsAdminContentEdit() {
        UserContext.set(new CurrentUser(COUNSELOR_ID, UserRoleEnum.ADMIN, true));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.save(STUDENT_ID, dto(Map.of("name", "林清和"))));

        assertEquals(ResultCode.PERMISSION_DENIED, ex.getResultCode());
        verify(fieldDefService, never()).listActive(anyString());
    }

    @Test
    @DisplayName("保存：姓名配置为仅老师后，学生保存被拒（EC-40301）")
    void saveHonorsTeacherOnlyPermission() {
        ArcFieldDef name = nameDef();
        name.setEditableRoles("COU");
        when(fieldDefService.listActive(BasicConstants.SECTION_CODE)).thenReturn(List.of(name));
        when(profileMapper.selectOne(any())).thenReturn(profile(ProfileStatusEnum.DRAFT));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.save(STUDENT_ID, dto(Map.of("name", "林清和"))));

        assertEquals(ResultCode.FIELD_NOT_EDITABLE, ex.getResultCode());
        verify(itemMapper, never()).insert(any(ArcProfileItem.class));
    }

    @Test
    @DisplayName("保存：姓名配置为仅老师后，辅导员可以保存")
    void saveAllowsCounselorWhenConfigured() {
        UserContext.set(new CurrentUser(COUNSELOR_ID, UserRoleEnum.COU, false));
        ArcFieldDef name = nameDef();
        name.setEditableRoles("COU");
        when(fieldDefService.listActive(BasicConstants.SECTION_CODE)).thenReturn(List.of(name));
        when(profileMapper.selectOne(any())).thenReturn(profile(ProfileStatusEnum.DRAFT));
        when(itemMapper.selectList(any())).thenReturn(List.of());
        when(profileMapper.updateById(any(ArcProfile.class))).thenReturn(1);

        service.save(STUDENT_ID, dto(Map.of("name", "林清和")));

        verify(itemMapper).insert(any(ArcProfileItem.class));
    }

    @Test
    @DisplayName("保存：越权改学校维护字段返回 403 并带字段明细")
    void saveRejectsSchoolMaintainedField() {
        when(fieldDefService.listActive(BasicConstants.SECTION_CODE)).thenReturn(List.of(studentNoDef()));
        when(profileMapper.selectOne(any())).thenReturn(profile(ProfileStatusEnum.DRAFT));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.save(STUDENT_ID, dto(Map.of("studentNo", "2023010101"))));

        assertEquals(ResultCode.FIELD_NOT_EDITABLE, ex.getResultCode());
        assertEquals("basic.studentNo", ex.getDetails().get(0).getField());
    }

    @Test
    @DisplayName("保存：已提交状态下拒绝写入")
    void saveRejectsWhenSubmitted() {
        when(fieldDefService.listActive(BasicConstants.SECTION_CODE)).thenReturn(List.of(nameDef()));
        when(profileMapper.selectOne(any())).thenReturn(profile(ProfileStatusEnum.SUBMITTED));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.save(STUDENT_ID, dto(Map.of("name", "林清和"))));

        assertEquals(ResultCode.STATE_CONFLICT, ex.getResultCode());
        verify(itemMapper, never()).insert(any(ArcProfileItem.class));
    }

    @Test
    @DisplayName("保存：STU_CFM 首填进入候选区并标记待确认")
    void firstFillOfConfirmableFieldIsPending() {
        ArcFieldDef phone = confirmableDef("basic.emergencyPhone", "紧急联系电话", "EMERGENCY_TEL");
        when(fieldDefService.listActive(BasicConstants.SECTION_CODE)).thenReturn(List.of(phone));
        when(profileMapper.selectOne(any())).thenReturn(profile(ProfileStatusEnum.DRAFT));
        when(itemMapper.selectList(any())).thenReturn(List.of());
        when(profileMapper.updateById(any(ArcProfile.class))).thenReturn(1);

        service.save(STUDENT_ID, dto(Map.of("emergencyPhone", "13800138000")));

        verify(itemMapper).insert(org.mockito.ArgumentMatchers.<ArcProfileItem>argThat(item ->
                item.getValueText() == null && "13800138000".equals(item.getPendingText())
                        && BasicConstants.CONFIRM_PENDING.equals(item.getConfirmStatus())));
        verify(changeLogMapper).insert(any(ArcChangeLog.class));
    }

    @Test
    @DisplayName("提交：必填缺失返回 422 并列出缺失字段，不流转状态")
    void submitReportsMissingRequiredFields() {
        when(fieldDefService.listActive(BasicConstants.SECTION_CODE)).thenReturn(List.of(nameDef()));
        when(profileMapper.selectOne(any())).thenReturn(profile(ProfileStatusEnum.DRAFT));
        when(itemMapper.selectList(any())).thenReturn(List.of());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.submit(STUDENT_ID, YEAR));

        assertEquals(ResultCode.REQUIRED_FIELD_MISSING, ex.getResultCode());
        assertEquals("basic.name", ex.getDetails().get(0).getField());
        verify(workflowService, never()).submit(any(), anyString());
    }

    @Test
    @DisplayName("提交：校验通过后委托公共状态机，板块自己不写状态")
    void submitDelegatesToWorkflow() {
        when(fieldDefService.listActive(BasicConstants.SECTION_CODE)).thenReturn(List.of(nameDef()));
        ArcProfile profile = profile(ProfileStatusEnum.DRAFT);
        when(profileMapper.selectOne(any())).thenReturn(profile);
        when(itemMapper.selectList(any())).thenReturn(List.of(item("basic.name", "林清和")));

        service.submit(STUDENT_ID, YEAR);

        verify(workflowService).submit(eq(profile), eq("学生提交"));
        assertEquals(ProfileStatusEnum.DRAFT.name(), profile.getStatus());
    }

    @Test
    @DisplayName("撤回：直接委托公共状态机，板块不重复判断次数")
    void withdrawDelegatesToWorkflow() {
        ArcProfile profile = profile(ProfileStatusEnum.SUBMITTED);
        when(profileMapper.selectOne(any())).thenReturn(profile);

        service.withdraw(STUDENT_ID, YEAR);

        verify(workflowService).withdraw(profile);
    }

    @Test
    @DisplayName("待确认处理：确认与驳回都委托公共待确认服务")
    void pendingFieldOperationsDelegateToCommonService() {
        PendingFieldVO vo = new PendingFieldVO();
        vo.setFieldCode("basic.phone");
        vo.setConfirmStatus("APPROVED");
        when(pendingFieldService.confirm(STUDENT_ID, BasicConstants.SECTION_CODE, "basic.phone", YEAR))
                .thenReturn(vo);

        assertEquals(vo, service.confirmField(STUDENT_ID, "basic.phone", YEAR));

        service.rejectField(STUDENT_ID, "basic.phone", YEAR);
        verify(pendingFieldService).reject(STUDENT_ID, BasicConstants.SECTION_CODE, "basic.phone", YEAR);
    }

    @Test
    @DisplayName("敏感字段查看：学生无权，返回 403")
    void revealDeniedForStudent() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.reveal(STUDENT_ID, "basic.idCard", YEAR));

        assertEquals(ResultCode.DATA_SCOPE_DENIED, ex.getResultCode());
    }

    @Test
    @DisplayName("敏感字段查看：辅导员可见明文，并同时写变更日志与审计日志")
    void revealWritesBothLogs() {
        UserContext.set(new CurrentUser(COUNSELOR_ID, UserRoleEnum.COU, false));
        when(fieldDefService.getByCode("basic.idCard")).thenReturn(idCardDef());
        when(profileMapper.selectOne(any())).thenReturn(profile(ProfileStatusEnum.SUBMITTED));
        when(itemMapper.selectOne(any())).thenReturn(item("basic.idCard", VALID_ID_CARD));

        SensitiveFieldVO vo = service.reveal(STUDENT_ID, "basic.idCard", YEAR);

        assertEquals(VALID_ID_CARD, vo.getValue());
        verify(changeLogMapper).insert(any(ArcChangeLog.class));
        verify(auditLogMapper).insert(any(SysAuditLog.class));
    }

    @Test
    @DisplayName("完成度：专业由班级推导，不计入必填统计")
    void completenessIgnoresDerivedField() {
        ArcFieldDef major = def("basic.majorId", "专业", "SELECT", true, "SYS", "", "DERIVED", null);
        ArcFieldDef emergencyPhone = confirmableDef("basic.emergencyPhone", "紧急联系电话", "EMERGENCY_TEL");
        when(fieldDefService.listActive(BasicConstants.SECTION_CODE))
                .thenReturn(List.of(emergencyPhone, major));
        ArcProfile profile = profile(ProfileStatusEnum.DRAFT);
        when(profileMapper.selectOne(any())).thenReturn(profile);
        when(itemMapper.selectList(any())).thenReturn(List.of());
        when(itemMapper.insert(any(ArcProfileItem.class))).thenReturn(1);
        when(profileMapper.updateById(any(ArcProfile.class))).thenReturn(1);

        service.save(STUDENT_ID, dto(Map.of("emergencyPhone", "13800138000")));

        assertEquals(new BigDecimal("100.00"), profile.getCompleteness());
    }

    @Test
    @DisplayName("并发保存：乐观锁更新 0 行时返回 EC-40907")
    void saveReportsVersionConflict() {
        when(fieldDefService.listActive(BasicConstants.SECTION_CODE)).thenReturn(List.of(nameDef()));
        when(profileMapper.selectOne(any())).thenReturn(profile(ProfileStatusEnum.DRAFT));
        when(itemMapper.selectList(any())).thenReturn(List.of(item("basic.name", "林清和")));
        when(profileMapper.updateById(any(ArcProfile.class))).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.save(STUDENT_ID, dto(Map.of("name", "林清和"))));

        assertEquals(ResultCode.VERSION_CONFLICT, ex.getResultCode());
    }

    @Test
    @DisplayName("详情：已退回时带回退回理由与时间（BR-S04 理由对学生可见）")
    void detailReturnsReviewReason() {
        when(fieldDefService.listActive(BasicConstants.SECTION_CODE)).thenReturn(List.of(nameDef()));
        when(profileMapper.selectOne(any())).thenReturn(profile(ProfileStatusEnum.RETURNED));
        when(itemMapper.selectList(any())).thenReturn(List.of());
        ArcChangeLog note = new ArcChangeLog();
        note.setReason("请补充紧急联系人关系说明，并核对宿舍号格式");
        note.setCreatedAt(java.time.LocalDateTime.of(2026, 11, 26, 20, 15));
        when(changeLogMapper.selectOne(any())).thenReturn(note);

        BasicSectionVO vo = service.detail(STUDENT_ID, YEAR);

        assertEquals(ProfileStatusEnum.RETURNED.name(), vo.getStatus());
        assertEquals("请补充紧急联系人关系说明，并核对宿舍号格式", vo.getReviewReason());
        assertNotNull(vo.getReviewedAt());
    }

    // ------------------------------------------------------------------
    // 测试数据
    // ------------------------------------------------------------------

    private ArcProfile profile(ProfileStatusEnum status) {
        ArcProfile profile = new ArcProfile();
        profile.setId(3001L);
        profile.setStudentId(STUDENT_ID);
        profile.setAcademicYear(YEAR);
        profile.setSectionCode(BasicConstants.SECTION_CODE);
        profile.setStatus(status.name());
        profile.setCompleted(false);
        profile.setCompleteness(BigDecimal.ZERO);
        profile.setSubmitCount(0);
        profile.setWithdrawCount(0);
        profile.setVersion(0);
        return profile;
    }

    private BasicSectionSaveDTO dto(Map<String, Object> fields) {
        BasicSectionSaveDTO dto = new BasicSectionSaveDTO();
        dto.setAcademicYear(YEAR);
        dto.setFields(new LinkedHashMap<>(fields));
        return dto;
    }

    private ArcProfileItem item(String fieldCode, String value) {
        ArcProfileItem item = new ArcProfileItem();
        item.setId(4001L);
        item.setProfileId(3001L);
        item.setFieldCode(fieldCode);
        item.setValueText(value);
        item.setConfirmStatus(BasicConstants.CONFIRM_NONE);
        return item;
    }

    private ArcFieldDef nameDef() {
        return def("basic.name", "姓名", "TEXT", true, "SIS", "STU", "PERSON_NAME", "{\"minLen\":2,\"maxLen\":32}");
    }

    private ArcFieldDef studentNoDef() {
        return def("basic.studentNo", "学号", "TEXT", true, "SIS", "", "STUDENT_NO", "{\"length\":10}");
    }

    private ArcFieldDef idCardDef() {
        ArcFieldDef def = def("basic.idCard", "身份证号", "TEXT", true, "SIS", "", "ID_CARD", null);
        def.setSensitiveLevel(2);
        return def;
    }

    private ArcFieldDef confirmableDef(String code, String name, String validator) {
        return def(code, name, "TEXT", true, "STU_CFM", "STU,COU", validator, null);
    }

    private ArcFieldDef def(String code, String name, String type, boolean required, String source,
                            String editableRoles, String validator, String ruleJson) {
        ArcFieldDef def = new ArcFieldDef();
        def.setFieldCode(code);
        def.setFieldName(name);
        def.setSectionCode(BasicConstants.SECTION_CODE);
        def.setFieldType(type);
        def.setRequired(required ? 1 : 0);
        def.setSource(source);
        def.setVisibleRoles("STU,COU");
        def.setEditableRoles(editableRoles);
        def.setValidator(validator);
        def.setRuleJson(ruleJson);
        def.setSensitiveLevel(0);
        def.setSortNo(1);
        def.setStatus(1);
        return def;
    }
}
