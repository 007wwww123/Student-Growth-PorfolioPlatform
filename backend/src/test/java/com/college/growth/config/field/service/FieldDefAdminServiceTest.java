package com.college.growth.config.field.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.context.UserContext;
import com.college.growth.common.enums.UserRoleEnum;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.field.entity.ArcFieldDef;
import com.college.growth.common.field.mapper.ArcFieldDefMapper;
import com.college.growth.common.field.validation.FieldValidatorRegistry;
import com.college.growth.common.log.service.AuditLogService;
import com.college.growth.common.profile.entity.ArcProfileItem;
import com.college.growth.common.profile.mapper.ArcProfileItemMapper;
import com.college.growth.common.result.ResultCode;
import com.college.growth.config.field.dto.FieldDefSaveDTO;
import com.college.growth.config.field.service.impl.FieldDefAdminServiceImpl;
import com.college.growth.config.field.vo.FieldDefVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Field definition config tests: code, options, type change, system field protection. */
@ExtendWith(MockitoExtension.class)
class FieldDefAdminServiceTest {

    static {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ArcFieldDef.class);
        TableInfoHelper.initTableInfo(assistant, ArcProfileItem.class);
    }

    @Mock
    private ArcFieldDefMapper fieldDefMapper;

    @Mock
    private ArcProfileItemMapper itemMapper;

    @Mock
    private FieldValidatorRegistry validatorRegistry;

    @Mock
    private AuditLogService auditLogService;

    private FieldDefAdminServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FieldDefAdminServiceImpl(fieldDefMapper, itemMapper, validatorRegistry,
                new ObjectMapper(), auditLogService);
        UserContext.set(new CurrentUser(9003L, UserRoleEnum.ADMIN, false));
        lenient().when(validatorRegistry.codes())
                .thenReturn(Set.of("TEXT_LEN", "ENUM", "STRING_LIST", "FILE_ID", "DERIVED"));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("create text field with defaults")
    void createTextField() {
        when(fieldDefMapper.selectOne(any())).thenReturn(null);

        FieldDefVO vo = service.create(dto("BASIC", "basic.testField", "Test Field", "TEXT", 0, 10));

        assertEquals("basic.testField", vo.getFieldCode());
        assertEquals("TEXT_LEN", vo.getValidator());
        assertEquals("STU,COU", String.join(",", vo.getVisibleRoles()));
        assertTrue(vo.getEditableRoles().contains("STU"));
        assertEquals(1, vo.getStatus());
    }

    @Test
    @DisplayName("enum field requires options")
    void enumFieldRequiresOptions() {
        when(fieldDefMapper.selectOne(any())).thenReturn(null);
        FieldDefSaveDTO dto = dto("BASIC", "basic.testSelect", "Test Select", "SELECT", 0, 10);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(dto));
        assertEquals(ResultCode.PARAM_MISSING.getCode(), ex.getResultCode().getCode());
    }

    @Test
    @DisplayName("duplicate field code rejected")
    void duplicateFieldCodeRejected() {
        ArcFieldDef existing = new ArcFieldDef();
        existing.setId(1L);
        existing.setFieldCode("basic.testField");
        when(fieldDefMapper.selectOne(any())).thenReturn(existing);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(dto("BASIC", "basic.testField", "Dup", "TEXT", 0, 10)));
        assertEquals(ResultCode.PARAM_INVALID.getCode(), ex.getResultCode().getCode());
    }

    @Test
    @DisplayName("cannot change type when values exist")
    void cannotChangeTypeWithValues() {
        ArcFieldDef def = field("basic.testField", "TEXT", "STU");
        when(fieldDefMapper.selectById(1L)).thenReturn(def);
        when(itemMapper.selectCount(any())).thenReturn(3L);

        FieldDefSaveDTO dto = dto("BASIC", "basic.testField", "Test Field", "TEXTAREA", 0, 10);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.update(1L, dto));
        assertEquals(ResultCode.STATE_CONFLICT.getCode(), ex.getResultCode().getCode());
    }

    @Test
    @DisplayName("computed field cannot be required")
    void computedFieldCannotBeRequired() {
        ArcFieldDef def = field("basic.majorId", "TEXT", "SYS");
        when(fieldDefMapper.selectById(1L)).thenReturn(def);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.updateRequired(1L, 1));
        assertEquals(ResultCode.STATE_CONFLICT.getCode(), ex.getResultCode().getCode());
    }

    @Test
    @DisplayName("SIS field cannot be disabled")
    void sisFieldCannotBeDisabled() {
        ArcFieldDef def = field("basic.name", "TEXT", "SIS");
        when(fieldDefMapper.selectById(1L)).thenReturn(def);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.updateStatus(1L, 0));
        assertEquals(ResultCode.STATE_CONFLICT.getCode(), ex.getResultCode().getCode());
    }

    @Test
    @DisplayName("unknown validator rejected")
    void unknownValidatorRejected() {
        when(fieldDefMapper.selectOne(any())).thenReturn(null);
        FieldDefSaveDTO dto = dto("BASIC", "basic.testField", "Test Field", "TEXT", 0, 10);
        dto.setValidator("NOT_EXISTS");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(dto));
        assertEquals(ResultCode.PARAM_INVALID.getCode(), ex.getResultCode().getCode());
    }

    @Test
    @DisplayName("duplicate option values rejected")
    void duplicateOptionsRejected() {
        when(fieldDefMapper.selectOne(any())).thenReturn(null);
        FieldDefSaveDTO dto = dto("LIFE", "life.testSelect", "Test Select", "SELECT", 0, 10);
        dto.setOptionsJson("[{\"value\":\"A\",\"label\":\"One\"},{\"value\":\"A\",\"label\":\"Two\"}]");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(dto));
        assertEquals(ResultCode.PARAM_INVALID.getCode(), ex.getResultCode().getCode());
    }

    private FieldDefSaveDTO dto(String section, String code, String name, String type,
                                int required, int sortNo) {
        FieldDefSaveDTO dto = new FieldDefSaveDTO();
        dto.setSectionCode(section);
        dto.setFieldCode(code);
        dto.setFieldName(name);
        dto.setFieldType(type);
        dto.setRequired(required);
        dto.setSource("STU");
        dto.setVisibleRoles(List.of("STU", "COU"));
        dto.setEditableRoles(List.of("STU"));
        dto.setSensitiveLevel(0);
        dto.setSortNo(sortNo);
        return dto;
    }

    private ArcFieldDef field(String code, String validator, String source) {
        ArcFieldDef def = new ArcFieldDef();
        def.setId(1L);
        def.setSectionCode("BASIC");
        def.setFieldCode(code);
        def.setFieldName("Test Field");
        def.setFieldType("TEXT");
        def.setRequired(0);
        def.setSource(source);
        def.setVisibleRoles("STU,COU");
        def.setEditableRoles("STU");
        def.setDefaultEditableRoles("STU");
        def.setValidator(validator);
        def.setSensitiveLevel(0);
        def.setSortNo(10);
        def.setStatus(1);
        return def;
    }
}
