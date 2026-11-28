package com.college.growth.academic.record.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.college.growth.academic.record.entity.ArcCertificate;
import com.college.growth.academic.record.entity.ArcRank;
import com.college.growth.academic.record.mapper.ArcCertificateMapper;
import com.college.growth.academic.record.mapper.ArcRankMapper;
import com.college.growth.academic.record.service.AcademicImportParser;
import com.college.growth.academic.record.service.AcademicImportParser.CertRow;
import com.college.growth.academic.record.service.AcademicImportParser.ParseResult;
import com.college.growth.academic.record.service.AcademicImportParser.RankRow;
import com.college.growth.academic.record.service.AcademicImportService;
import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.context.UserContext;
import com.college.growth.common.enums.UserRoleEnum;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.log.service.AuditLogService;
import com.college.growth.common.org.service.StudentClassService;
import com.college.growth.common.result.ResultCode;
import com.college.growth.common.student.entity.StuStudent;
import com.college.growth.common.student.mapper.StuStudentMapper;
import com.college.growth.common.util.AcademicYearUtils;
import com.college.growth.common.util.TextUtils;
import com.college.growth.importer.entity.SysImportTask;
import com.college.growth.importer.mapper.SysImportTaskMapper;
import com.college.growth.importer.vo.ImportPreviewVO;
import com.college.growth.importer.vo.ImportResultVO;
import com.college.growth.importer.vo.ImportRowVO;
import com.college.growth.importer.vo.ImportTaskVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * 成绩/证书导入实现（F-10）。
 *
 * <p>成绩按「学生 + 学期」覆盖（旧值改前留快照由 arc_change_log 记录）；
 * 证书一律以 PENDING 入库，等待 COU 认定。COU 只能导入本班，ADMIN 全部班级。</p>
 */
@Service
public class AcademicImportServiceImpl implements AcademicImportService {

    private static final Logger log = LoggerFactory.getLogger(AcademicImportServiceImpl.class);

    private static final String TASK_RANK = "RANK";

    private static final String TASK_CERT = "CERT";

    private static final Set<String> CERT_TYPES = Set.of("外语", "计算机", "职业资格", "技能", "其他");

    private static final String PENDING = "PENDING";

    private static final String PASSED = "PASSED";

    private final AcademicImportParser parser;

    private final StuStudentMapper studentMapper;

    private final ArcRankMapper rankMapper;

    private final ArcCertificateMapper certificateMapper;

    private final SysImportTaskMapper taskMapper;

    private final StudentClassService studentClassService;

    private final AuditLogService auditLogService;

    private final ObjectMapper objectMapper;

    private final TransactionTemplate transactionTemplate;

