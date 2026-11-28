package com.college.growth.export.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.context.UserContext;
import com.college.growth.common.enums.ProfileStatusEnum;
import com.college.growth.common.enums.SectionCodeEnum;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.field.entity.ArcFieldDef;
import com.college.growth.common.field.mapper.ArcFieldDefMapper;
import com.college.growth.common.log.service.AuditLogService;
import com.college.growth.common.org.entity.OrgClass;
import com.college.growth.common.org.mapper.OrgClassMapper;
import com.college.growth.common.org.service.StudentClassService;
import com.college.growth.common.profile.entity.ArcProfile;
import com.college.growth.common.profile.entity.ArcProfileItem;
import com.college.growth.common.profile.mapper.ArcProfileItemMapper;
import com.college.growth.common.profile.mapper.ArcProfileMapper;
import com.college.growth.common.result.ResultCode;
import com.college.growth.common.student.entity.StuStudent;
import com.college.growth.common.student.mapper.StuStudentMapper;
import com.college.growth.common.util.AcademicYearUtils;
import com.college.growth.common.util.TextUtils;
import com.college.growth.academic.record.entity.ArcCertificate;
import com.college.growth.academic.record.entity.ArcEvent;
import com.college.growth.academic.record.mapper.ArcCertificateMapper;
import com.college.growth.academic.record.mapper.ArcEventMapper;
import com.college.growth.auth.entity.SysUser;
import com.college.growth.auth.mapper.SysUserMapper;
import com.college.growth.export.entity.SysExportTask;
import com.college.growth.export.mapper.SysExportTaskMapper;
import com.college.growth.export.service.ProfileExportData;
import com.college.growth.export.service.ProfileExportService;
import com.college.growth.export.service.ProfilePdfRenderer;
import com.college.growth.export.vo.ExportTaskVO;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 单人档案 PDF 导出实现（F-12）。
 *
 * <p>同步生成：任务创建后立即渲染落盘，状态置 SUCCESS；下载有效期为 24 小时。
 * 学生导出要求六板块全部 APPROVED/LOCKED（AC-042），辅导员可在任意状态导出。</p>
 *
 * <p>渲染内容不含身份证号、导师内部备注、学生不可见的重大事件备忘，
 * 避免在导出文件中保留内部/高敏字段（7.5 安全禁止项）。</p>
 */
@Service
public class ProfileExportServiceImpl implements ProfileExportService {

    private static final Logger log = LoggerFactory.getLogger(ProfileExportServiceImpl.class);

    private static final String TASK_PROFILE_PDF = "PROFILE_PDF";

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final StuStudentMapper studentMapper;

    private final OrgClassMapper classMapper;

    private final ArcProfileMapper profileMapper;

    private final ArcProfileItemMapper itemMapper;

    private final ArcFieldDefMapper fieldDefMapper;

    private final ArcCertificateMapper certificateMapper;

    private final ArcEventMapper eventMapper;

    private final SysUserMapper userMapper;

    private final SysExportTaskMapper taskMapper;

    private final StudentClassService studentClassService;

    private final AuditLogService auditLogService;

    private final ProfilePdfRenderer renderer;

    private final Path exportDir;

    public ProfileExportServiceImpl(StuStudentMapper studentMapper, OrgClassMapper classMapper,
                                    ArcProfileMapper profileMapper, ArcProfileItemMapper itemMapper,
                                    ArcFieldDefMapper fieldDefMapper,
                                    ArcCertificateMapper certificateMapper, ArcEventMapper eventMapper,
                                    SysUserMapper userMapper, SysExportTaskMapper taskMapper,
                                    StudentClassService studentClassService, AuditLogService auditLogService,
                                    ProfilePdfRenderer renderer,
                                    @Value("${growth.export.dir:}") String exportDir) {
        this.studentMapper = studentMapper;
        this.classMapper = classMapper;
        this.profileMapper = profileMapper;
        this.itemMapper = itemMapper;
        this.fieldDefMapper = fieldDefMapper;
        this.certificateMapper = certificateMapper;
        this.eventMapper = eventMapper;
        this.userMapper = userMapper;
        this.taskMapper = taskMapper;
        this.studentClassService = studentClassService;
        this.auditLogService = auditLogService;
        this.renderer = renderer;
        String dir = TextUtils.isBlank(exportDir)
                ? System.getProperty("java.io.tmpdir") + File.separator + "student-growth-export"
                : exportDir;
        this.exportDir = Paths.get(dir);
    }

