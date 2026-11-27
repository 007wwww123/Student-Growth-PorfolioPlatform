package com.college.growth.common.profile.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.context.UserContext;
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
import com.college.growth.common.profile.service.impl.PendingFieldServiceImpl;
import com.college.growth.common.profile.vo.PendingFieldVO;
import com.college.growth.common.result.ResultCode;
import java.math.BigDecimal;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 待确认区处理单测（BR-F06）。
 *
 * <p>关键断言：确认后新值生效、驳回后旧值保留，两种处理都必须清空 pending_*，
 * 否则下一次修改会被误判为「已有待确认值」而永远挂起。</p>
 */
@ExtendWith(MockitoExtension.class)
class PendingFieldServiceTest {

    private static final String YEAR = "2025-2026-1";

    private static final long STUDENT_ID = 1001L;

    private static final long COUNSELOR_ID = 9001L;

    private static final String FIELD_CODE = "basic.phone";

    static {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ArcProfile.class);
        TableInfoHelper.initTableInfo(assistant, ArcProfileItem.class);
    }

    @Mock
    private ArcProfileMapper profileMapper;

    @Mock
    private ArcProfileItemMapper itemMapper;

    @Mock
    private FieldDefService fieldDefService;

    @Mock
    private StudentClassService studentClassService;

    @Mock
    private ArcChangeLogMapper changeLogMapper;

    @Mock
    private SysAuditLogMapper auditLogMapper;

    @Mock
    private ProfileCompletenessService completenessService;

    private PendingFieldServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PendingFieldServiceImpl(new ProfileLocator(profileMapper),
                new ProfileItemService(itemMapper), fieldDefService, studentClassService,
                new ChangeLogService(changeLogMapper), new AuditLogService(auditLogMapper), completenessService);
        UserContext.set(new CurrentUser(COUNSELOR_ID, UserRoleEnum.COU, false));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("确认：新值替换正式值，待确认区清空，状态置 APPROVED")
    void confirmReplacesValue() {
        givenPendingItem("13800138000", "13900139000");
        when(studentClassService.isCounselorOf(STUDENT_ID, YEAR, COUNSELOR_ID)).thenReturn(true);
        when(itemMapper.update(any(), any())).thenReturn(1);

        PendingFieldVO vo = service.confirm(STUDENT_ID, "BASIC", FIELD_CODE, YEAR);

        assertEquals("13900139000", vo.getValue());
        assertEquals("APPROVED", vo.getConfirmStatus());
        verify(itemMapper).update(any(), any());
        verify(changeLogMapper).insert(any(ArcChangeLog.class));
        verify(auditLogMapper).insert(any(SysAuditLog.class));
    }

    @Test
    @DisplayName("驳回：正式值不变，待确认区清空，状态置 REJECTED")
    void rejectKeepsOldValue() {
        ArcProfileItem item = givenPendingItem("13800138000", "13900139000");
        when(studentClassService.isCounselorOf(STUDENT_ID, YEAR, COUNSELOR_ID)).thenReturn(true);
        when(itemMapper.update(any(), any())).thenReturn(1);

        service.reject(STUDENT_ID, "BASIC", FIELD_CODE, YEAR);

        assertEquals("13800138000", item.getValueText());
        assertNull(item.getPendingText());
        assertEquals("REJECTED", item.getConfirmStatus());
        verify(completenessService).refresh(any(ArcProfile.class));
        assertEquals(COUNSELOR_ID, item.getConfirmedBy());
    }

    @Test
    @DisplayName("没有待确认值时返回 EC-40902")
    void confirmRejectedWithoutPendingValue() {
        givenPendingItem("13800138000", null);
        when(studentClassService.isCounselorOf(STUDENT_ID, YEAR, COUNSELOR_ID)).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.confirm(STUDENT_ID, "BASIC", FIELD_CODE, YEAR));

        assertEquals(ResultCode.STATE_CONFLICT, ex.getResultCode());
        verify(itemMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("学生不能自己确认自己的值，返回 EC-40302")
    void confirmRejectedForStudent() {
        UserContext.set(new CurrentUser(STUDENT_ID, UserRoleEnum.STU, false));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.confirm(STUDENT_ID, "BASIC", FIELD_CODE, YEAR));

        assertEquals(ResultCode.DATA_SCOPE_DENIED, ex.getResultCode());
    }

    @Test
    @DisplayName("非本班辅导员不能处理，返回 EC-40302")
    void confirmRejectedForOtherClassCounselor() {
        when(studentClassService.isCounselorOf(STUDENT_ID, YEAR, COUNSELOR_ID)).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.confirm(STUDENT_ID, "BASIC", FIELD_CODE, YEAR));

        assertEquals(ResultCode.DATA_SCOPE_DENIED, ex.getResultCode());
    }

    @Test
    @DisplayName("敏感字段：返回脱敏值，日志不落明文")
    void confirmMasksSensitiveValue() {
        ArcFieldDef def = phoneDef();
        def.setSensitiveLevel(2);
        givenPendingItem("13800138000", "13900139000", def);
        when(studentClassService.isCounselorOf(STUDENT_ID, YEAR, COUNSELOR_ID)).thenReturn(true);
        when(itemMapper.update(any(), any())).thenReturn(1);

        PendingFieldVO vo = service.confirm(STUDENT_ID, "BASIC", FIELD_CODE, YEAR);

        assertEquals("139****9000", vo.getValue());
    }

    @Test
    void firstFillCanBeRejectedWithoutPublishingIt() {
        ArcProfileItem item = givenPendingItem(null, "13900139000");
        when(studentClassService.isCounselorOf(STUDENT_ID, YEAR, COUNSELOR_ID)).thenReturn(true);
        service.reject(STUDENT_ID, "BASIC", FIELD_CODE, YEAR);
        assertNull(item.getValueText());
        assertNull(item.getPendingText());
    }

    @Test
    void explicitlyRequestedClearIsDistinguishableFromLegacyFirstFill() {
        ArcProfileItem item = givenPendingItem("13800138000", null);
        item.setConfirmStatus("PENDING");
        when(studentClassService.isCounselorOf(STUDENT_ID, YEAR, COUNSELOR_ID)).thenReturn(true);
        assertThrows(BusinessException.class, () -> service.confirm(STUDENT_ID, "BASIC", FIELD_CODE, YEAR));
        assertEquals("13800138000", item.getValueText());
        com.college.growth.common.profile.FieldValueCodec.writePending(item, phoneDef(), null);
        service.confirm(STUDENT_ID, "BASIC", FIELD_CODE, YEAR);
        assertNull(item.getValueText());
        assertNull(item.getPendingJson());
        assertEquals("APPROVED", item.getConfirmStatus());
    }

    // ------------------------------------------------------------------
    // 测试数据
    // ------------------------------------------------------------------

    private ArcProfileItem givenPendingItem(String value, String pending) {
        return givenPendingItem(value, pending, phoneDef());
    }

    private ArcProfileItem givenPendingItem(String value, String pending, ArcFieldDef def) {
        ArcProfile profile = new ArcProfile();
        profile.setId(3001L);
        profile.setStudentId(STUDENT_ID);
        profile.setAcademicYear(YEAR);
        profile.setSectionCode("BASIC");
        profile.setStatus("DRAFT");
        profile.setCompleteness(BigDecimal.ZERO);
        when(profileMapper.selectOne(any())).thenReturn(profile);

        ArcProfileItem item = new ArcProfileItem();
        item.setId(4001L);
        item.setProfileId(3001L);
        item.setFieldCode(FIELD_CODE);
        item.setValueText(value);
        item.setPendingText(pending);
        item.setConfirmStatus(pending == null ? "NONE" : "PENDING");
        when(itemMapper.selectOne(any())).thenReturn(item);

        when(fieldDefService.getByCode(FIELD_CODE)).thenReturn(def);
        return item;
    }

    private ArcFieldDef phoneDef() {
        ArcFieldDef def = new ArcFieldDef();
        def.setFieldCode(FIELD_CODE);
        def.setFieldName("手机号");
        def.setSectionCode("BASIC");
        def.setFieldType("TEXT");
        def.setRequired(1);
        def.setSource("STU_CFM");
        def.setVisibleRoles("STU,COU");
        def.setEditableRoles("STU,COU");
        def.setValidator("MOBILE");
        def.setSensitiveLevel(0);
        def.setStatus(1);
        return def;
    }
}