    public AcademicImportServiceImpl(AcademicImportParser parser, StuStudentMapper studentMapper,
                                     ArcRankMapper rankMapper, ArcCertificateMapper certificateMapper,
                                     SysImportTaskMapper taskMapper, StudentClassService studentClassService,
                                     AuditLogService auditLogService, ObjectMapper objectMapper,
                                     PlatformTransactionManager transactionManager) {
        this.parser = parser;
        this.studentMapper = studentMapper;
        this.rankMapper = rankMapper;
        this.certificateMapper = certificateMapper;
        this.taskMapper = taskMapper;
        this.studentClassService = studentClassService;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public byte[] rankTemplate() {
        return template("成绩导入", AcademicImportParser.RANK_HEADERS,
                new String[]{"1. 仅支持 .xlsx；表头不可调整。",
                        "2. 学号必须已存在且在本班；学期格式 2025-2026-1。",
                        "3. 排名为正整数（可空），GPA 0-5 且最多两位小数。",
                        "4. 同一学生同一学期重复导入时覆盖原值。"});
    }

    @Override
    public byte[] certificateTemplate() {
        return template("证书导入", AcademicImportParser.CERT_HEADERS,
                new String[]{"1. 仅支持 .xlsx；表头不可调整。",
                        "2. 学号必须已存在且在本班；证书类型：外语/计算机/职业资格/技能/其他。",
                        "3. 取得日期格式 yyyy-MM-dd，不晚于今天、不早于入学日期。",
                        "4. 导入后状态为待认定（PENDING），由辅导员认定。"});
    }

    @Override
    public ImportPreviewVO previewRanks(MultipartFile file) {
        ParseResult<RankRow> parsed = parser.parseRanks(file);
        List<RowCheck<RankRow>> checks = validateRanks(parsed.getRows());
        return preview(file, parsed.getHeaders(), checks);
    }

    @Override
    public ImportResultVO executeRanks(MultipartFile file) {
        ParseResult<RankRow> parsed = parser.parseRanks(file);
        List<RowCheck<RankRow>> checks = validateRanks(parsed.getRows());
        return execute(file, TASK_RANK, checks, this::upsertRank);
    }

    @Override
    public ImportPreviewVO previewCertificates(MultipartFile file) {
        ParseResult<CertRow> parsed = parser.parseCertificates(file);
        List<RowCheck<CertRow>> checks = validateCertificates(parsed.getRows());
        return preview(file, parsed.getHeaders(), checks);
    }

    @Override
    public ImportResultVO executeCertificates(MultipartFile file) {
        ParseResult<CertRow> parsed = parser.parseCertificates(file);
        List<RowCheck<CertRow>> checks = validateCertificates(parsed.getRows());
        return execute(file, TASK_CERT, checks, this::insertCertificate);
    }

    @Override
    public List<ImportTaskVO> tasks() {
        return taskMapper.selectList(Wrappers.<SysImportTask>lambdaQuery()
                        .in(SysImportTask::getTaskType, TASK_RANK, TASK_CERT)
                        .orderByDesc(SysImportTask::getId)
                        .last("limit 20"))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public byte[] receipt(Long taskId) {
        SysImportTask task = taskId == null ? null : taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "导入任务不存在：" + taskId);
        }
        List<ImportRowVO> errors = new ArrayList<>();
        if (!TextUtils.isBlank(task.getErrorJson())) {
            try {
                errors = objectMapper.readValue(task.getErrorJson(), new TypeReference<List<ImportRowVO>>() {
                });
            } catch (Exception ex) {
                log.warn("学业导入回执解析失败 taskId={}", taskId, ex);
            }
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("错误回执");
            Row header = sheet.createRow(0);
            String[] columns = {"行号", "学号/证书", "错误原因"};
            for (int i = 0; i < columns.length; i++) {
                header.createCell(i).setCellValue(columns[i]);
                sheet.setColumnWidth(i, (i == 2 ? 80 : 20) * 256);
            }
            int index = 1;
            for (ImportRowVO error : errors) {
                Row row = sheet.createRow(index++);
                row.createCell(0).setCellValue(error.getRowNo());
                row.createCell(1).setCellValue(error.getStudentNo() == null ? "" : error.getStudentNo());
                row.createCell(2).setCellValue(String.join("；", error.getErrors()));
            }
            if (errors.isEmpty()) {
                sheet.createRow(1).createCell(0).setCellValue("无错误");
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "回执生成失败");
        }
    }

    // ------------------------------------------------------------------
    // 校验
    // ------------------------------------------------------------------

    private List<RowCheck<RankRow>> validateRanks(List<RankRow> rows) {
        Map<String, StuStudent> students = students(rows.stream().map(RankRow::getStudentNo).toList());
        Set<String> seen = new HashSet<>();
        List<RowCheck<RankRow>> checks = new ArrayList<>();
        for (RankRow row : rows) {
            RowCheck<RankRow> check = new RowCheck<>(row);
            StuStudent student = students.get(row.getStudentNo());
            if (TextUtils.isBlank(row.getStudentNo()) || student == null) {
                check.errors.add("学号不存在或为空");
            } else {
                requireScope(student, check.errors);
            }
            if (TextUtils.isBlank(row.getTerm()) || !row.getTerm().matches("\\d{4}-\\d{4}-[12]")) {
                check.errors.add("学期格式应为 2025-2026-1");
            }
            Integer classRank = positiveInt(row.getClassRank(), "班级排名", check.errors);
            Integer majorRank = positiveInt(row.getMajorRank(), "专业排名", check.errors);
            BigDecimal gpa = gpa(row.getGpa(), check.errors);
            if (classRank == null && majorRank == null && gpa == null
                    && check.errors.isEmpty()) {
                check.errors.add("班级排名、专业排名、GPA 至少填写一项");
            }
            if (student != null && !TextUtils.isBlank(row.getTerm())
                    && !seen.add(student.getId() + "|" + row.getTerm())) {
                check.errors.add("同一学生同一学期在文件中重复");
            }
            checks.add(check);
        }
        return checks;
    }