    @Override
    public ExportTaskVO create(Long studentId, String academicYear) {
        CurrentUser user = UserContext.get();
        String year = AcademicYearUtils.resolve(academicYear);
        studentClassService.requireStudentScope(studentId, year, user);
        if (user.isStudent()) {
            requireAllApproved(studentId, year);
        }
        StuStudent student = requireStudent(studentId);
        SysUser exporter = userMapper.selectById(user.userId());
        ProfileExportData data = buildData(student, year, exporter);
        byte[] bytes;
        try {
            bytes = renderer.render(data);
        } catch (Exception ex) {
            SysExportTask failed = failedTask(studentId, year, user.userId(), ex);
            log.warn("PDF 导出失败 studentId={} taskId={}", studentId, failed.getId(), ex);
            throw new BusinessException(ResultCode.PDF_RENDER_TIMEOUT, "档案生成失败，请稍后重试");
        }

        SysExportTask task = new SysExportTask();
        task.setTaskType(TASK_PROFILE_PDF);
        task.setStudentId(studentId);
        task.setAcademicYear(year);
        task.setStatus("SUCCESS");
        task.setOperatorId(user.userId());
        task.setExpireAt(LocalDateTime.now().plusHours(24));
        task.setFileName("student-profile-" + student.getStudentNo() + ".pdf");
        taskMapper.insert(task);
        try {
            Files.createDirectories(exportDir);
            Path file = exportDir.resolve("profile-" + task.getId() + ".pdf");
            Files.write(file, bytes);
            task.setFilePath(file.toAbsolutePath().toString());
            taskMapper.updateById(task);
        } catch (Exception ex) {
            task.setStatus("FAILED");
            task.setErrorMsg("文件写入失败：" + ex.getMessage());
            taskMapper.updateById(task);
            throw new BusinessException(ResultCode.STORAGE_UNAVAILABLE, "文件保存失败，请稍后重试");
        }
        audit(task, AuditLogService.ACTION_EXPORT, "导出单人档案：" + student.getStudentNo());
        log.info("PDF 导出成功 studentId={} taskId={} operator={}", studentId, task.getId(), user.userId());
        return toVO(task);
    }

    @Override
    public ExportTaskVO status(Long taskId) {
        return toVO(requireOwnTask(taskId));
    }

