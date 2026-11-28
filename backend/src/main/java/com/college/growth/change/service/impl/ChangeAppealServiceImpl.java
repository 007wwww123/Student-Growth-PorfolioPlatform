package com.college.growth.change.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.college.growth.change.service.ChangeAppealService;
import com.college.growth.change.vo.AppealVO;
import com.college.growth.change.vo.ChangeLogVO;
import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.context.UserContext;
import com.college.growth.common.enums.SectionCodeEnum;
import com.college.growth.common.enums.UserRoleEnum;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.field.entity.ArcFieldDef;
import com.college.growth.common.field.mapper.ArcFieldDefMapper;
import com.college.growth.common.log.entity.ArcChangeLog;
import com.college.growth.common.log.mapper.ArcChangeLogMapper;
import com.college.growth.common.log.service.AuditLogService;
import com.college.growth.common.org.entity.OrgClass;
import com.college.growth.common.org.mapper.OrgClassMapper;
import com.college.growth.common.org.service.StudentClassService;
import com.college.growth.common.result.PageResult;
import com.college.growth.common.result.ResultCode;
import com.college.growth.common.student.entity.StuStudent;
import com.college.growth.common.student.mapper.StuStudentMapper;
import com.college.growth.common.util.AcademicYearUtils;
import com.college.growth.common.util.TextUtils;
import com.college.growth.auth.entity.SysUser;
import com.college.growth.auth.mapper.SysUserMapper;
import com.college.growth.grant.entity.SysApproval;
import com.college.growth.notification.service.NotificationService;
import com.college.growth.grant.mapper.SysApprovalMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 变更记录与申诉（F-18 / AC-070）。
 *
 * <p>可见范围：学生本人、辅导员本班、管理员（`archive:change:read`）。
 * 申诉复用 {@code sys_approval} 的 APPEAL 类型，COU 只能处理本班学生的申诉。</p>
 */
@Service
public class ChangeAppealServiceImpl implements ChangeAppealService {

    private static final String TYPE_APPEAL = "APPEAL";

    private static final String PENDING = "PENDING";

    private static final String APPROVED = "APPROVED";

    private static final String REJECTED = "REJECTED";

    private final ArcChangeLogMapper changeLogMapper;

    private final ArcFieldDefMapper fieldDefMapper;

    private final StuStudentMapper studentMapper;

    private final SysUserMapper userMapper;

    private final SysApprovalMapper approvalMapper;

    private final OrgClassMapper orgClassMapper;

    private final StudentClassService studentClassService;

    private final AuditLogService auditLogService;

    private final NotificationService notificationService;

    public ChangeAppealServiceImpl(ArcChangeLogMapper changeLogMapper, ArcFieldDefMapper fieldDefMapper,
                                   StuStudentMapper studentMapper, SysUserMapper userMapper,
                                   SysApprovalMapper approvalMapper, OrgClassMapper orgClassMapper,
                                   StudentClassService studentClassService, AuditLogService auditLogService,
                                   NotificationService notificationService) {
        this.changeLogMapper = changeLogMapper;
        this.fieldDefMapper = fieldDefMapper;
        this.studentMapper = studentMapper;
        this.userMapper = userMapper;
        this.approvalMapper = approvalMapper;
        this.orgClassMapper = orgClassMapper;
        this.studentClassService = studentClassService;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
    }