    private List<RowCheck<CertRow>> validateCertificates(List<CertRow> rows) {
        Map<String, StuStudent> students = students(rows.stream().map(CertRow::getStudentNo).toList());
        Set<String> seen = new HashSet<>();
        List<RowCheck<CertRow>> checks = new ArrayList<>();
        for (CertRow row : rows) {
            RowCheck<CertRow> check = new RowCheck<>(row);
            StuStudent student = students.get(row.getStudentNo());
            if (TextUtils.isBlank(row.getStudentNo()) || student == null) {
                check.errors.add("学号不存在或为空");
            } else {
                requireScope(student, check.errors);
            }
            if (TextUtils.isBlank(row.getCertType()) || !CERT_TYPES.contains(row.getCertType().trim())) {
                check.errors.add("证书类型只能是：外语/计算机/职业资格/技能/其他");
            }
            String name = row.getCertName() == null ? "" : row.getCertName().trim();
            if (name.length() < 2 || name.length() > 128) {
                check.errors.add("证书名称 2-128 字");
            }
            String score = row.getScore() == null ? "" : row.getScore().trim();
            if (score.matches("\\d+") && (score.length() > 3 || Integer.parseInt(score) > 999)) {
                check.errors.add("成绩为纯数字时不能超过 999");
            }
            LocalDate date = date(row.getObtainDate(), check.errors);
            if (date != null) {
                if (date.isAfter(LocalDate.now())) {
                    check.errors.add("取得日期不能晚于今天");
                }
                if (student != null && student.getEnrollmentYear() != null
                        && date.isBefore(LocalDate.of(student.getEnrollmentYear(), 1, 1))) {
                    check.errors.add("取得日期不能早于入学日期");
                }
            }
            if (student != null && !TextUtils.isBlank(name)) {
                if (!seen.add(student.getId() + "|" + name)) {
                    check.errors.add("同一学生同名证书在文件中重复");
                } else {
                    long duplicate = certificateMapper.selectCount(Wrappers.<ArcCertificate>lambdaQuery()
                            .eq(ArcCertificate::getStudentId, student.getId())
                            .eq(ArcCertificate::getCertName, name)
                            .in(ArcCertificate::getVerifyStatus, PENDING, PASSED));
                    if (duplicate > 0) {
                        check.errors.add("同名证书已提交或已认定");
                    }
                }
            }
            checks.add(check);
        }
        return checks;
    }

    private Integer positiveInt(String raw, String label, List<String> errors) {
        if (TextUtils.isBlank(raw)) {
            return null;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            if (value <= 0) {
                errors.add(label + "必须为正整数");
                return null;
            }
            return value;
        } catch (NumberFormatException ex) {
            errors.add(label + "必须为正整数");
            return null;
        }
    }

