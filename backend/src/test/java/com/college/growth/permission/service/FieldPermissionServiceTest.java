package com.college.growth.permission.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.college.growth.common.context.CurrentUser;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.college.growth.common.context.UserContext;
import com.college.growth.common.enums.UserRoleEnum;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.field.entity.ArcFieldDef;
import com.college.growth.common.field.mapper.ArcFieldDefMapper;
import com.college.growth.common.log.service.AuditLogService;
import com.college.growth.common.result.ResultCode;
import com.college.growth.permission.dto.FieldPermissionDTO;
import com.college.growth.permission.dto.PermissionSaveDTO;
import com.college.growth.permission.service.impl.FieldPermissionServiceImpl;
import com.college.growth.permission.vo.FieldPermissionVO;
import com.college.growth.permission.vo.PermissionMatrixVO;
import com.college.growth.permission.vo.PermissionSaveResultVO;
import com.college.growth.permission.vo.SectionPermissionVO;
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
 * 字段编辑权限配置单测。
 *
 * <p>重点验证：只有管理员能进；ADMIN 不能成为内容编辑角色；系统字段不可放开；
 * 保存只写差异字段并留审计；恢复默认取 default_editable_roles。
 * 实际生效链路由 {@code BasicSectionServiceTest} 与真库联调覆盖。</p>
 */
@ExtendWith(MockitoExtension.class)
class FieldPermissionServiceTest {

    private static final long ADMIN_ID = 9003L;

