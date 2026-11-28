package com.college.growth.ledger.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.context.UserContext;
import com.college.growth.common.enums.ProfileStatusEnum;
import com.college.growth.common.enums.SectionCodeEnum;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.org.entity.OrgClass;
import com.college.growth.common.org.mapper.OrgClassMapper;
import com.college.growth.common.result.PageResult;
import com.college.growth.common.result.ResultCode;
import com.college.growth.common.util.AcademicYearUtils;
import com.college.growth.common.util.TextUtils;
import com.college.growth.ledger.common.LedgerConstants;
import com.college.growth.ledger.mapper.LedgerClassStatRow;
import com.college.growth.ledger.mapper.LedgerMapper;
import com.college.growth.ledger.mapper.LedgerTaskRow;
import com.college.growth.ledger.service.LedgerService;
import com.college.growth.ledger.vo.LedgerClassVO;
import com.college.growth.ledger.vo.LedgerRowVO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 班级台账实现（F-07 / AC-020）。
 *
 * <p>本类是纯读能力，不加事务。count 与 list 是两次查询、两个快照，
 * 翻页期间若有学生提交，总数与当前页行数之和可能短暂不等，这是并发写入的正常表现；
 * 这里保证的是「两次查询使用完全相同的过滤条件」，即 AC-020 所说的筛选口径与计数一致，
 * 而不是牺牲读性能去做快照隔离。</p>
 *
 * <p>数据范围只在服务端收口：班级筛选参数要先过归属校验，
 * 防止「换个班级编码就能看别人班」。</p>
 */
@Service
public class LedgerServiceImpl implements LedgerService {

    private final LedgerMapper ledgerMapper;

    private final OrgClassMapper orgClassMapper;

    public LedgerServiceImpl(LedgerMapper ledgerMapper, OrgClassMapper orgClassMapper) {
        this.ledgerMapper = ledgerMapper;
        this.orgClassMapper = orgClassMapper;
    }

    @Override
    public List<LedgerClassVO> listClasses(String academicYear) {
        CurrentUser user = UserContext.get();
        Long counselorId = resolveCounselorScope(user);
        String year = AcademicYearUtils.resolve(academicYear);

        // 汇总表以「数据范围内的班级」为骨架：新建但还没有学生的班也要出现，
        // 否则 AC-005「绑定辅导员后可见该班台账」在空班场景下看不到任何东西。
        List<OrgClass> scopedClasses = loadScopedClasses(counselorId);
        List<LedgerClassStatRow> stats = ledgerMapper.selectClassStats(year, counselorId);
        return buildClassVOs(scopedClasses, stats);
    }

