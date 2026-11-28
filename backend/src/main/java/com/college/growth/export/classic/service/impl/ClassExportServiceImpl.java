package com.college.growth.export.classic.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.college.growth.auth.entity.SysUser;
import com.college.growth.auth.mapper.SysUserMapper;
import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.context.UserContext;
import com.college.growth.common.enums.ProfileStatusEnum;
import com.college.growth.common.enums.SectionCodeEnum;
import com.college.growth.common.enums.UserRoleEnum;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.log.service.AuditLogService;
import com.college.growth.common.org.entity.OrgClass;
import com.college.growth.common.org.mapper.OrgClassMapper;
import com.college.growth.common.profile.entity.ArcProfile;
import com.college.growth.common.profile.entity.ArcProfileItem;
import com.college.growth.common.profile.mapper.ArcProfileItemMapper;
import com.college.growth.common.profile.mapper.ArcProfileMapper;
import com.college.growth.common.result.ResultCode;
import com.college.growth.common.security.IdCardCrypto;
import com.college.growth.common.student.entity.StuStudent;
import com.college.growth.common.student.mapper.StuStudentMapper;
import com.college.growth.common.util.AcademicYearUtils;
import com.college.growth.common.util.TextUtils;
import com.college.growth.export.classic.dto.ClassExportDTO;
import com.college.growth.export.classic.dto.SensitiveExportApplyDTO;
import com.college.growth.export.classic.service.ClassExportService;
import com.college.growth.export.classic.vo.SensitiveExportApprovalVO;
import com.college.growth.export.entity.SysExportTask;
import com.college.growth.export.mapper.SysExportTaskMapper;
import com.college.growth.export.vo.ExportTaskVO;
import com.college.growth.grant.entity.SysApproval;
import com.college.growth.grant.mapper.SysApprovalMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 班级汇总 Excel（F-13 / AC-044）。
 *
 * <p>COU 只能导出本班；默认列不含敏感列。手机号/身份证号必须持有
 * {@code EXPORT_SENSITIVE} 审批（4 小时有效），否则返回 {@code EC-40304}。
 * 文件落服务端私有目录，复用 {@code sys_export_task} 的 24 小时下载。</p>
 */
@Service
public class ClassExportServiceImpl implements ClassExportService {

    private static final String TYPE = "EXPORT_SENSITIVE";

    private static final String PENDING = "PENDING";

    private static final String APPROVED = "APPROVED";

    private static final String REJECTED = "REJECTED";

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Map<String, Column> COLUMNS = new LinkedHashMap<>();

    static {
        COLUMNS.put("studentNo", new Column("studentNo", "学号", false));
        COLUMNS.put("name", new Column("name", "姓名", false));
        COLUMNS.put("className", new Column("className", "班级", false));
        COLUMNS.put("gender", new Column("gender", "性别", false));
        COLUMNS.put("nation", new Column("nation", "民族", false));
        COLUMNS.put("phone", new Column("phone", "手机号", true));
        COLUMNS.put("idCard", new Column("idCard", "身份证号", true));
        COLUMNS.put("completeness", new Column("completeness", "完成度(%)", false));
        COLUMNS.put("basic", new Column("basic", "基本信息", false));
        COLUMNS.put("academic", new Column("academic", "学业发展", false));
        COLUMNS.put("interest", new Column("interest", "兴趣特长", false));
        COLUMNS.put("life", new Column("life", "生活心理", false));
        COLUMNS.put("career", new Column("career", "职业规划", false));
        COLUMNS.put("mentor", new Column("mentor", "导师寄语", false));
    }

    private final OrgClassMapper orgClassMapper;

    private final StuStudentMapper studentMapper;

    private final ArcProfileMapper profileMapper;

    private final ArcProfileItemMapper itemMapper;

    private final SysApprovalMapper approvalMapper;

    private final SysExportTaskMapper taskMapper;

    private final SysUserMapper userMapper;

    private final AuditLogService auditLogService;

    private final ObjectMapper objectMapper;

    private final Path exportDir;

