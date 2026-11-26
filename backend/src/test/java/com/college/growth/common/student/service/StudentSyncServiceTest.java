package com.college.growth.common.student.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.college.growth.common.field.entity.ArcFieldDef;
import com.college.growth.common.field.service.FieldDefService;
import com.college.growth.common.log.entity.ArcChangeLog;
import com.college.growth.common.log.mapper.ArcChangeLogMapper;
import com.college.growth.common.log.service.ChangeLogService;
import com.college.growth.common.profile.entity.ArcProfile;
import com.college.growth.common.profile.entity.ArcProfileItem;
import com.college.growth.common.profile.mapper.ArcProfileItemMapper;
import com.college.growth.common.profile.service.ProfileItemService;
import com.college.growth.common.student.entity.StuStudent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 主数据同步单测。
 *
 * <p>重点验证两条语义边界：<b>补齐空值绝不覆盖已有值</b>（否则学生确认过的手机、
 * 辅导员维护的宿舍会被一次同步冲掉），以及<b>强制覆盖会留痕且不写敏感明文</b>。</p>
 */
@ExtendWith(MockitoExtension.class)
class StudentSyncServiceTest {

    private static final long STUDENT_ID = 1001L;

    private static final long PROFILE_ID = 10L;

    static {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ArcProfile.class);
        TableInfoHelper.initTableInfo(assistant, ArcProfileItem.class);
    }

    @Mock
    private StudentMasterService studentMasterService;

    @Mock
    private FieldDefService fieldDefService;

    @Mock
    private ArcProfileItemMapper itemMapper;

    @Mock
    private ArcChangeLogMapper changeLogMapper;

    private StudentSyncService syncService;

    private ArcProfile profile;

    @BeforeEach
    void setUp() {
        syncService = new StudentSyncService(studentMasterService, fieldDefService,
                new ProfileItemService(itemMapper), new ChangeLogService(changeLogMapper));
        profile = new ArcProfile();
        profile.setId(PROFILE_ID);
        profile.setStudentId(STUDENT_ID);
        profile.setSectionCode("BASIC");
    }

    @Test
    @DisplayName("补齐空值：只写 SIS 字段且只填空着的，写一条 IMPORT 日志")
    void fillAbsentWritesOnlyEmptySisFields() {
        when(studentMasterService.find(STUDENT_ID)).thenReturn(Optional.of(student()));
        ArcFieldDef nameDef = def("basic.name", "SIS", "name");
        ArcFieldDef phoneDef = def("basic.phone", "STU_CFM", null);
        Map<String, ArcProfileItem> items = new LinkedHashMap<>();

        int written = syncService.fillAbsent(profile, List.of(nameDef, phoneDef), items);

        assertEquals(1, written);
        assertEquals("李明", items.get("basic.name").getValueText());
        assertNull(items.get("basic.phone"));

        ArgumentCaptor<ArcChangeLog> captor = ArgumentCaptor.forClass(ArcChangeLog.class);
        verify(changeLogMapper).insert(captor.capture());
        ArcChangeLog log = captor.getValue();
        assertEquals(ChangeLogService.SOURCE_IMPORT, log.getSource());
        assertEquals(ChangeLogService.SYSTEM_OPERATOR_ID, log.getOperatorId());
        assertEquals(ChangeLogService.ROLE_SYSTEM, log.getOperatorRole());
        assertEquals("basic.name", log.getFieldCode());
        assertEquals("李明", log.getNewValue());
    }

    @Test
    @DisplayName("补齐空值：已有值不覆盖，也不写日志")
    void fillAbsentKeepsExistingValue() {
        when(studentMasterService.find(STUDENT_ID)).thenReturn(Optional.of(student()));
        ArcFieldDef nameDef = def("basic.name", "SIS", "name");
        Map<String, ArcProfileItem> items = new LinkedHashMap<>();
        items.put("basic.name", item("旧名"));

        int written = syncService.fillAbsent(profile, List.of(nameDef), items);

        assertEquals(0, written);
        assertEquals("旧名", items.get("basic.name").getValueText());
        verifyNoInteractions(changeLogMapper);
    }

    @Test
    @DisplayName("强制覆盖：值不同则改写并留痕，值相同则跳过")
    void overwriteReplacesChangedValueOnly() {
        when(studentMasterService.find(STUDENT_ID)).thenReturn(Optional.of(student()));
        ArcFieldDef nameDef = def("basic.name", "SIS", "name");
        Map<String, ArcProfileItem> items = new LinkedHashMap<>();
        items.put("basic.name", item("旧名"));

        assertEquals(1, syncService.overwrite(profile, List.of(nameDef), items));
        assertEquals("李明", items.get("basic.name").getValueText());

        // 再跑一次：值已一致，不应重复写日志
        assertEquals(0, syncService.overwrite(profile, List.of(nameDef), items));
        verify(changeLogMapper).insert(any(ArcChangeLog.class));
    }

    @Test
    @DisplayName("强制覆盖：敏感字段日志只写脱敏值，不落身份证明文")
    void overwriteMasksSensitiveValueInLog() {
        when(studentMasterService.find(STUDENT_ID)).thenReturn(Optional.of(student()));
        ArcFieldDef idCardDef = def("basic.idCard", "SIS", "id_card");
        idCardDef.setSensitiveLevel(2);
        ArcFieldDef genderDef = def("basic.gender", "SIS", "gender");
        Map<String, ArcProfileItem> items = new LinkedHashMap<>();

        assertEquals(2, syncService.overwrite(profile, List.of(idCardDef, genderDef), items));

        ArgumentCaptor<ArcChangeLog> captor = ArgumentCaptor.forClass(ArcChangeLog.class);
        verify(changeLogMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        ArcChangeLog idCardLog = captor.getAllValues().stream()
                .filter(log -> "basic.idCard".equals(log.getFieldCode()))
                .findFirst()
                .orElseThrow();
        assertFalse(idCardLog.getNewValue().contains("320102200201011232"));
        assertTrue(idCardLog.getNewValue().contains("****"));
        // 档案里仍然是明文，脱敏只发生在日志
        assertEquals("320102200201011232", items.get("basic.idCard").getValueText());
    }

    @Test
    @DisplayName("主数据不存在：整体跳过，不写库不写日志")
    void skipWhenStudentMissing() {
        when(studentMasterService.find(STUDENT_ID)).thenReturn(Optional.empty());
        Map<String, ArcProfileItem> items = new LinkedHashMap<>();

        int written = syncService.fillAbsent(profile, List.of(def("basic.name", "SIS", "name")), items);

        assertEquals(0, written);
        assertTrue(items.isEmpty());
        verify(itemMapper, never()).insert(any(ArcProfileItem.class));
        verifyNoInteractions(changeLogMapper);
    }

    @Test
    @DisplayName("source_field 指向不存在的主数据列：跳过该字段，不抛异常")
    void skipUnknownSourceField() {
        when(studentMasterService.find(STUDENT_ID)).thenReturn(Optional.of(student()));
        Map<String, ArcProfileItem> items = new LinkedHashMap<>();

        int written = syncService.fillAbsent(profile, List.of(def("basic.unknown", "SIS", "no_such_col")), items);

        assertEquals(0, written);
        verify(itemMapper, never()).insert(any(ArcProfileItem.class));
    }

    @Test
    @DisplayName("SIS 字段但主数据值为空：不写入，避免把空值当有效值")
    void skipWhenMasterValueBlank() {
        StuStudent blank = student();
        blank.setNation("   ");
        when(studentMasterService.find(STUDENT_ID)).thenReturn(Optional.of(blank));
        Map<String, ArcProfileItem> items = new LinkedHashMap<>();

        assertEquals(0, syncService.fillAbsent(profile, List.of(def("basic.nation", "SIS", "nation")), items));
        assertTrue(items.isEmpty());
    }

    // ------------------------------------------------------------------
    // 测试数据
    // ------------------------------------------------------------------

    private static StuStudent student() {
        StuStudent student = new StuStudent();
        student.setId(STUDENT_ID);
        student.setName("李明");
        student.setStudentNo("2024010101");
        student.setGender("M");
        student.setNation("汉族");
        student.setIdCard("320102200201011232");
        student.setNativePlace("江苏 南京");
        student.setClassCode("RJ2401");
        student.setStatus(1);
        return student;
    }

    private static ArcFieldDef def(String fieldCode, String source, String sourceField) {
        ArcFieldDef def = new ArcFieldDef();
        def.setSectionCode("BASIC");
        def.setFieldCode(fieldCode);
        def.setFieldName(fieldCode);
        def.setFieldType("TEXT");
        def.setSource(source);
        def.setSourceField(sourceField);
        def.setSensitiveLevel(0);
        def.setStatus(1);
        return def;
    }

    private static ArcProfileItem item(String value) {
        ArcProfileItem item = new ArcProfileItem();
        item.setId(1L);
        item.setProfileId(PROFILE_ID);
        item.setValueText(value);
        return item;
    }
}