    private BigDecimal gpa(String raw, List<String> errors) {
        if (TextUtils.isBlank(raw)) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(raw.trim());
            if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(new BigDecimal("5")) > 0) {
                errors.add("GPA 应在 0-5 之间");
                return null;
            }
            if (value.scale() > 2) {
                errors.add("GPA 最多两位小数");
                return null;
            }
            return value;
        } catch (NumberFormatException ex) {
            errors.add("GPA 不是数字");
            return null;
        }
    }

    private LocalDate date(String raw, List<String> errors) {
        if (TextUtils.isBlank(raw)) {
            errors.add("取得日期不能为空");
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException ex) {
            errors.add("取得日期格式应为 yyyy-MM-dd");
            return null;
        }
    }

    private void requireScope(StuStudent student, List<String> errors) {
        CurrentUser user = UserContext.get();
        if (user.role() == UserRoleEnum.COU
                && !studentClassService.isCounselorOf(student.getId(), AcademicYearUtils.current(), user.userId())) {
            errors.add("只能导入本班学生");
        }
    }

    private Map<String, StuStudent> students(List<String> studentNos) {
        Set<String> nos = studentNos.stream().filter(no -> !TextUtils.isBlank(no)).collect(Collectors.toSet());
        if (nos.isEmpty()) {
            return Map.of();
        }
        return studentMapper.selectList(Wrappers.<StuStudent>lambdaQuery()
                        .in(StuStudent::getStudentNo, nos))
                .stream().collect(Collectors.toMap(StuStudent::getStudentNo, item -> item, (a, b) -> a));
    }

    // ------------------------------------------------------------------
    // 执行
    // ------------------------------------------------------------------

    private <T> ImportPreviewVO preview(MultipartFile file, List<String> headers,
                                        List<RowCheck<T>> checks) {
        int valid = (int) checks.stream().filter(check -> check.errors.isEmpty()).count();
        ImportPreviewVO vo = new ImportPreviewVO();
        vo.setFileName(file.getOriginalFilename());
        vo.setHeaders(headers);
        vo.setHeaderValid(true);
        vo.setTotalRows(checks.size());
        vo.setValidRows(valid);
        vo.setInvalidRows(checks.size() - valid);
        vo.setRows(checks.stream().limit(20).map(this::toVO).collect(Collectors.toList()));
        return vo;
    }

    private <T> ImportResultVO execute(MultipartFile file, String taskType, List<RowCheck<T>> checks,
                                       RowWriter<T> writer) {
        List<ImportRowVO> failures = new ArrayList<>();
        int success = 0;
        for (RowCheck<T> check : checks) {
            if (!check.errors.isEmpty()) {
                failures.add(toVO(check));
                continue;
            }
            try {
                transactionTemplate.executeWithoutResult(status -> writer.write(check.row));
                success++;
            } catch (Exception ex) {
                check.errors.add("入库失败：" + rootMessage(ex));
                failures.add(toVO(check));
                log.warn("学业导入行失败 taskType={} row={} reason={}", taskType,
                        check.rowNo(), rootMessage(ex));
            }
        }
        int failed = checks.size() - success;
        SysImportTask task = new SysImportTask();
        task.setTaskType(taskType);
        task.setFileName(file.getOriginalFilename());
        task.setTotalRows(checks.size());
        task.setSuccessRows(success);
        task.setFailedRows(failed);
        task.setStatus(failed == 0 ? "SUCCESS" : success == 0 ? "FAILED" : "PARTIAL");
        task.setOperatorId(UserContext.get().userId());
        try {
            task.setErrorJson(failures.isEmpty() ? null : objectMapper.writeValueAsString(failures));
        } catch (Exception ex) {
            task.setErrorJson(null);
        }
        taskMapper.insert(task);

        ImportResultVO result = new ImportResultVO();
        result.setTaskId(task.getId());
        result.setTotalRows(checks.size());
        result.setSuccessRows(success);
        result.setFailedRows(failed);
        result.setStatus(task.getStatus());
        result.setErrors(failures.stream().limit(20).collect(Collectors.toList()));
        return result;
    }

    private void upsertRank(RankRow row) {
        StuStudent student = studentMapper.selectOne(Wrappers.<StuStudent>lambdaQuery()
                .eq(StuStudent::getStudentNo, row.getStudentNo()).last("limit 1"));
        ArcRank existing = rankMapper.selectOne(Wrappers.<ArcRank>lambdaQuery()
                .eq(ArcRank::getStudentId, student.getId())
                .eq(ArcRank::getAcademicYear, row.getTerm().trim())
                .last("limit 1"));
        ArcRank entity = existing == null ? new ArcRank() : existing;
        entity.setStudentId(student.getId());
        entity.setAcademicYear(row.getTerm().trim());
        entity.setClassRank(parseInt(row.getClassRank()));
        entity.setMajorRank(parseInt(row.getMajorRank()));
        entity.setGpa(TextUtils.isBlank(row.getGpa()) ? null : new BigDecimal(row.getGpa().trim()));
        entity.setSource("IMPORT");
        if (existing == null) {
            rankMapper.insert(entity);
        } else {
            rankMapper.updateById(entity);
        }
        auditLogService.record(AuditLogService.BIZ_ACADEMIC, AuditLogService.TARGET_STUDENT,
                student.getId(), student.getId(), "ACADEMIC", "academic.rankMatrix",
                AuditLogService.ACTION_IMPORT, "导入成绩：" + entity.getAcademicYear());
    }

    private void insertCertificate(CertRow row) {
        StuStudent student = studentMapper.selectOne(Wrappers.<StuStudent>lambdaQuery()
                .eq(StuStudent::getStudentNo, row.getStudentNo()).last("limit 1"));
        ArcCertificate entity = new ArcCertificate();
        entity.setStudentId(student.getId());
        entity.setCertType(row.getCertType().trim());
        entity.setCertName(row.getCertName().trim());
        entity.setScore(TextUtils.isBlank(row.getScore()) ? null : row.getScore().trim());
        entity.setObtainDate(LocalDate.parse(row.getObtainDate().trim()));
        entity.setVerifyStatus(PENDING);
        certificateMapper.insert(entity);
        auditLogService.record(AuditLogService.BIZ_ACADEMIC, AuditLogService.TARGET_STUDENT,
                student.getId(), student.getId(), "ACADEMIC", "academic.certificates",
                AuditLogService.ACTION_IMPORT, "导入证书：" + entity.getCertName());
    }

    private Integer parseInt(String raw) {
        return TextUtils.isBlank(raw) ? null : Integer.parseInt(raw.trim());
    }

    private byte[] template(String sheetName, List<String> headers, String[] tips) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sheetName);
            CellStyle style = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            style.setFont(font);
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                var cell = header.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(style);
                sheet.setColumnWidth(i, 20 * 256);
            }
            Sheet tipSheet = workbook.createSheet("填写说明");
            for (int i = 0; i < tips.length; i++) {
                tipSheet.createRow(i).createCell(0).setCellValue(tips[i]);
            }
            tipSheet.setColumnWidth(0, 100 * 256);
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new BusinessException(ResultCode.IMPORT_TEMPLATE_INVALID, "模板生成失败");
        }
    }

    private <T> ImportRowVO toVO(RowCheck<T> check) {
        ImportRowVO vo = new ImportRowVO();
        vo.setRowNo(check.rowNo());
        vo.setStudentNo(check.studentNo());
        vo.setName(check.label());
        vo.setValid(check.errors.isEmpty());
        vo.setErrors(List.copyOf(check.errors));
        return vo;
    }

    private ImportTaskVO toVO(SysImportTask task) {
        ImportTaskVO vo = new ImportTaskVO();
        vo.setId(task.getId());
        vo.setFileName(task.getFileName());
        vo.setTotalRows(task.getTotalRows() == null ? 0 : task.getTotalRows());
        vo.setSuccessRows(task.getSuccessRows() == null ? 0 : task.getSuccessRows());
        vo.setFailedRows(task.getFailedRows() == null ? 0 : task.getFailedRows());
        vo.setStatus(task.getStatus());
        vo.setOperatorId(task.getOperatorId());
        vo.setCreatedAt(task.getCreatedAt());
        return vo;
    }

    private String rootMessage(Throwable ex) {
        Throwable current = ex;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    @FunctionalInterface
    private interface RowWriter<T> {

        void write(T row);
    }

    private static class RowCheck<T> {

        private final T row;

        private final List<String> errors = new ArrayList<>();

        RowCheck(T row) {
            this.row = row;
        }

        int rowNo() {
            if (row instanceof RankRow rank) {
                return rank.getRowNo();
            }
            return ((CertRow) row).getRowNo();
        }

        String studentNo() {
            if (row instanceof RankRow rank) {
                return rank.getStudentNo();
            }
            return ((CertRow) row).getStudentNo();
        }

        String label() {
            if (row instanceof RankRow) {
                return null;
            }
            return ((CertRow) row).getCertName();
        }
    }
}