    public ClassExportServiceImpl(OrgClassMapper orgClassMapper, StuStudentMapper studentMapper,
                                  ArcProfileMapper profileMapper, ArcProfileItemMapper itemMapper,
                                  SysApprovalMapper approvalMapper, SysExportTaskMapper taskMapper,
                                  SysUserMapper userMapper, AuditLogService auditLogService,
                                  ObjectMapper objectMapper,
                                  @Value("${growth.export.dir:}") String exportDir) {
        this.orgClassMapper = orgClassMapper;
        this.studentMapper = studentMapper;
        this.profileMapper = profileMapper;
        this.itemMapper = itemMapper;
        this.approvalMapper = approvalMapper;
        this.taskMapper = taskMapper;
        this.userMapper = userMapper;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
        String dir = TextUtils.isBlank(exportDir)
                ? System.getProperty("java.io.tmpdir") + File.separator + "student-growth-export"
                : exportDir;
        this.exportDir = Paths.get(dir);
    }

    @Override
    public ExportTaskVO create(ClassExportDTO dto) {
        CurrentUser user = requireCounselor();
        OrgClass orgClass = requireClass(dto.getClassCode(), user);
        String year = AcademicYearUtils.resolve(dto.getAcademicYear());
        List<Column> columns = resolveColumns(dto.getColumns());
        boolean sensitive = columns.stream().anyMatch(column -> column.sensitive);
        if (sensitive) {
            requireSensitiveApproval(dto.getApprovalId(), orgClass.getClassCode(), user);
        }
        List<StuStudent> students = studentMapper.selectList(Wrappers.<StuStudent>lambdaQuery()
                .eq(StuStudent::getClassCode, orgClass.getClassCode())
                .eq(StuStudent::getStatus, 1)
                .orderByAsc(StuStudent::getStudentNo));
        Map<Long, Map<String, ArcProfile>> profiles = profilesOf(students, year);
        Map<Long, String> phones = phonesOf(profiles);
        byte[] bytes = workbook(orgClass, year, students, profiles, phones, columns, user,
                sensitive ? dto.getApprovalId() : null);

        SysExportTask task = new SysExportTask();
        task.setTaskType("CLASS_EXCEL");
        task.setStudentId(0L);
        task.setAcademicYear(year);
        task.setStatus("SUCCESS");
        task.setOperatorId(user.userId());
        task.setExpireAt(LocalDateTime.now().plusHours(24));
        task.setFileName("class-" + orgClass.getClassCode() + ".xlsx");
        taskMapper.insert(task);
        try {
            Files.createDirectories(exportDir);
            Path file = exportDir.resolve("class-" + task.getId() + ".xlsx");
            Files.write(file, bytes);
            task.setFilePath(file.toAbsolutePath().toString());
            taskMapper.updateById(task);
        } catch (Exception ex) {
            task.setStatus("FAILED");
            task.setErrorMsg("文件写入失败：" + ex.getMessage());
            taskMapper.updateById(task);
            throw new BusinessException(ResultCode.STORAGE_UNAVAILABLE, "文件保存失败");
        }
        auditLogService.record(AuditLogService.BIZ_EXPORT, AuditLogService.TARGET_EXPORT, task.getId(),
                null, null, null, AuditLogService.ACTION_EXPORT,
                "导出班级 Excel：" + orgClass.getClassCode() + "，敏感列=" + sensitive);
        return toTaskVO(task);
    }

    @Override
    public SensitiveExportApprovalVO apply(SensitiveExportApplyDTO dto) {
        CurrentUser user = requireCounselor();
        OrgClass orgClass = requireClass(dto.getClassCode(), user);
        long open = approvalMapper.selectCount(Wrappers.<SysApproval>lambdaQuery()
                .eq(SysApproval::getApprovalType, TYPE)
                .eq(SysApproval::getApplicantId, user.userId())
                .eq(SysApproval::getStatus, PENDING));
        if (open > 0) {
            throw new BusinessException(ResultCode.STATE_CONFLICT, "已有待审批的敏感导出申请");
        }
        SysApproval approval = new SysApproval();
        approval.setApprovalType(TYPE);
        approval.setApplicantId(user.userId());
        approval.setTargetJson("{\"classCode\":\"" + orgClass.getClassCode() + "\"}");
        approval.setReason(dto.getReason().trim());
        approval.setStatus(PENDING);
        approvalMapper.insert(approval);
        audit(approval, AuditLogService.ACTION_APPLY, "申请敏感列导出：" + orgClass.getClassCode());
        return toVO(approval, Map.of());
    }