    static {
        // Lambda 条件构造器依赖 MP 实体元数据缓存；纯单测没有 Spring 容器，需手动初始化。
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ArcFieldDef.class);
    }

    @Mock
    private ArcFieldDefMapper fieldDefMapper;

    @Mock
    private AuditLogService auditLogService;

    private FieldPermissionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FieldPermissionServiceImpl(fieldDefMapper, auditLogService);
        UserContext.set(new CurrentUser(ADMIN_ID, UserRoleEnum.ADMIN, false));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("权限矩阵：非管理员返回 EC-40303")
    void matrixRejectsNonAdmin() {
        UserContext.set(new CurrentUser(1001L, UserRoleEnum.STU, false));
        assertThrows(BusinessException.class, () -> service.matrix());

        UserContext.set(new CurrentUser(9001L, UserRoleEnum.COU, false));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.matrix());
        assertEquals(ResultCode.PERMISSION_DENIED, ex.getResultCode());
    }

    @Test
    @DisplayName("权限矩阵：六板块齐全，模式/已调整/固定只读计算正确")
    void matrixDescribesPermissionState() {
        when(fieldDefMapper.selectList(any())).thenReturn(List.of(
                def(1101L, "BASIC", "basic.name", "姓名", "SIS", "PERSON_NAME", "STU", "STU"),
                def(1109L, "BASIC", "basic.majorId", "专业", "SYS", "DERIVED", "", ""),
                def(1201L, "ACADEMIC", "academic.goalShort", "短期学业目标", "STU", "TEXT_LEN", "STU,COU", "STU,COU"),
                def(1301L, "INTEREST", "interest.hobbies", "兴趣爱好", "STU", "TEXT_LEN", "STU", "STU"),
                def(1401L, "LIFE", "life.orgRole", "学生工作职务", "STU", "TEXT_LEN", "COU", "STU"),
                def(1501L, "CAREER", "career.intention", "发展意向", "STU", "ENUM", "STU", "STU"),
                def(1601L, "MENTOR", "mentor.talkDate", "谈话日期", "COU", "TEXT_LEN", "COU", "COU")));

        PermissionMatrixVO matrix = service.matrix();

        assertEquals(6, matrix.getSections().size());
        assertEquals("BASIC", matrix.getSections().get(0).getSectionCode());
        assertEquals("基本信息", matrix.getSections().get(0).getSectionName());

        SectionPermissionVO basic = matrix.getSections().get(0);
        assertEquals(2, basic.getFields().size());
        assertEquals(1, basic.getStudentEditable());
        assertEquals(0, basic.getCounselorEditable());
        assertEquals(1, basic.getReadonly());

        FieldPermissionVO name = basic.getFields().get(0);
        assertEquals("STU", name.getPermissionMode());
        assertFalse(name.getCustomized());
        assertFalse(name.getLocked());

        FieldPermissionVO major = basic.getFields().get(1);
        assertTrue(major.getLocked());
        assertEquals("NONE", major.getPermissionMode());

        FieldPermissionVO life = matrix.getSections().get(3).getFields().get(0);
        assertEquals("COU", life.getPermissionMode());
        assertTrue(life.getCustomized());
        assertEquals(1, matrix.getSections().get(3).getCustomized());
    }

    @Test
    @DisplayName("保存：学生与辅导员不能配置权限")
    void saveRejectsNonAdmin() {
        UserContext.set(new CurrentUser(9001L, UserRoleEnum.COU, false));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.save("basic", dto("basic.name", List.of("STU"))));
        assertEquals(ResultCode.PERMISSION_DENIED, ex.getResultCode());
        verify(fieldDefMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("保存：姓名从只读改为仅学生，写库并留审计")
    void saveUpdatesRolesAndWritesAudit() {
        ArcFieldDef name = def(1101L, "BASIC", "basic.name", "姓名", "SIS", "PERSON_NAME", "", "");
        when(fieldDefMapper.selectList(any())).thenReturn(List.of(name));
        when(fieldDefMapper.update(isNull(), any())).thenReturn(1);

        PermissionSaveResultVO result = service.save("BASIC", dto("basic.name", List.of("STU")));

        assertEquals(1, result.getChanged());
        assertEquals(List.of("STU"), result.getSection().getFields().get(0).getEditableRoles());
        assertEquals("STU", result.getSection().getFields().get(0).getPermissionMode());
        verify(auditLogService).record(eq(AuditLogService.BIZ_PERMISSION_CONFIG),
                eq(AuditLogService.TARGET_FIELD_DEF), eq(1101L), isNull(), eq("BASIC"),
                eq("basic.name"), eq("UPDATE"), anyString());
    }

    @Test
    @DisplayName("保存：值未变化时不写库、不记审计")
    void saveSkipsUnchangedField() {
        ArcFieldDef name = def(1101L, "BASIC", "basic.name", "姓名", "SIS", "PERSON_NAME", "STU", "STU");
        when(fieldDefMapper.selectList(any())).thenReturn(List.of(name));

        PermissionSaveResultVO result = service.save("basic", dto("basic.name", List.of("STU")));

        assertEquals(0, result.getChanged());
        verify(fieldDefMapper, never()).update(any(), any());
        verify(auditLogService, never()).record(anyString(), anyString(), any(), any(), anyString(),
                anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("保存：管理员不能被配置为内容编辑角色")
    void saveRejectsAdminRole() {
        when(fieldDefMapper.selectList(any())).thenReturn(List.of(
                def(1101L, "BASIC", "basic.name", "姓名", "SIS", "PERSON_NAME", "", "")));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.save("basic", dto("basic.name", List.of("ADMIN"))));
        assertEquals(ResultCode.PARAM_INVALID, ex.getResultCode());
        assertTrue(ex.getMessage().contains("管理员"));
    }

    @Test
    @DisplayName("保存：系统推导字段不能放开编辑")
    void saveRejectsLockedField() {
        when(fieldDefMapper.selectList(any())).thenReturn(List.of(
                def(1109L, "BASIC", "basic.majorId", "专业", "SYS", "DERIVED", "", "")));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.save("basic", dto("basic.majorId", List.of("STU"))));
        assertEquals(ResultCode.PARAM_INVALID, ex.getResultCode());
        assertTrue(ex.getMessage().contains("不可配置"));
    }

    @Test
    @DisplayName("保存：对角色不可见的字段不能配置为可编辑")
    void saveRejectsInvisibleRole() {
        // 谈话记录只对老师可见：若允许配置成「学生可编辑」，学生会得到能写但看不见的字段。
        ArcFieldDef talk = def(1601L, "MENTOR", "mentor.talkDate", "谈话日期", "COU", "TEXT_LEN", "", "");
        talk.setVisibleRoles("COU");
        when(fieldDefMapper.selectList(any())).thenReturn(List.of(talk));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.save("mentor", dto("mentor.talkDate", List.of("STU"))));
        assertEquals(ResultCode.PARAM_INVALID, ex.getResultCode());
        assertTrue(ex.getMessage().contains("不可见"));
        verify(fieldDefMapper, never()).update(any(), any());

        // 同一字段配置「老师可编辑」应当放行
        when(fieldDefMapper.update(isNull(), any())).thenReturn(1);
        PermissionSaveResultVO result = service.save("mentor", dto("mentor.talkDate", List.of("COU")));
        assertEquals(1, result.getChanged());
        assertEquals("COU", result.getSection().getFields().get(0).getPermissionMode());
    }

    @Test
    @DisplayName("权限矩阵：透出角色可见性，供前端禁用不可达选项")
    void matrixExposesRoleVisibility() {
        ArcFieldDef talk = def(1601L, "MENTOR", "mentor.talkDate", "谈话日期", "COU", "TEXT_LEN", "COU", "COU");
        talk.setVisibleRoles("COU");
        when(fieldDefMapper.selectList(any())).thenReturn(List.of(talk));

        FieldPermissionVO field = service.matrix().getSections().get(5).getFields().get(0);
        assertFalse(field.getStudentVisible());
        assertTrue(field.getCounselorVisible());
    }

    @Test
    @DisplayName("保存：字段不属于该板块时拒绝")
    void saveRejectsForeignField() {
        when(fieldDefMapper.selectList(any())).thenReturn(List.of(
                def(1101L, "BASIC", "basic.name", "姓名", "SIS", "PERSON_NAME", "", "")));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.save("basic", dto("academic.goalShort", List.of("STU"))));
        assertEquals(ResultCode.PARAM_INVALID, ex.getResultCode());
    }

    @Test
    @DisplayName("恢复默认：取 default_editable_roles 并留 RESET 审计")
    void resetRestoresDefault() {
        ArcFieldDef name = def(1101L, "BASIC", "basic.name", "姓名", "SIS", "PERSON_NAME", "COU", "STU");
        when(fieldDefMapper.selectList(any())).thenReturn(List.of(name));
        when(fieldDefMapper.update(isNull(), any())).thenReturn(1);

        PermissionSaveResultVO result = service.reset("basic");

        assertEquals(1, result.getChanged());
        assertEquals(List.of("STU"), result.getSection().getFields().get(0).getEditableRoles());
        verify(auditLogService).record(eq(AuditLogService.BIZ_PERMISSION_CONFIG),
                eq(AuditLogService.TARGET_FIELD_DEF), eq(1101L), isNull(), eq("BASIC"),
                eq("basic.name"), eq(AuditLogService.ACTION_RESET), anyString());
    }

    private PermissionSaveDTO dto(String fieldCode, List<String> roles) {
        FieldPermissionDTO field = new FieldPermissionDTO();
        field.setFieldCode(fieldCode);
        field.setEditableRoles(roles);
        PermissionSaveDTO dto = new PermissionSaveDTO();
        dto.setFields(List.of(field));
        return dto;
    }

    private ArcFieldDef def(long id, String section, String code, String name, String source,
                            String validator, String editable, String defaultRoles) {
        ArcFieldDef def = new ArcFieldDef();
        def.setId(id);
        def.setSectionCode(section);
        def.setFieldCode(code);
        def.setFieldName(name);
        def.setFieldType("TEXT");
        def.setRequired(0);
        def.setSource(source);
        def.setVisibleRoles("STU,COU");
        def.setEditableRoles(editable);
        def.setDefaultEditableRoles(defaultRoles);
        def.setValidator(validator);
        def.setSensitiveLevel(0);
        def.setSortNo(10);
        def.setStatus(1);
        return def;
    }
}
