package com.college.growth.ledger.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.context.UserContext;
import com.college.growth.common.enums.UserRoleEnum;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.org.entity.OrgClass;
import com.college.growth.common.org.mapper.OrgClassMapper;
import com.college.growth.common.result.PageResult;
import com.college.growth.common.result.ResultCode;
import com.college.growth.ledger.common.LedgerConstants;
import com.college.growth.ledger.mapper.LedgerClassStatRow;
import com.college.growth.ledger.mapper.LedgerMapper;
import com.college.growth.ledger.mapper.LedgerTaskRow;
import com.college.growth.ledger.service.impl.LedgerServiceImpl;
import com.college.growth.ledger.vo.LedgerClassVO;
import com.college.growth.ledger.vo.LedgerRowVO;
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
 * 班级台账单测（F-07 / AC-020）。
 *
 * <p>重点覆盖三类风险：数据范围只能在服务端收口（辅导员越权班级必须 403）、
 * 筛选口径与计数必须同源（AC-020 的「筛出 5 份、计数 5」）、
 * 未建档行与分页边界不能抛异常。SQL 本身的语法与联合查询由真库联调覆盖。</p>
 */
@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    private static final String YEAR = "2025-2026-1";

    private static final long COUNSELOR_ID = 9001L;

    @Mock
    private LedgerMapper ledgerMapper;

    @Mock
    private OrgClassMapper orgClassMapper;

    private LedgerServiceImpl ledger;

    @BeforeEach
    void setUp() {
        ledger = new LedgerServiceImpl(ledgerMapper, orgClassMapper);
        // 默认：按班级编码查不到班级；需要命中时由各用例覆盖
        lenient().when(orgClassMapper.selectOne(any())).thenReturn(null);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("辅导员汇总：只统计带班，分母为在册学生，未建档 = 在册 - 建档")
    void classesForCounselor() {
        UserContext.set(new CurrentUser(COUNSELOR_ID, UserRoleEnum.COU, false));
        when(orgClassMapper.selectList(any()))
                .thenReturn(List.of(orgClass("RJ2401", "软件2401", COUNSELOR_ID)));
        when(ledgerMapper.selectClassStats(YEAR, COUNSELOR_ID))
                .thenReturn(List.of(classStat("RJ2401", "软件2401", 3, 2, 2, 1, 0, 0)));

        List<LedgerClassVO> rows = ledger.listClasses(YEAR);

        assertEquals(1, rows.size());
        LedgerClassVO row = rows.get(0);
        assertEquals("RJ2401", row.getClassCode());
        assertEquals("软件2401", row.getClassName());
        assertEquals(3, row.getStudentTotal());
        assertEquals(2, row.getArchivedStudents());
        assertEquals(1, row.getUnarchivedStudents());
        assertEquals(2, row.getCompletedStudents());
        // 2/3 = 66.666…，一位小数四舍五入
        assertEquals(new BigDecimal("66.7"), row.getCompletionRate());
        assertEquals(1, row.getPendingProfiles());
        verify(ledgerMapper).selectClassStats(YEAR, COUNSELOR_ID);
    }

    @Test
    @DisplayName("汇总：没有学生的班级也要出现，各项为 0、完成率 0.0")
    void classesKeepEmptyClass() {
        UserContext.set(new CurrentUser(COUNSELOR_ID, UserRoleEnum.COU, false));
        when(orgClassMapper.selectList(any()))
                .thenReturn(List.of(orgClass("RJ2401", "软件2401", COUNSELOR_ID)));
        when(ledgerMapper.selectClassStats(YEAR, COUNSELOR_ID)).thenReturn(List.of());

        List<LedgerClassVO> rows = ledger.listClasses(YEAR);

        assertEquals(1, rows.size());
        LedgerClassVO row = rows.get(0);
        assertEquals("软件2401", row.getClassName());
        assertEquals(0, row.getStudentTotal());
        assertEquals(0, row.getArchivedStudents());
        assertEquals(0, row.getUnarchivedStudents());
        assertEquals(new BigDecimal("0.0"), row.getCompletionRate());
    }

    @Test
    @DisplayName("汇总：管理员持授权不限范围，未分班桶排在最后")
    void classesForAdminIncludeUnassigned() {
        UserContext.set(new CurrentUser(9003L, UserRoleEnum.ADMIN, true));
        when(orgClassMapper.selectList(any()))
                .thenReturn(List.of(orgClass("RJ2401", "软件2401", COUNSELOR_ID)));
        when(ledgerMapper.selectClassStats(YEAR, null)).thenReturn(List.of(
                classStat("RJ2401", "软件2401", 3, 3, 3, 0, 0, 0),
                classStat(LedgerConstants.UNASSIGNED_CLASS, null, 2, 0, 0, 0, 0, 0)));

        List<LedgerClassVO> rows = ledger.listClasses(YEAR);

        assertEquals(2, rows.size());
        assertEquals("未分班", rows.get(1).getClassCode());
        assertEquals("未分班", rows.get(1).getClassName());
        assertEquals(2, rows.get(1).getUnarchivedStudents());
        assertEquals(new BigDecimal("100.0"), rows.get(0).getCompletionRate());
        verify(ledgerMapper).selectClassStats(YEAR, null);
    }

    @Test
    @DisplayName("AC-020：筛选待审核时 count 与列表行数一致（5 份）")
    void tasksCountMatchesSubmittedRows() {
        UserContext.set(new CurrentUser(COUNSELOR_ID, UserRoleEnum.COU, false));
        when(ledgerMapper.countTasks(YEAR, COUNSELOR_ID, null, "SUBMITTED", null)).thenReturn(5L);
        when(ledgerMapper.selectTaskPage(YEAR, COUNSELOR_ID, null, "SUBMITTED", null, 0L, 20L))
                .thenReturn(submittedRows(5));

        // 小写传参也要归一化为大写，避免前端大小写不一致查不到数据
        PageResult<LedgerRowVO> page = ledger.listTasks(YEAR, null, "submitted", null, null, null);

        assertEquals(5L, page.getTotal());
        assertEquals(5, page.getRecords().size());
        assertEquals(1L, page.getPageNum());
        assertEquals(20L, page.getPageSize());
        assertEquals("已提交", page.getRecords().get(0).getStatusLabel());
        verify(ledgerMapper).countTasks(YEAR, COUNSELOR_ID, null, "SUBMITTED", null);
        verify(ledgerMapper).selectTaskPage(YEAR, COUNSELOR_ID, null, "SUBMITTED", null, 0L, 20L);
    }

    @Test
    @DisplayName("未建档：status 归一化为 UNARCHIVED，每生一行且 sectionCode 为空")
    void tasksNormalizeUnarchived() {
        UserContext.set(new CurrentUser(COUNSELOR_ID, UserRoleEnum.COU, false));
        when(ledgerMapper.countTasks(YEAR, COUNSELOR_ID, null, "UNARCHIVED", null)).thenReturn(1L);
        LedgerTaskRow row = new LedgerTaskRow();
        row.setStudentId(1001L);
        row.setStudentNo("2024010101");
        row.setStudentName("李明");
        row.setClassCode("RJ2401");
        row.setClassName("软件2401");
        row.setStatus("UNARCHIVED");
        when(ledgerMapper.selectTaskPage(YEAR, COUNSELOR_ID, null, "UNARCHIVED", null, 0L, 20L))
                .thenReturn(List.of(row));

        PageResult<LedgerRowVO> page = ledger.listTasks(YEAR, null, "unarchived", "", 1L, 20L);

        assertEquals(1, page.getRecords().size());
        LedgerRowVO vo = page.getRecords().get(0);
        assertNull(vo.getProfileId());
        assertNull(vo.getSectionCode());
        assertNull(vo.getSectionName());
        assertEquals("UNARCHIVED", vo.getStatus());
        assertEquals("未建档", vo.getStatusLabel());
        assertEquals(0, vo.getSubmitCount());
    }

    @Test
    @DisplayName("非法状态：EC-40003，且不查任何台账数据")
    void tasksRejectUnknownStatus() {
        UserContext.set(new CurrentUser(COUNSELOR_ID, UserRoleEnum.COU, false));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> ledger.listTasks(YEAR, null, "ARCHIVED", null, 1L, 20L));

        assertEquals(ResultCode.PARAM_INVALID, ex.getResultCode());
        assertTrue(ex.getMessage().contains("ARCHIVED"));
        verifyNoInteractions(ledgerMapper);
    }

    @Test
    @DisplayName("分页边界：页码 0、每页 101 条与超大页码均返回 EC-40003")
    void tasksRejectInvalidPagination() {
        UserContext.set(new CurrentUser(COUNSELOR_ID, UserRoleEnum.COU, false));

        BusinessException zeroPage = assertThrows(BusinessException.class,
                () -> ledger.listTasks(YEAR, null, null, null, 0L, 20L));
        assertEquals(ResultCode.PARAM_INVALID, zeroPage.getResultCode());

        BusinessException oversized = assertThrows(BusinessException.class,
                () -> ledger.listTasks(YEAR, null, null, null, 1L, 101L));
        assertEquals(ResultCode.PARAM_INVALID, oversized.getResultCode());

        // 偏移量乘法溢出必须拒绝，不能变成负偏移让数据库从头取
        BusinessException overflow = assertThrows(BusinessException.class,
                () -> ledger.listTasks(YEAR, null, null, null, Long.MAX_VALUE, 100L));
        assertEquals(ResultCode.PARAM_INVALID, overflow.getResultCode());
        verifyNoInteractions(ledgerMapper);
    }

    @Test
    @DisplayName("空数据：返回 0 行、total 0，不抛异常也不查明细")
    void tasksEmptyReturnsZeroRows() {
        UserContext.set(new CurrentUser(COUNSELOR_ID, UserRoleEnum.COU, false));
        when(ledgerMapper.countTasks(YEAR, COUNSELOR_ID, null, null, null)).thenReturn(0L);

        PageResult<LedgerRowVO> page = ledger.listTasks(YEAR, null, null, null, null, null);

        assertEquals(0L, page.getTotal());
        assertTrue(page.getRecords().isEmpty());
        assertEquals(1L, page.getPageNum());
        assertEquals(20L, page.getPageSize());
        verify(ledgerMapper, never()).selectTaskPage(any(), any(), any(), any(), any(),
                anyLong(), anyLong());
    }

    @Test
    @DisplayName("越权班级：辅导员传他班编码返回 EC-40302，且不查明细")
    void tasksRejectOtherClassForCounselor() {
        UserContext.set(new CurrentUser(COUNSELOR_ID, UserRoleEnum.COU, false));
        when(orgClassMapper.selectOne(any())).thenReturn(orgClass("DZ2401", "电子2401", 9002L));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> ledger.listTasks(YEAR, "DZ2401", null, null, 1L, 20L));

        assertEquals(ResultCode.DATA_SCOPE_DENIED, ex.getResultCode());
        assertEquals("只能查看本班数据", ex.getMessage());
        verifyNoInteractions(ledgerMapper);
    }

    @Test
    @DisplayName("班级不存在：EC-40003，提示里带班级编码")
    void tasksRejectUnknownClass() {
        UserContext.set(new CurrentUser(COUNSELOR_ID, UserRoleEnum.COU, false));
        when(orgClassMapper.selectOne(any())).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> ledger.listTasks(YEAR, "XX9999", null, null, 1L, 20L));

        assertEquals(ResultCode.PARAM_INVALID, ex.getResultCode());
        assertTrue(ex.getMessage().contains("XX9999"));
    }

    @Test
    @DisplayName("未分班：辅导员无权筛选，管理员（持授权）可筛")
    void unassignedClassScope() {
        UserContext.set(new CurrentUser(COUNSELOR_ID, UserRoleEnum.COU, false));
        BusinessException denied = assertThrows(BusinessException.class,
                () -> ledger.listTasks(YEAR, LedgerConstants.UNASSIGNED_CLASS, null, null, 1L, 20L));
        assertEquals(ResultCode.DATA_SCOPE_DENIED, denied.getResultCode());

        UserContext.set(new CurrentUser(9003L, UserRoleEnum.ADMIN, true));
        when(ledgerMapper.countTasks(YEAR, null, LedgerConstants.UNASSIGNED_CLASS, null, null))
                .thenReturn(1L);
        when(ledgerMapper.selectTaskPage(YEAR, null, LedgerConstants.UNASSIGNED_CLASS, null, null, 0L, 20L))
                .thenReturn(List.of(new LedgerTaskRow()));
        PageResult<LedgerRowVO> page = ledger.listTasks(YEAR, LedgerConstants.UNASSIGNED_CLASS,
                null, null, 1L, 20L);
        assertEquals(1L, page.getTotal());
    }

    @Test
    @DisplayName("管理员无临时授权：EC-40302，不查任何数据")
    void tasksRejectAdminWithoutGrant() {
        UserContext.set(new CurrentUser(9004L, UserRoleEnum.ADMIN, false));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> ledger.listTasks(YEAR, null, null, null, null, null));

        assertEquals(ResultCode.DATA_SCOPE_DENIED, ex.getResultCode());
        assertEquals("管理员默认无业务数据权限", ex.getMessage());
        verifyNoInteractions(ledgerMapper);
    }

    @Test
    @DisplayName("学生：EC-40302，提示无权查看台账")
    void tasksRejectStudent() {
        UserContext.set(new CurrentUser(1001L, UserRoleEnum.STU, false));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> ledger.listClasses(YEAR));

        assertEquals(ResultCode.DATA_SCOPE_DENIED, ex.getResultCode());
        assertTrue(ex.getMessage().contains("学生"));
        verifyNoInteractions(ledgerMapper);
    }

    @Test
    @DisplayName("关键字去空格后传给 SQL；班级筛选命中本班时按班过滤")
    void tasksPassNormalizedFilters() {
        UserContext.set(new CurrentUser(COUNSELOR_ID, UserRoleEnum.COU, false));
        when(orgClassMapper.selectOne(any()))
                .thenReturn(orgClass("RJ2401", "软件2401", COUNSELOR_ID));
        when(ledgerMapper.countTasks(YEAR, COUNSELOR_ID, "RJ2401", null, "李明")).thenReturn(1L);
        when(ledgerMapper.selectTaskPage(YEAR, COUNSELOR_ID, "RJ2401", null, "李明", 10L, 10L))
                .thenReturn(List.of(submittedRows(1).get(0)));

        PageResult<LedgerRowVO> page = ledger.listTasks(YEAR, " RJ2401 ", "ALL", "  李明  ", 2L, 10L);

        assertEquals(1L, page.getTotal());
        assertEquals(2L, page.getPageNum());
        // 第二页的偏移量是 (2-1)*10
        verify(ledgerMapper).selectTaskPage(YEAR, COUNSELOR_ID, "RJ2401", null, "李明", 10L, 10L);
    }

    /** 一个班级统计行：在册、建档、完成、待审、退回、草稿。 */
    private LedgerClassStatRow classStat(String code, String name, int students, int archived,
                                         int completed, int submitted, int returned, int draft) {
        LedgerClassStatRow row = new LedgerClassStatRow();
        row.setClassCode(code);
        row.setClassName(name);
        row.setStudentTotal(students);
        row.setArchivedStudents(archived);
        row.setCompletedStudents(completed);
        row.setPendingProfiles(submitted);
        row.setReturnedProfiles(returned);
        row.setDraftProfiles(draft);
        return row;
    }

    private List<LedgerTaskRow> submittedRows(int count) {
        List<LedgerTaskRow> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            LedgerTaskRow row = new LedgerTaskRow();
            row.setProfileId(3000L + i);
            row.setStudentId(1000L + i);
            row.setStudentNo("202401010" + i);
            row.setStudentName("学生" + i);
            row.setClassCode("RJ2401");
            row.setClassName("软件2401");
            row.setSectionCode("BASIC");
            row.setStatus("SUBMITTED");
            row.setSubmitCount(1);
            rows.add(row);
        }
        return rows;
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