    @Override
    public List<SensitiveExportApprovalVO> approvals(String status) {
        CurrentUser user = UserContext.get();
        if (user == null || (user.role() != UserRoleEnum.COU && user.role() != UserRoleEnum.ADMIN)) {
            throw new BusinessException(ResultCode.PERMISSION_DENIED, "无权查看敏感导出审批");
        }
        List<SysApproval> rows = approvalMapper.selectList(Wrappers.<SysApproval>lambdaQuery()
                .eq(SysApproval::getApprovalType, TYPE)
                .eq(user.role() == UserRoleEnum.COU, SysApproval::getApplicantId, user.userId())
                .eq(!TextUtils.isBlank(status), SysApproval::getStatus, status == null ? null : status.toUpperCase())
                .orderByDesc(SysApproval::getId)
                .last("limit 100"));
        return toVOs(rows);
    }

    @Override
    @Transactional
    public SensitiveExportApprovalVO approve(Long id, String remark) {
        CurrentUser approver = requireApprover();
        SysApproval approval = requirePending(id);
        if (approval.getApplicantId().equals(approver.userId())) {
            throw new BusinessException(ResultCode.DATA_SCOPE_DENIED, "不能审批自己的申请");
        }
        approval.setStatus(APPROVED);
        approval.setApproverId(approver.userId());
        approval.setApproverRemark(TextUtils.isBlank(remark) ? null : remark.trim());
        approval.setApprovedAt(LocalDateTime.now());
        approval.setExpireAt(LocalDateTime.now().plusHours(4));
        approvalMapper.updateById(approval);
        audit(approval, AuditLogService.ACTION_APPROVE, "通过敏感列导出，4 小时内有效");
        return toVO(approval, Map.of());
    }