    @Override
    public PageResult<ChangeLogVO> list(Long studentId, String sectionCode, String bizType, String fieldCode,
                                        String from, String to, long pageNum, long pageSize) {
        CurrentUser user = UserContext.get();
        Long scopedStudent = studentId;
        Set<Long> classStudents = null;
        if (user.role() == UserRoleEnum.STU) {
            scopedStudent = user.userId();
        } else if (user.role() == UserRoleEnum.COU) {
            if (scopedStudent != null) {
                studentClassService.requireStudentScope(scopedStudent, AcademicYearUtils.current(), user);
            } else {
                classStudents = classStudentIds(user.userId());
                if (classStudents.isEmpty()) {
                    return new PageResult<>(List.of(), 0, 1, pageSize);
                }
            }
        }
        IPage<ArcChangeLog> page = changeLogMapper.selectPage(page(pageNum, pageSize),
                Wrappers.<ArcChangeLog>lambdaQuery()
                        .eq(scopedStudent != null, ArcChangeLog::getStudentId, scopedStudent)
                        .in(classStudents != null, ArcChangeLog::getStudentId, classStudents)
                        .eq(!TextUtils.isBlank(sectionCode), ArcChangeLog::getSectionCode, upper(sectionCode))
                        .eq(!TextUtils.isBlank(bizType), ArcChangeLog::getBizType, upper(bizType))
                        .eq(!TextUtils.isBlank(fieldCode), ArcChangeLog::getFieldCode, fieldCode)
                        .ge(!TextUtils.isBlank(from), ArcChangeLog::getCreatedAt, start(from))
                        .lt(!TextUtils.isBlank(to), ArcChangeLog::getCreatedAt,
                                TextUtils.isBlank(to) ? null : date(to).plusDays(1).atStartOfDay())
                        .orderByDesc(ArcChangeLog::getId));
        List<ChangeLogVO> rows = toChangeVOs(page.getRecords());
        return new PageResult<>(rows, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public AppealVO appeal(Long changeId, String reason) {
        CurrentUser user = requireStudent();
        ArcChangeLog change = changeId == null ? null : changeLogMapper.selectById(changeId);
        if (change == null) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "变更记录不存在：" + changeId);
        }
        if (!change.getStudentId().equals(user.userId())) {
            throw new BusinessException(ResultCode.DATA_SCOPE_DENIED, "只能对本人变更发起申诉");
        }
        if (TextUtils.isBlank(reason) || reason.trim().length() < 5 || reason.trim().length() > 500) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "申诉理由 5-500 字");
        }
        long open = approvalMapper.selectCount(Wrappers.<SysApproval>lambdaQuery()
                .eq(SysApproval::getApprovalType, TYPE_APPEAL)
                .eq(SysApproval::getBizId, changeId)
                .in(SysApproval::getStatus, PENDING, APPROVED));
        if (open > 0) {
            throw new BusinessException(ResultCode.STATE_CONFLICT, "该变更已有待处理或已通过的申诉");
        }
        SysApproval appeal = new SysApproval();
        appeal.setApprovalType(TYPE_APPEAL);
        appeal.setApplicantId(user.userId());
        appeal.setStudentId(change.getStudentId());
        appeal.setBizId(changeId);
        appeal.setReason(reason.trim());
        appeal.setStatus(PENDING);
        approvalMapper.insert(appeal);
        audit(appeal, AuditLogService.ACTION_APPLY, "发起变更申诉：" + reason.trim());
        return toAppealVO(appeal);
    }

    @Override
    public PageResult<AppealVO> appeals(String status, long pageNum, long pageSize) {
        CurrentUser user = UserContext.get();
        Set<Long> classStudents = null;
        Long applicant = null;
        if (user.role() == UserRoleEnum.STU) {
            applicant = user.userId();
        } else if (user.role() == UserRoleEnum.COU) {
            classStudents = classStudentIds(user.userId());
            if (classStudents.isEmpty()) {
                return new PageResult<>(List.of(), 0, 1, pageSize);
            }
        }
        IPage<SysApproval> page = approvalMapper.selectPage(page(pageNum, pageSize),
                Wrappers.<SysApproval>lambdaQuery()
                        .eq(SysApproval::getApprovalType, TYPE_APPEAL)
                        .eq(applicant != null, SysApproval::getApplicantId, applicant)
                        .in(classStudents != null, SysApproval::getStudentId, classStudents)
                        .eq(!TextUtils.isBlank(status), SysApproval::getStatus, upper(status))
                        .orderByDesc(SysApproval::getId));
        return new PageResult<>(toAppealVOs(page.getRecords()), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    @Transactional
    public AppealVO approve(Long appealId, String remark) {
        CurrentUser approver = requireCounselor();
        SysApproval appeal = requirePending(appealId);
        requireClassScope(appeal, approver);
        appeal.setStatus(APPROVED);
        appeal.setApproverId(approver.userId());
        appeal.setApproverRemark(TextUtils.isBlank(remark) ? null : remark.trim());
        appeal.setApprovedAt(LocalDateTime.now());
        approvalMapper.updateById(appeal);
        audit(appeal, AuditLogService.ACTION_APPROVE, "申诉通过：" + nullSafe(remark));
        notificationService.notify(appeal.getStudentId(), "APPEAL_RESULT", "申诉已通过",
                nullSafe(remark), "APPEAL", appeal.getId());
        return toAppealVO(appeal);
    }

    @Override
    @Transactional
    public AppealVO reject(Long appealId, String remark) {
        CurrentUser approver = requireCounselor();
        if (TextUtils.isBlank(remark)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "拒绝时必须填写处理意见");
        }
        SysApproval appeal = requirePending(appealId);
        requireClassScope(appeal, approver);
        appeal.setStatus(REJECTED);
        appeal.setApproverId(approver.userId());
        appeal.setApproverRemark(remark.trim());
        appeal.setApprovedAt(LocalDateTime.now());
        approvalMapper.updateById(appeal);
        audit(appeal, AuditLogService.ACTION_REJECT, "申诉拒绝：" + remark.trim());
        notificationService.notify(appeal.getStudentId(), "APPEAL_RESULT", "申诉被拒绝",
                remark.trim(), "APPEAL", appeal.getId());
        return toAppealVO(appeal);
    }

    // ------------------------------------------------------------------

    private List<ChangeLogVO> toChangeVOs(List<ArcChangeLog> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<Long> studentIds = rows.stream().map(ArcChangeLog::getStudentId).collect(Collectors.toSet());
        Set<Long> operatorIds = rows.stream().map(ArcChangeLog::getOperatorId)
                .filter(id -> id != null && id > 0).collect(Collectors.toSet());
        Set<String> fieldCodes = rows.stream().map(ArcChangeLog::getFieldCode)
                .filter(code -> !TextUtils.isBlank(code)).collect(Collectors.toSet());
        Map<Long, String> students = namesOfStudents(studentIds);
        Map<Long, String> operators = namesOfUsers(operatorIds);
        Map<String, String> fields = fieldNames(fieldCodes);
        Map<Long, SysApproval> appeals = appealsOfChanges(rows.stream().map(ArcChangeLog::getId).toList());
        List<ChangeLogVO> result = new ArrayList<>(rows.size());
        for (ArcChangeLog row : rows) {
            ChangeLogVO vo = new ChangeLogVO();
            vo.setId(row.getId());
            vo.setStudentId(row.getStudentId());
            vo.setStudentName(students.get(row.getStudentId()));
            vo.setSectionCode(row.getSectionCode());
            vo.setSectionName(sectionName(row.getSectionCode()));
            vo.setBizType(row.getBizType());
            vo.setFieldCode(row.getFieldCode());
            vo.setFieldName(fields.getOrDefault(row.getFieldCode(), row.getFieldCode()));
            vo.setOldValue(row.getOldValue());
            vo.setNewValue(row.getNewValue());
            vo.setFromStatus(row.getFromStatus());
            vo.setToStatus(row.getToStatus());
            vo.setOperatorId(row.getOperatorId());
            vo.setOperatorName(row.getOperatorId() != null && row.getOperatorId() == 0
                    ? "系统" : operators.get(row.getOperatorId()));
            vo.setOperatorRole(row.getOperatorRole());
            vo.setSource(row.getSource());
            vo.setReason(row.getReason());
            vo.setCreatedAt(row.getCreatedAt());
            SysApproval appeal = appeals.get(row.getId());
            if (appeal != null) {
                vo.setAppealId(appeal.getId());
                vo.setAppealStatus(appeal.getStatus());
            }
            result.add(vo);
        }
        return result;
    }

    private List<AppealVO> toAppealVOs(List<SysApproval> rows) {
        List<AppealVO> result = new ArrayList<>(rows.size());
        for (SysApproval row : rows) {
            result.add(toAppealVO(row));
        }
        return result;
    }

    private AppealVO toAppealVO(SysApproval approval) {
        AppealVO vo = new AppealVO();
        vo.setId(approval.getId());
        vo.setChangeId(approval.getBizId());
        vo.setStudentId(approval.getStudentId());
        vo.setApplicantId(approval.getApplicantId());
        vo.setReason(approval.getReason());
        vo.setStatus(approval.getStatus());
        vo.setApproverId(approval.getApproverId());
        vo.setApproverRemark(approval.getApproverRemark());
        vo.setCreatedAt(approval.getCreatedAt());
        vo.setApprovedAt(approval.getApprovedAt());
        ArcChangeLog change = approval.getBizId() == null ? null : changeLogMapper.selectById(approval.getBizId());
        if (change != null) {
            vo.setStudentId(change.getStudentId());
            vo.setSectionCode(change.getSectionCode());
            vo.setSectionName(sectionName(change.getSectionCode()));
            vo.setFieldCode(change.getFieldCode());
            vo.setOldValue(change.getOldValue());
            vo.setNewValue(change.getNewValue());
            Map<String, String> fields = fieldNames(Set.of(change.getFieldCode() == null ? "" : change.getFieldCode()));
            vo.setFieldName(fields.getOrDefault(change.getFieldCode(), change.getFieldCode()));
        }
        vo.setStudentName(vo.getStudentId() == null ? null : namesOfStudents(Set.of(vo.getStudentId())).get(vo.getStudentId()));
        vo.setApplicantName(namesOfUsers(Set.of(approval.getApplicantId())).get(approval.getApplicantId()));
        if (approval.getApproverId() != null) {
            vo.setApproverName(namesOfUsers(Set.of(approval.getApproverId())).get(approval.getApproverId()));
        }
        return vo;
    }

    private Map<Long, SysApproval> appealsOfChanges(List<Long> changeIds) {
        if (changeIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, SysApproval> result = new HashMap<>();
        approvalMapper.selectList(Wrappers.<SysApproval>lambdaQuery()
                        .eq(SysApproval::getApprovalType, TYPE_APPEAL)
                        .in(SysApproval::getBizId, changeIds)
                        .orderByDesc(SysApproval::getId))
                .forEach(row -> result.putIfAbsent(row.getBizId(), row));
        return result;
    }

    private Set<Long> classStudentIds(Long counselorId) {
        List<String> classCodes = orgClassMapper.selectList(Wrappers.<OrgClass>lambdaQuery()
                        .eq(OrgClass::getCounselorUserId, counselorId)
                        .eq(OrgClass::getStatus, 1))
                .stream().map(OrgClass::getClassCode).filter(code -> !TextUtils.isBlank(code)).toList();
        if (classCodes.isEmpty()) {
            return Set.of();
        }
        return studentMapper.selectList(Wrappers.<StuStudent>lambdaQuery()
                        .in(StuStudent::getClassCode, classCodes))
                .stream().map(StuStudent::getId).collect(Collectors.toSet());
    }

    private Map<Long, String> namesOfStudents(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return studentMapper.selectBatchIds(ids).stream().collect(Collectors.toMap(
                StuStudent::getId, StuStudent::getName, (a, b) -> a));
    }

    private Map<Long, String> namesOfUsers(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(ids).stream().collect(Collectors.toMap(
                SysUser::getId, user -> user.getDisplayName() == null ? user.getUsername() : user.getDisplayName(),
                (a, b) -> a));
    }

    private Map<String, String> fieldNames(Set<String> codes) {
        if (codes.isEmpty()) {
            return Map.of();
        }
        return fieldDefMapper.selectList(Wrappers.<ArcFieldDef>lambdaQuery()
                        .in(ArcFieldDef::getFieldCode, codes))
                .stream().collect(Collectors.toMap(ArcFieldDef::getFieldCode, ArcFieldDef::getFieldName, (a, b) -> a));
    }

    private String sectionName(String code) {
        try {
            return SectionCodeEnum.valueOf(code).getLabel();
        } catch (Exception ex) {
            return code;
        }
    }

    private CurrentUser requireStudent() {
        CurrentUser user = UserContext.get();
        if (user == null || user.role() != UserRoleEnum.STU) {
            throw new BusinessException(ResultCode.PERMISSION_DENIED, "仅学生本人可发起申诉");
        }
        return user;
    }

    private CurrentUser requireCounselor() {
        CurrentUser user = UserContext.get();
        if (user == null || (user.role() != UserRoleEnum.COU && user.role() != UserRoleEnum.ADMIN)) {
            throw new BusinessException(ResultCode.PERMISSION_DENIED, "仅辅导员或管理员可处理申诉");
        }
        return user;
    }

    private SysApproval requirePending(Long id) {
        SysApproval appeal = id == null ? null : approvalMapper.selectById(id);
        if (appeal == null || !TYPE_APPEAL.equals(appeal.getApprovalType())) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "申诉不存在：" + id);
        }
        if (!PENDING.equals(appeal.getStatus())) {
            throw new BusinessException(ResultCode.STATE_CONFLICT, "申诉已处理");
        }
        return appeal;
    }

    private void requireClassScope(SysApproval appeal, CurrentUser approver) {
        if (approver.role() == UserRoleEnum.ADMIN) {
            return;
        }
        if (appeal.getStudentId() == null
                || !studentClassService.isCounselorOf(appeal.getStudentId(),
                AcademicYearUtils.current(), approver.userId())) {
            throw new BusinessException(ResultCode.DATA_SCOPE_DENIED, "只能处理本班学生的申诉");
        }
    }

    private void audit(SysApproval appeal, String action, String detail) {
        auditLogService.record(AuditLogService.BIZ_APPEAL, AuditLogService.TARGET_APPROVAL,
                appeal.getId(), appeal.getStudentId(), null, null, action, detail);
    }

    private <T> Page<T> page(long pageNum, long pageSize) {
        return new Page<>(Math.max(1, pageNum), Math.min(Math.max(1, pageSize), 100));
    }

    private String upper(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }

    private LocalDateTime start(String from) {
        return TextUtils.isBlank(from) ? null : date(from).atStartOfDay();
    }

    private LocalDate date(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "日期格式应为 yyyy-MM-dd");
        }
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