    @Override
    public byte[] download(Long taskId) {
        SysExportTask task = requireOwnTask(taskId);
        if (task.getExpireAt() == null || task.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ResultCode.EXPORT_EXPIRED);
        }
        if (!"SUCCESS".equals(task.getStatus()) || TextUtils.isBlank(task.getFilePath())) {
            throw new BusinessException(ResultCode.STATE_CONFLICT, "导出任务未成功，无法下载");
        }
        try {
            byte[] bytes = Files.readAllBytes(Paths.get(task.getFilePath()));
            audit(task, AuditLogService.ACTION_DOWNLOAD, "下载单人档案 PDF");
            return bytes;
        } catch (Exception ex) {
            throw new BusinessException(ResultCode.STORAGE_UNAVAILABLE, "导出文件不存在或不可读");
        }
    }

    // ------------------------------------------------------------------
    // 构建渲染数据
    // ------------------------------------------------------------------

    private ProfileExportData buildData(StuStudent student, String year, SysUser exporter) {
        ProfileExportData data = new ProfileExportData();
        data.setStudentName(student.getName());
        data.setStudentNo(student.getStudentNo());
        data.setAcademicYear(year);
        data.setExporterName(exporter == null ? "未知" : exporter.getDisplayName());
        data.setExportedAt(LocalDateTime.now().format(TIME));
        OrgClass orgClass = classMapper.selectOne(Wrappers.<OrgClass>lambdaQuery()
                .eq(OrgClass::getClassCode, student.getClassCode()).last("limit 1"));
        data.setClassName(orgClass == null ? student.getClassCode() : orgClass.getClassName());
        data.setMajorName(orgClass == null ? null : orgClass.getMajorName());

        CurrentUser user = UserContext.get();
        List<ProfileExportData.Section> sections = new ArrayList<>();
        for (SectionCodeEnum section : SectionCodeEnum.values()) {
            ArcProfile profile = profileMapper.selectOne(Wrappers.<ArcProfile>lambdaQuery()
                    .eq(ArcProfile::getStudentId, student.getId())
                    .eq(ArcProfile::getSectionCode, section.name())
                    .eq(ArcProfile::getAcademicYear, year)
                    .last("limit 1"));
            ProfileExportData.Section view = new ProfileExportData.Section();
            view.setName(section.getLabel());
            if (profile == null) {
                view.setStatus("未建档");
                view.setCompleteness("0");
                view.setLines(List.of());
                sections.add(view);
                continue;
            }
            view.setStatus(labelOf(profile.getStatus()));
            view.setCompleteness(profile.getCompleteness() == null ? "0"
                    : profile.getCompleteness().stripTrailingZeros().toPlainString());
            view.setLines(fieldLines(profile, section, user));
            sections.add(view);
        }
        data.setSections(sections);

        data.setCertificates(certificateMapper.selectList(Wrappers.<ArcCertificate>lambdaQuery()
                        .eq(ArcCertificate::getStudentId, student.getId())
                        .eq(ArcCertificate::getVerifyStatus, "PASSED")
                        .orderByDesc(ArcCertificate::getId))
                .stream().map(item -> item.getCertName() + "（" + item.getCertType() + "）")
                .collect(Collectors.toList()));
        data.setEvents(eventMapper.selectList(Wrappers.<ArcEvent>lambdaQuery()
                        .eq(ArcEvent::getStudentId, student.getId())
                        .eq(ArcEvent::getVerifyStatus, "PASSED")
                        .orderByDesc(ArcEvent::getId))
                .stream().map(item -> item.getTitle() + "（" + item.getLevel() + "）")
                .collect(Collectors.toList()));
        return data;
    }

    private List<ProfileExportData.Line> fieldLines(ArcProfile profile, SectionCodeEnum section,
                                                    CurrentUser user) {
        List<ArcFieldDef> defs = fieldDefMapper.selectList(Wrappers.<ArcFieldDef>lambdaQuery()
                .eq(ArcFieldDef::getSectionCode, section.name())
                .eq(ArcFieldDef::getStatus, 1)
                .orderByAsc(ArcFieldDef::getSortNo));
        List<ArcProfileItem> items = itemMapper.selectList(Wrappers.<ArcProfileItem>lambdaQuery()
                .eq(ArcProfileItem::getProfileId, profile.getId()));
        Map<String, String> valueByCode = items.stream().collect(Collectors.toMap(
                ArcProfileItem::getFieldCode,
                item -> TextUtils.isBlank(item.getValueText())
                        ? (item.getValueJson() == null ? "" : item.getValueJson())
                        : item.getValueText(),
                (left, right) -> left));
        List<ProfileExportData.Line> lines = new ArrayList<>();
        for (ArcFieldDef def : defs) {
            String code = def.getFieldCode();
            if ("basic.idCard".equals(code) || "mentor.internalRemark".equals(code)) {
                continue;
            }
            if (user.isStudent() && "life.majorEvent".equals(code)) {
                continue;
            }
            String value = valueByCode.get(code);
            if (TextUtils.isBlank(value)) {
                continue;
            }
            lines.add(new ProfileExportData.Line(def.getFieldName(), value));
        }
        return lines;
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    private void requireAllApproved(Long studentId, String year) {
        List<ArcProfile> profiles = profileMapper.selectList(Wrappers.<ArcProfile>lambdaQuery()
                .eq(ArcProfile::getStudentId, studentId)
                .eq(ArcProfile::getAcademicYear, year));
        Map<String, String> statusBySection = profiles.stream().collect(Collectors.toMap(
                ArcProfile::getSectionCode, ArcProfile::getStatus, (a, b) -> a));
        for (SectionCodeEnum section : SectionCodeEnum.values()) {
            String status = statusBySection.get(section.name());
            if (!"APPROVED".equals(status) && !"LOCKED".equals(status)) {
                throw new BusinessException(ResultCode.EXPORT_NOT_READY,
                        "「" + section.getLabel() + "」尚未通过审核，暂不能导出");
            }
        }
    }

    private StuStudent requireStudent(Long studentId) {
        StuStudent student = studentId == null ? null : studentMapper.selectById(studentId);
        if (student == null) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "学生不存在：" + studentId);
        }
        return student;
    }

    private SysExportTask requireOwnTask(Long taskId) {
        SysExportTask task = taskId == null ? null : taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "导出任务不存在：" + taskId);
        }
        if (!task.getOperatorId().equals(UserContext.get().userId())) {
            throw new BusinessException(ResultCode.DATA_SCOPE_DENIED, "只能访问本人的导出任务");
        }
        return task;
    }

    private SysExportTask failedTask(Long studentId, String year, Long operatorId, Exception ex) {
        SysExportTask task = new SysExportTask();
        task.setTaskType(TASK_PROFILE_PDF);
        task.setStudentId(studentId);
        task.setAcademicYear(year);
        task.setStatus("FAILED");
        task.setOperatorId(operatorId);
        task.setExpireAt(LocalDateTime.now().plusHours(24));
        task.setErrorMsg(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        taskMapper.insert(task);
        return task;
    }

    private String labelOf(String status) {
        try {
            return ProfileStatusEnum.valueOf(status).getLabel();
        } catch (Exception ex) {
            return status;
        }
    }

    private void audit(SysExportTask task, String action, String detail) {
        auditLogService.record(AuditLogService.BIZ_EXPORT, AuditLogService.TARGET_EXPORT,
                task.getId(), task.getStudentId(), null, null, action, detail);
    }

    private ExportTaskVO toVO(SysExportTask task) {
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
}
