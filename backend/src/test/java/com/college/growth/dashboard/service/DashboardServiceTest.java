package com.college.growth.dashboard.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.context.UserContext;
import com.college.growth.common.enums.UserRoleEnum;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.org.entity.OrgClass;
import com.college.growth.common.org.mapper.OrgClassMapper;
import com.college.growth.common.result.ResultCode;
import com.college.growth.dashboard.mapper.DashboardClassRow;
import com.college.growth.dashboard.mapper.DashboardMapper;
import com.college.growth.dashboard.mapper.DashboardSectionRow;
import com.college.growth.dashboard.service.impl.DashboardServiceImpl;
import com.college.growth.dashboard.vo.ClassProgressVO;
import com.college.growth.dashboard.vo.DashboardOverviewVO;
import com.college.growth.dashboard.vo.SectionProgressVO;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 首页进度聚合单测（需求未编号，按「首页进度聚合」实现）。
 *
 * <p>重点验证三件事：数据范围只在服务端收口、口径（分母、百分比、缺板块补零）正确、
 * 班级筛选不能越权。SQL 本身的正确性由真库联调覆盖。</p>
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final String YEAR = "2025-2026-1";

    private static final long COUNSELOR_ID = 9001L;

    @Mock
    private DashboardMapper dashboardMapper;

    @Mock
    private OrgClassMapper orgClassMapper;

    private DashboardServiceImpl dashboard;

    @BeforeEach
    void setUp() {
        dashboard = new DashboardServiceImpl(dashboardMapper, orgClassMapper);
        lenient().when(orgClassMapper.selectOne(any())).thenReturn(null);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("辅导员视角：按带班统计总数、完成率与待审量")
    void overviewForCounselor() {
        UserContext.set(new CurrentUser(COUNSELOR_ID, UserRoleEnum.COU, false));
        when(dashboardMapper.selectClassRows(YEAR, COUNSELOR_ID, null))
                .thenReturn(List.of(classRow("RJ2401", "软件2401", 3, 2, 1, 0, 0)));
        when(dashboardMapper.selectSectionRows(YEAR, COUNSELOR_ID, null))
                .thenReturn(sectionRows());

        DashboardOverviewVO vo = dashboard.overview(YEAR, null);

        assertEquals("我的班级", vo.getScopeLabel());
        assertEquals(3, vo.getStudentTotal());
        assertEquals(2, vo.getCompletedStudents());
        assertEquals(new BigDecimal("66.7"), vo.getCompletionRate());
        assertEquals(1, vo.getPendingReview());
        assertEquals(6, vo.getSections().size());
        assertEquals(1, vo.getClasses().size());
    }

    @Test
    @DisplayName("板块分布：按状态累计，完成率 =（已通过 + 已归档）/ 建档数")
    void sectionDistribution() {
        UserContext.set(new CurrentUser(COUNSELOR_ID, UserRoleEnum.COU, false));
        when(dashboardMapper.selectClassRows(YEAR, COUNSELOR_ID, null))
                .thenReturn(List.of(classRow("RJ2401", "软件2401", 4, 1, 0, 1, 1)));
        when(dashboardMapper.selectSectionRows(YEAR, COUNSELOR_ID, null))
                .thenReturn(sectionRows());

        DashboardOverviewVO vo = dashboard.overview(YEAR, null);

        SectionProgressVO basic = vo.getSections().get(0);
        assertEquals("basic", basic.getSectionCode());
        assertEquals(4, basic.getStudentCount());
        assertEquals(1, basic.getDraft());
        assertEquals(1, basic.getReturned());
        assertEquals(1, basic.getApproved());
        assertEquals(1, basic.getLocked());
        // 已通过与已归档都算完成：2 / 4
        assertEquals(new BigDecimal("50.0"), basic.getCompletionRate());

        // 没有“已通过”数据的板块完成率为 0，但待审量照常体现
        SectionProgressVO mentor = vo.getSections().get(5);
        assertEquals("mentor", mentor.getSectionCode());
        assertEquals(1, mentor.getStudentCount());
        assertEquals(1, mentor.getSubmitted());
        assertEquals(new BigDecimal("0.0"), mentor.getCompletionRate());
    }

    @Test
    @DisplayName("空学年：全部为 0，完成率 0.0，班级列表为空")
    void overviewHandlesEmptyYear() {
        UserContext.set(new CurrentUser(COUNSELOR_ID, UserRoleEnum.COU, false));
        when(dashboardMapper.selectClassRows(YEAR, COUNSELOR_ID, null)).thenReturn(List.of());
        when(dashboardMapper.selectSectionRows(YEAR, COUNSELOR_ID, null)).thenReturn(List.of());

        DashboardOverviewVO vo = dashboard.overview(YEAR, null);

        assertEquals(0, vo.getStudentTotal());
        assertEquals(new BigDecimal("0.0"), vo.getCompletionRate());
        assertTrue(vo.getClasses().isEmpty());
        assertEquals(6, vo.getSections().size());
    }

    @Test
    @DisplayName("管理员持临时授权：不限数据范围，范围文案为全校")
    void overviewForAdminWithGrant() {
        UserContext.set(new CurrentUser(9003L, UserRoleEnum.ADMIN, true));
        when(dashboardMapper.selectClassRows(YEAR, null, null))
                .thenReturn(List.of(classRow("RJ2401", "软件2401", 3, 3, 0, 0, 0)));
        when(dashboardMapper.selectSectionRows(YEAR, null, null)).thenReturn(List.of());

        DashboardOverviewVO vo = dashboard.overview(YEAR, null);

        assertEquals("全校", vo.getScopeLabel());
        assertEquals(new BigDecimal("100.0"), vo.getCompletionRate());
        verify(dashboardMapper).selectClassRows(YEAR, null, null);
    }

    @Test
    @DisplayName("管理员无临时授权：EC-40302，且不查任何数据")
    void overviewRejectedForAdminWithoutGrant() {
        UserContext.set(new CurrentUser(9004L, UserRoleEnum.ADMIN, false));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> dashboard.overview(YEAR, null));

        assertEquals(ResultCode.DATA_SCOPE_DENIED, ex.getResultCode());
        assertEquals("管理员默认无业务数据权限", ex.getMessage());
    }

    @Test
    @DisplayName("学生：EC-40302，提示到板块页面看本人进度")
    void overviewRejectedForStudent() {
        UserContext.set(new CurrentUser(1001L, UserRoleEnum.STU, false));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> dashboard.overview(YEAR, null));

        assertEquals(ResultCode.DATA_SCOPE_DENIED, ex.getResultCode());
        assertEquals("学生请在各板块页面查看本人进度", ex.getMessage());
    }

    @Test
    @DisplayName("辅导员按班级筛选：只能查自己的班，范围文案取班级名")
    void overviewFiltersOwnClass() {
        UserContext.set(new CurrentUser(COUNSELOR_ID, UserRoleEnum.COU, false));
        when(orgClassMapper.selectOne(any()))
                .thenReturn(orgClass("RJ2401", "软件2401", COUNSELOR_ID));
        when(dashboardMapper.selectClassRows(YEAR, COUNSELOR_ID, "RJ2401"))
                .thenReturn(List.of(classRow("RJ2401", "软件2401", 3, 0, 2, 0, 0)));
        when(dashboardMapper.selectSectionRows(YEAR, COUNSELOR_ID, "RJ2401")).thenReturn(List.of());

        DashboardOverviewVO vo = dashboard.overview(YEAR, "RJ2401");

        assertEquals("软件2401", vo.getScopeLabel());
        assertEquals(2, vo.getPendingReview());
        assertEquals(1, vo.getClasses().size());
    }

    @Test
    @DisplayName("辅导员查他班：EC-40302")
    void overviewRejectsOtherClassForCounselor() {
        UserContext.set(new CurrentUser(COUNSELOR_ID, UserRoleEnum.COU, false));
        when(orgClassMapper.selectOne(any())).thenReturn(orgClass("DZ2401", "电子2401", 9002L));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> dashboard.overview(YEAR, "DZ2401"));

        assertEquals(ResultCode.DATA_SCOPE_DENIED, ex.getResultCode());
        assertEquals("只能查看本班数据", ex.getMessage());
    }

    @Test
    @DisplayName("班级不存在：EC-40003")
    void overviewRejectsUnknownClass() {
        UserContext.set(new CurrentUser(COUNSELOR_ID, UserRoleEnum.COU, false));
        when(orgClassMapper.selectOne(any())).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> dashboard.overview(YEAR, "XX9999"));

        assertEquals(ResultCode.PARAM_INVALID, ex.getResultCode());
        assertTrue(ex.getMessage().contains("XX9999"));
    }

    @Test
    @DisplayName("未分班学生：班级名回落为「未分班」，不被统计漏掉")
    void unassignedClassIsKept() {
        UserContext.set(new CurrentUser(9003L, UserRoleEnum.ADMIN, true));
        when(dashboardMapper.selectClassRows(YEAR, null, null))
                .thenReturn(List.of(classRow("未分班", null, 2, 0, 0, 0, 2)));
        when(dashboardMapper.selectSectionRows(YEAR, null, null)).thenReturn(List.of());

        DashboardOverviewVO vo = dashboard.overview(YEAR, null);

        ClassProgressVO row = vo.getClasses().get(0);
        assertEquals("未分班", row.getClassName());
        assertEquals(2, row.getStudentTotal());
        assertEquals(2, vo.getDraftProfiles());
    }

    /** 一个班级聚合行。 */
    private DashboardClassRow classRow(String classCode, String className, int students, int completed,
                                       int submitted, int returned, int draft) {
        DashboardClassRow row = new DashboardClassRow();
        row.setClassCode(classCode);
        row.setClassName(className);
        row.setStudentTotal(students);
        row.setCompletedStudents(completed);
        row.setSubmittedProfiles(submitted);
        row.setReturnedProfiles(returned);
        row.setDraftProfiles(draft);
        return row;
    }

    /** 基本信息四状态各一人 + 导师寄语一条待审，其余板块本轮无数据。 */
    private List<DashboardSectionRow> sectionRows() {
        List<DashboardSectionRow> rows = new ArrayList<>();
        rows.add(sectionRow("BASIC", "DRAFT", 1, 1));
        rows.add(sectionRow("BASIC", "RETURNED", 1, 1));
        rows.add(sectionRow("BASIC", "APPROVED", 1, 1));
        rows.add(sectionRow("BASIC", "LOCKED", 1, 1));
        rows.add(sectionRow("MENTOR", "SUBMITTED", 1, 1));
        return rows;
    }

    private DashboardSectionRow sectionRow(String sectionCode, String status, int profiles, int students) {
        DashboardSectionRow row = new DashboardSectionRow();
        row.setSectionCode(sectionCode);
        row.setStatus(status);
        row.setProfileCount(profiles);
        row.setStudentCount(students);
        return row;
    }

    private OrgClass orgClass(String code, String name, Long counselorId) {
        OrgClass orgClass = new OrgClass();
        orgClass.setClassCode(code);
        orgClass.setClassName(name);
        orgClass.setCounselorUserId(counselorId);
        orgClass.setStatus(1);
        return orgClass;
    }
}