    @Override
    public PageResult<LedgerRowVO> listTasks(String academicYear, String classCode, String status,
                                              String keyword, Long pageNum, Long pageSize) {
        CurrentUser user = UserContext.get();
        Long counselorId = resolveCounselorScope(user);
        String year = AcademicYearUtils.resolve(academicYear);
        String normalizedClass = normalizeClassCode(classCode, counselorId);
        String normalizedStatus = normalizeStatus(status);
        String normalizedKeyword = normalizeKeyword(keyword);
        long num = pageNum == null ? LedgerConstants.DEFAULT_PAGE_NUM : pageNum;
        long size = pageSize == null ? LedgerConstants.DEFAULT_PAGE_SIZE : pageSize;
        if (num < 1) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "页码从 1 开始");
        }
        if (size < 1 || size > LedgerConstants.MAX_PAGE_SIZE) {
            throw new BusinessException(ResultCode.PARAM_INVALID,
                    "每页条数应在 1–" + LedgerConstants.MAX_PAGE_SIZE + " 之间");
        }
        long offset;
        try {
            offset = Math.multiplyExact(num - 1, size);
        } catch (ArithmeticException ex) {
            // 页码极端大时避免乘法溢出成负偏移，被数据库当成「从头取」
            throw new BusinessException(ResultCode.PARAM_INVALID, "页码超出允许范围");
        }

        // 先 count 再取页：count 与 list 共用同一段过滤 SQL，
        // AC-020 的「筛出 5 份，计数也是 5」由结构保证，而不是靠两处各写一遍条件。
        long total = ledgerMapper.countTasks(year, counselorId, normalizedClass,
                normalizedStatus, normalizedKeyword);
        if (total == 0) {
            return new PageResult<>(List.of(), 0L, num, size);
        }
        List<LedgerTaskRow> rows = ledgerMapper.selectTaskPage(year, counselorId, normalizedClass,
                normalizedStatus, normalizedKeyword, offset, size);
        return new PageResult<>(toRowVOs(rows), total, num, size);
    }

    /**
     * 解析数据范围。
     *
     * @return 辅导员 ID（只看带班）；null 表示不限（仅持临时授权的管理员）
     */
    private Long resolveCounselorScope(CurrentUser user) {
        if (user.isCounselor()) {
            if (user.userId() == null || user.userId() <= 0) {
                throw new BusinessException(ResultCode.DATA_SCOPE_DENIED, "缺少辅导员身份，无法查看班级台账");
            }
            return user.userId();
        }
        if (user.isAdminWithGrant()) {
            return null;
        }
        if (user.isStudent()) {
            throw new BusinessException(ResultCode.DATA_SCOPE_DENIED, "学生无权查看班级台账");
        }
        throw new BusinessException(ResultCode.DATA_SCOPE_DENIED, "管理员默认无业务数据权限");
    }

    /** 数据范围内的启用班级；辅导员只取本人带班。 */
    private List<OrgClass> loadScopedClasses(Long counselorId) {
        LambdaQueryWrapper<OrgClass> query = Wrappers.<OrgClass>lambdaQuery()
                .eq(OrgClass::getStatus, 1)
                .orderByAsc(OrgClass::getClassName)
                .orderByAsc(OrgClass::getClassCode);
        if (counselorId != null) {
            query.eq(OrgClass::getCounselorUserId, counselorId);
        }
        List<OrgClass> classes = orgClassMapper.selectList(query);
        return classes == null ? List.of() : classes;
    }

    /**
     * 合并「班级骨架」与「学生统计」。
     *
     * <p>正常情况下两者一一对应；管理员视角下会出现统计里有、org_class 里没有的桶
     * （未分班、班级已停用或已删除的存量数据）。这些桶照样要展示，
     * 否则它们的学生会从台账里凭空消失，班级合计也对不上全校总数。</p>
     */
    private List<LedgerClassVO> buildClassVOs(List<OrgClass> scopedClasses,
                                               List<LedgerClassStatRow> stats) {
        Map<String, LedgerClassStatRow> statByCode = new HashMap<>();
        if (stats != null) {
            for (LedgerClassStatRow row : stats) {
                if (!TextUtils.isBlank(row.getClassCode())) {
                    statByCode.put(row.getClassCode(), row);
                }
            }
        }

        List<LedgerClassVO> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (OrgClass orgClass : scopedClasses) {
            String code = orgClass.getClassCode();
            if (TextUtils.isBlank(code) || !seen.add(code)) {
                continue;
            }
            result.add(toClassVO(code, orgClass.getClassName(), statByCode.get(code)));
        }
        for (LedgerClassStatRow row : statByCode.values()) {
            if (!TextUtils.isBlank(row.getClassCode()) && seen.add(row.getClassCode())) {
                result.add(toClassVO(row.getClassCode(), row.getClassName(), row));
            }
        }
        // 未分班固定排最后；其余按班级名、编码排序，保证接口输出稳定。
        result.sort(Comparator
                .comparing((LedgerClassVO vo) -> LedgerConstants.UNASSIGNED_CLASS.equals(vo.getClassCode()))
                .thenComparing(vo -> vo.getClassName() == null ? "" : vo.getClassName())
                .thenComparing(LedgerClassVO::getClassCode));
        return result;
    }

    private LedgerClassVO toClassVO(String classCode, String className, LedgerClassStatRow row) {
        int studentTotal = row == null ? 0 : nvl(row.getStudentTotal());
        int archived = row == null ? 0 : nvl(row.getArchivedStudents());
        int completed = row == null ? 0 : nvl(row.getCompletedStudents());

        LedgerClassVO vo = new LedgerClassVO();
        vo.setClassCode(classCode);
        vo.setClassName(resolveClassName(classCode, className));
        vo.setStudentTotal(studentTotal);
        vo.setArchivedStudents(archived);
        vo.setUnarchivedStudents(Math.max(0, studentTotal - archived));
        vo.setCompletedStudents(completed);
        vo.setCompletionRate(rate(completed, studentTotal));
        vo.setPendingProfiles(row == null ? 0 : nvl(row.getPendingProfiles()));
        vo.setReturnedProfiles(row == null ? 0 : nvl(row.getReturnedProfiles()));
        vo.setDraftProfiles(row == null ? 0 : nvl(row.getDraftProfiles()));
        return vo;
    }

    private String resolveClassName(String classCode, String className) {
        if (!TextUtils.isBlank(className)) {
            return className;
        }
        // 未分班桶与「班级已删除但学生仍挂着编码」都回落到编码，前端至少能看出是哪个班。
        return LedgerConstants.UNASSIGNED_CLASS.equals(classCode) ? LedgerConstants.UNASSIGNED_CLASS : classCode;
    }

    /**
     * 校验班级筛选。
     *
     * <p>辅导员只能传自己的班；管理员（持授权）可传任意存在的班，
     * 也可传「未分班」这个虚拟桶（汇总表里有这一行，下拉选中后必须能筛出结果）。</p>
     *
     * @return 规范化后的班级编码；null 表示不限
     */
    private String normalizeClassCode(String classCode, Long counselorId) {
        if (TextUtils.isBlank(classCode)) {
            return null;
        }
        String code = classCode.trim();
        if (LedgerConstants.UNASSIGNED_CLASS.equals(code)) {
            // 未分班的学生没有班级，也就没有归属辅导员，辅导员视角一律越权。
            if (counselorId != null) {
                throw new BusinessException(ResultCode.DATA_SCOPE_DENIED, "只能查看本班数据");
            }
            return code;
        }
        OrgClass orgClass = orgClassMapper.selectOne(Wrappers.<OrgClass>lambdaQuery()
                .eq(OrgClass::getClassCode, code)
                .last("limit 1"));
        if (orgClass == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "班级不存在：" + code);
        }
        if (counselorId != null && !counselorId.equals(orgClass.getCounselorUserId())) {
            throw new BusinessException(ResultCode.DATA_SCOPE_DENIED, "只能查看本班数据");
        }
        return code;
    }

    /**
     * 状态归一化。
     *
     * @return 大写状态名；ALL/空串返回 null（SQL 不按状态过滤）；非法值抛 EC-40003
     */
    private String normalizeStatus(String status) {
        if (TextUtils.isBlank(status)) {
            return null;
        }
        String value = status.trim().toUpperCase(Locale.ROOT);
        if (LedgerConstants.STATUS_ALL.equals(value)) {
            return null;
        }
        if (LedgerConstants.STATUS_UNARCHIVED.equals(value)) {
            return value;
        }
        try {
            return ProfileStatusEnum.valueOf(value).name();
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "未知台账状态：" + status);
        }
    }

    private String normalizeKeyword(String keyword) {
        return TextUtils.isBlank(keyword) ? null : keyword.trim();
    }

    private List<LedgerRowVO> toRowVOs(List<LedgerTaskRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<LedgerRowVO> result = new ArrayList<>(rows.size());
        for (LedgerTaskRow row : rows) {
            LedgerRowVO vo = new LedgerRowVO();
            vo.setProfileId(row.getProfileId());
            vo.setStudentId(row.getStudentId());
            vo.setStudentNo(row.getStudentNo());
            vo.setStudentName(row.getStudentName());
            vo.setClassCode(row.getClassCode());
            vo.setClassName(resolveClassName(row.getClassCode(), row.getClassName()));
            vo.setSectionCode(row.getSectionCode());
            vo.setSectionName(sectionName(row.getSectionCode()));
            vo.setStatus(row.getStatus());
            vo.setStatusLabel(statusLabel(row.getStatus()));
            vo.setCompleteness(row.getCompleteness());
            vo.setSubmitCount(row.getSubmitCount() == null ? 0 : row.getSubmitCount());
            vo.setSubmittedAt(row.getSubmittedAt());
            vo.setReviewedAt(row.getReviewedAt());
            vo.setReviewerId(row.getReviewerId());
            vo.setReviewerName(row.getReviewerName());
            result.add(vo);
        }
        return result;
    }

    private String sectionName(String sectionCode) {
        SectionCodeEnum section = SectionCodeEnum.tryFromCode(sectionCode);
        return section == null ? null : section.getLabel();
    }

    private String statusLabel(String status) {
        if (LedgerConstants.STATUS_UNARCHIVED.equals(status)) {
            return LedgerConstants.STATUS_UNARCHIVED_LABEL;
        }
        try {
            return ProfileStatusEnum.valueOf(status).getLabel();
        } catch (IllegalArgumentException | NullPointerException ex) {
            // 脏数据不阻断整页：原样返回，至少能让辅导员看出异常状态。
            return status;
        }
    }

    /** 百分比一位小数；分母为 0 时返回 0.0，避免前端出现 NaN。 */
    private BigDecimal rate(int part, int total) {
        if (total <= 0) {
            return BigDecimal.ZERO.setScale(LedgerConstants.RATE_SCALE);
        }
        return BigDecimal.valueOf(part)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), LedgerConstants.RATE_SCALE, RoundingMode.HALF_UP);
    }

    private int nvl(Integer value) {
        return value == null ? 0 : value;
    }
}