    @Override
    @Transactional
    public SensitiveExportApprovalVO reject(Long id, String remark) {
        CurrentUser approver = requireApprover();
        if (TextUtils.isBlank(remark)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "拒绝时必须填写审批意见");
        }
        SysApproval approval = requirePending(id);
        if (approval.getApplicantId().equals(approver.userId())) {
            throw new BusinessException(ResultCode.DATA_SCOPE_DENIED, "不能审批自己的申请");
        }
        approval.setStatus(REJECTED);
        approval.setApproverId(approver.userId());
        approval.setApproverRemark(remark.trim());
        approval.setApprovedAt(LocalDateTime.now());
        approvalMapper.updateById(approval);
        audit(approval, AuditLogService.ACTION_REJECT, "拒绝敏感列导出：" + remark.trim());
        return toVO(approval, Map.of());
    }

    // ------------------------------------------------------------------

    private byte[] workbook(OrgClass orgClass, String year, List<StuStudent> students,
                            Map<Long, Map<String, ArcProfile>> profiles, Map<Long, String> phones,
                            List<Column> columns, CurrentUser user, Long approvalId) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("班级汇总");
            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);
            Row header = sheet.createRow(0);
            for (int i = 0; i < columns.size(); i++) {
                header.createCell(i).setCellValue(columns.get(i).label);
                header.getCell(i).setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 18 * 256);
            }
            int rowIndex = 1;
            for (StuStudent student : students) {
                Row row = sheet.createRow(rowIndex++);
                Map<String, ArcProfile> sectionMap = profiles.getOrDefault(student.getId(), Map.of());
                for (int i = 0; i < columns.size(); i++) {
                    row.createCell(i).setCellValue(value(columns.get(i).key, student, orgClass,
                            sectionMap, phones.get(student.getId()), approvalId != null));
                }
            }
            int footer = rowIndex + 1;
            sheet.createRow(footer).createCell(0).setCellValue(
                    "仅供内部使用；导出人：" + displayName(user.userId()) + "；导出时间：" + LocalDateTime.now().format(TIME)
                            + (approvalId == null ? "" : "；敏感列审批单 #" + approvalId));
            sheet.createRow(footer + 1).createCell(0).setCellValue("班级：" + orgClass.getClassName() + "；学年：" + year);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "班级 Excel 生成失败");
        }
    }

    private String value(String key, StuStudent student, OrgClass orgClass,
                         Map<String, ArcProfile> profiles, String phone, boolean fullSensitive) {
        return switch (key) {
            case "studentNo" -> student.getStudentNo();
            case "name" -> student.getName();
            case "className" -> orgClass.getClassName();
            case "gender" -> student.getGender();
            case "nation" -> student.getNation();
            case "phone" -> fullSensitive ? nullSafe(phone) : maskPhone(phone);
            case "idCard" -> fullSensitive
                    ? nullSafe(IdCardCrypto.plain(student.getIdCard(), student.getIdCardCipher()))
                    : maskIdCard(IdCardCrypto.plain(student.getIdCard(), student.getIdCardCipher()));
            case "completeness" -> averageCompleteness(profiles);
            default -> sectionStatus(profiles, key);
        };
    }

    private String averageCompleteness(Map<String, ArcProfile> profiles) {
        double sum = 0;
        int count = 0;
        for (ArcProfile profile : profiles.values()) {
            if (profile.getCompleteness() != null) {
                sum += profile.getCompleteness().doubleValue();
                count++;
            }
        }
        return count == 0 ? "0" : String.format("%.1f", sum / count);
    }

    private String sectionStatus(Map<String, ArcProfile> profiles, String key) {
        ArcProfile profile = profiles.get(key.toUpperCase());
        if (profile == null) {
            return "未建档";
        }
        try {
            return ProfileStatusEnum.valueOf(profile.getStatus()).getLabel();
        } catch (Exception ex) {
            return profile.getStatus();
        }
    }

    private Map<Long, Map<String, ArcProfile>> profilesOf(List<StuStudent> students, String year) {
        if (students.isEmpty()) {
            return Map.of();
        }
        Set<Long> ids = students.stream().map(StuStudent::getId).collect(Collectors.toSet());
        return profileMapper.selectList(Wrappers.<ArcProfile>lambdaQuery()
                        .in(ArcProfile::getStudentId, ids)
                        .eq(ArcProfile::getAcademicYear, year))
                .stream().collect(Collectors.groupingBy(ArcProfile::getStudentId,
                        Collectors.toMap(ArcProfile::getSectionCode, profile -> profile, (a, b) -> a)));
    }

    private Map<Long, String> phonesOf(Map<Long, Map<String, ArcProfile>> profiles) {
        List<Long> profileIds = profiles.values().stream()
                .map(map -> map.get(SectionCodeEnum.BASIC.name()))
                .filter(java.util.Objects::nonNull)
                .map(ArcProfile::getId).toList();
        if (profileIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> byProfile = itemMapper.selectList(Wrappers.<ArcProfileItem>lambdaQuery()
                        .in(ArcProfileItem::getProfileId, profileIds)
                        .eq(ArcProfileItem::getFieldCode, "basic.phone"))
                .stream().collect(Collectors.toMap(ArcProfileItem::getProfileId,
                        item -> item.getValueText() == null ? "" : item.getValueText(), (a, b) -> a));
        Map<Long, String> result = new LinkedHashMap<>();
        profiles.forEach((studentId, sections) -> {
            ArcProfile basic = sections.get(SectionCodeEnum.BASIC.name());
            if (basic != null && byProfile.containsKey(basic.getId())) {
                result.put(studentId, byProfile.get(basic.getId()));
            }
        });
        return result;
    }

    private List<Column> resolveColumns(List<String> requested) {
        List<String> keys = requested == null || requested.isEmpty()
                ? COLUMNS.keySet().stream().filter(key -> !COLUMNS.get(key).sensitive).toList()
                : requested;
        List<Column> result = new ArrayList<>();
        for (String key : keys) {
            Column column = COLUMNS.get(key);
            if (column == null) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "不支持的导出列：" + key);
            }
            result.add(column);
        }
        return result;
    }

    private void requireSensitiveApproval(Long approvalId, String classCode, CurrentUser user) {
        SysApproval approval = approvalId == null ? null : approvalMapper.selectById(approvalId);
        if (approval == null || !TYPE.equals(approval.getApprovalType())
                || !APPROVED.equals(approval.getStatus())
                || approval.getExpireAt() == null || !approval.getExpireAt().isAfter(LocalDateTime.now())
                || !user.userId().equals(approval.getApplicantId())
                || !classCode.equals(classOf(approval))) {
            throw new BusinessException(ResultCode.SENSITIVE_EXPORT_DENIED);
        }
    }

    private String classOf(SysApproval approval) {
        try {
            JsonNode node = objectMapper.readTree(approval.getTargetJson());
            return node.path("classCode").asText(null);
        } catch (Exception ex) {
            return null;
        }
    }

    private OrgClass requireClass(String classCode, CurrentUser user) {
        OrgClass orgClass = orgClassMapper.selectOne(Wrappers.<OrgClass>lambdaQuery()
                .eq(OrgClass::getClassCode, classCode)
                .eq(OrgClass::getStatus, 1)
                .last("limit 1"));
        if (orgClass == null || !user.userId().equals(orgClass.getCounselorUserId())) {
            throw new BusinessException(ResultCode.DATA_SCOPE_DENIED, "只能导出本人带班班级");
        }
        return orgClass;
    }

    private CurrentUser requireCounselor() {
        CurrentUser user = UserContext.get();
        if (user == null || user.role() != UserRoleEnum.COU) {
            throw new BusinessException(ResultCode.PERMISSION_DENIED, "仅辅导员可导出班级 Excel");
        }
        return user;
    }

    private CurrentUser requireApprover() {
        CurrentUser user = UserContext.get();
        if (user == null || user.role() != UserRoleEnum.ADMIN) {
            throw new BusinessException(ResultCode.PERMISSION_DENIED, "仅管理员可审批敏感导出");
        }
        return user;
    }

    private SysApproval requirePending(Long id) {
        SysApproval approval = id == null ? null : approvalMapper.selectById(id);
        if (approval == null || !TYPE.equals(approval.getApprovalType())) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "审批单不存在：" + id);
        }
        if (!PENDING.equals(approval.getStatus())) {
            throw new BusinessException(ResultCode.STATE_CONFLICT, "申请已处理");
        }
        return approval;
    }

    private List<SensitiveExportApprovalVO> toVOs(List<SysApproval> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<Long> ids = rows.stream().flatMap(row -> java.util.stream.Stream.of(row.getApplicantId(), row.getApproverId()))
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> names = ids.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(ids).stream().collect(Collectors.toMap(
                SysUser::getId, user -> user.getDisplayName() == null ? user.getUsername() : user.getDisplayName()));
        return rows.stream().map(row -> toVO(row, names)).toList();
    }

    private SensitiveExportApprovalVO toVO(SysApproval approval, Map<Long, String> names) {
        SensitiveExportApprovalVO vo = new SensitiveExportApprovalVO();
        vo.setId(approval.getId());
        vo.setApplicantId(approval.getApplicantId());
        vo.setApplicantName(names.get(approval.getApplicantId()));
        vo.setClassCode(classOf(approval));
        vo.setReason(approval.getReason());
        vo.setStatus(approval.getStatus());
        vo.setApproverId(approval.getApproverId());
        vo.setApproverName(approval.getApproverId() == null ? null : names.get(approval.getApproverId()));
        vo.setApproverRemark(approval.getApproverRemark());
        vo.setApprovedAt(approval.getApprovedAt());
        vo.setExpireAt(approval.getExpireAt());
        vo.setCreatedAt(approval.getCreatedAt());
        vo.setActive(APPROVED.equals(approval.getStatus()) && approval.getExpireAt() != null
                && approval.getExpireAt().isAfter(LocalDateTime.now()));
        return vo;
    }

    private String displayName(Long userId) {
        SysUser user = userMapper.selectById(userId);
        return user == null ? String.valueOf(userId) : (user.getDisplayName() == null ? user.getUsername() : user.getDisplayName());
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private String maskPhone(String phone) {
        if (TextUtils.isBlank(phone) || phone.length() < 7) {
            return phone == null ? "" : phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private String maskIdCard(String idCard) {
        if (TextUtils.isBlank(idCard) || idCard.length() < 10) {
            return idCard == null ? "" : idCard;
        }
        return idCard.substring(0, 6) + "********" + idCard.substring(idCard.length() - 4);
    }

    private void audit(SysApproval approval, String action, String detail) {
        auditLogService.record(AuditLogService.BIZ_EXPORT, AuditLogService.TARGET_APPROVAL,
                approval.getId(), null, null, null, action, detail);
    }

    private ExportTaskVO toTaskVO(SysExportTask task) {
        ExportTaskVO vo = new ExportTaskVO();
        vo.setId(task.getId());
        vo.setStudentId(task.getStudentId());
        vo.setAcademicYear(task.getAcademicYear());
        vo.setStatus(task.getStatus());
        vo.setFileName(task.getFileName());
        vo.setExpireAt(task.getExpireAt());
        vo.setCreatedAt(task.getCreatedAt());
        return vo;
    }

    private record Column(String key, String label, boolean sensitive) {
    }
}
