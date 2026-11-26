package com.college.growth.common.org.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.college.growth.common.org.entity.OrgClass;
import com.college.growth.common.org.mapper.OrgClassMapper;
import com.college.growth.common.profile.entity.ArcProfile;
import com.college.growth.common.profile.entity.ArcProfileItem;
import com.college.growth.common.profile.mapper.ArcProfileItemMapper;
import com.college.growth.common.profile.mapper.ArcProfileMapper;
import com.college.growth.common.student.service.StudentMasterService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 班级解析单测。
 *
 * <p>班级有两条来源：学生主数据（首选）与 {@code basic.classId} 字段值（存量回退）。
 * 这里把优先级、回退、两者皆无三种情况钉死——审核权完全依赖它，
 * 判错的后果是「本班辅导员看不到待办」或「外班辅导员能审核」。</p>
 */
@ExtendWith(MockitoExtension.class)
class StudentClassServiceTest {

    private static final String YEAR = "2025-2026-1";

    private static final long STUDENT_ID = 1001L;

    private static final long COUNSELOR_ID = 9001L;

    static {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ArcProfile.class);
        TableInfoHelper.initTableInfo(assistant, ArcProfileItem.class);
        TableInfoHelper.initTableInfo(assistant, OrgClass.class);
    }

    @Mock
    private ArcProfileMapper profileMapper;

    @Mock
    private ArcProfileItemMapper itemMapper;

    @Mock
    private OrgClassMapper orgClassMapper;

    @Mock
    private StudentMasterService studentMasterService;

    private StudentClassService service;

    @BeforeEach
    void setUp() {
        service = new StudentClassService(profileMapper, itemMapper, orgClassMapper, studentMasterService);
    }

    @Test
    @DisplayName("主数据有班级时优先取主数据，不再回退查档案字段")
    void preferMasterData() {
        when(studentMasterService.findClassCode(STUDENT_ID)).thenReturn("RJ2401");
        when(orgClassMapper.selectOne(any())).thenReturn(orgClass());

        OrgClass result = service.findClass(STUDENT_ID, YEAR);

        assertEquals("RJ2401", result.getClassCode());
        verify(profileMapper, never()).selectOne(any());
        verify(itemMapper, never()).selectOne(any());
    }

    @Test
    @DisplayName("主数据缺失班级时回退到 basic.classId 字段值（存量档案不受影响）")
    void fallbackToFieldValue() {
        when(studentMasterService.findClassCode(STUDENT_ID)).thenReturn(null);
        when(profileMapper.selectOne(any())).thenReturn(basicProfile());
        when(itemMapper.selectOne(any())).thenReturn(classItem("RJ2402"));

        assertEquals("RJ2402", service.findClassValue(STUDENT_ID, YEAR));
    }

    @Test
    @DisplayName("主数据与档案字段都没有班级时返回 null，不抛异常")
    void nullWhenBothMissing() {
        when(studentMasterService.findClassCode(STUDENT_ID)).thenReturn(null);
        when(profileMapper.selectOne(any())).thenReturn(null);

        assertNull(service.findClassValue(STUDENT_ID, YEAR));
        assertNull(service.findClass(STUDENT_ID, YEAR));
    }

    @Test
    @DisplayName("审核权按主数据班级判定：本班辅导员通过")
    void counselorOfSameClass() {
        when(studentMasterService.findClassCode(STUDENT_ID)).thenReturn("RJ2401");
        when(orgClassMapper.selectOne(any())).thenReturn(orgClass());

        assertTrue(service.isCounselorOf(STUDENT_ID, YEAR, COUNSELOR_ID));
    }

    @Test
    @DisplayName("审核权按主数据班级判定：非本班辅导员拒绝")
    void counselorOfOtherClass() {
        when(studentMasterService.findClassCode(STUDENT_ID)).thenReturn("RJ2401");
        when(orgClassMapper.selectOne(any())).thenReturn(orgClass());

        assertFalse(service.isCounselorOf(STUDENT_ID, YEAR, 9002L));
    }

    @Test
    @DisplayName("无法判定班级时一律拒绝审核（宁可拒绝也不放行）")
    void denyWhenClassUnknown() {
        when(studentMasterService.findClassCode(STUDENT_ID)).thenReturn(null);
        when(profileMapper.selectOne(any())).thenReturn(null);

        assertFalse(service.isCounselorOf(STUDENT_ID, YEAR, COUNSELOR_ID));
    }

    // ------------------------------------------------------------------
    // 测试数据
    // ------------------------------------------------------------------

    private static OrgClass orgClass() {
        OrgClass orgClass = new OrgClass();
        orgClass.setId(2001L);
        orgClass.setClassCode("RJ2401");
        orgClass.setClassName("软件2401");
        orgClass.setMajorName("软件工程");
        orgClass.setCounselorUserId(COUNSELOR_ID);
        orgClass.setStatus(1);
        return orgClass;
    }

    private static ArcProfile basicProfile() {
        ArcProfile profile = new ArcProfile();
        profile.setId(10L);
        profile.setStudentId(STUDENT_ID);
        profile.setSectionCode("BASIC");
        profile.setAcademicYear(YEAR);
        return profile;
    }

    private static ArcProfileItem classItem(String value) {
        ArcProfileItem item = new ArcProfileItem();
        item.setId(1L);
        item.setProfileId(10L);
        item.setFieldCode("basic.classId");
        item.setValueText(value);
        return item;
    }
}
