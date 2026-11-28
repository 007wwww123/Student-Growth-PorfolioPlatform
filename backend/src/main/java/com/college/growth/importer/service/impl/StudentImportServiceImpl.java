package com.college.growth.importer.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.college.growth.auth.entity.SysUser;
import com.college.growth.auth.mapper.SysUserMapper;
import com.college.growth.auth.service.PasswordHasher;
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
import com.college.growth.common.profile.mapper.ArcProfileMapper;
import com.college.growth.common.result.ResultCode;
import com.college.growth.common.student.entity.StuStudent;
import com.college.growth.common.student.mapper.StuStudentMapper;
import com.college.growth.common.util.TextUtils;
import com.college.growth.importer.entity.SysImportTask;
import com.college.growth.importer.mapper.SysImportTaskMapper;
import com.college.growth.importer.service.StudentImportParser;
import com.college.growth.importer.service.StudentImportParser.ParseResult;
import com.college.growth.importer.service.StudentImportParser.StudentRow;
import com.college.growth.importer.service.StudentImportService;
import com.college.growth.importer.vo.ImportPreviewVO;
import com.college.growth.importer.vo.ImportResultVO;
import com.college.growth.importer.vo.ImportRowVO;
import com.college.growth.importer.vo.ImportTaskVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
 * 学生批量导入实现（F-09 / AC-030–032）。
 *
 * <p>部分成功策略：合法行入库，非法行进回执；每行一个事务，避免
 * 「学生写进去、账号没建出来」的半行数据。COU 只能导入本班，ADMIN 可导入全部班级。</p>
 */
@Service
public class StudentImportServiceImpl implements StudentImportService {

    private static final Logger log = LoggerFactory.getLogger(StudentImportServiceImpl.class);

    private static final String TASK_STUDENT = "STUDENT";

    private static final String STATUS_SUCCESS = "SUCCESS";

    private static final String STATUS_PARTIAL = "PARTIAL";

    private static final String STATUS_FAILED = "FAILED";

    private final StudentImportParser parser;

    private final StuStudentMapper studentMapper;

    private final SysUserMapper userMapper;

    private final OrgClassMapper classMapper;

    private final ArcProfileMapper profileMapper;

    private final SysImportTaskMapper taskMapper;

    private final PasswordHasher passwordHasher;

    private final AuditLogService auditLogService;

    private final ObjectMapper objectMapper;

    private final TransactionTemplate transactionTemplate;

    public StudentImportServiceImpl(StudentImportParser parser, StuStudentMapper studentMapper,
                                    SysUserMapper userMapper, OrgClassMapper classMapper,
                                    ArcProfileMapper profileMapper, SysImportTaskMapper taskMapper,
                                    PasswordHasher passwordHasher, AuditLogService auditLogService,
                                    ObjectMapper objectMapper, PlatformTransactionManager transactionManager) {
        this.parser = parser;
        this.studentMapper = studentMapper;
        this.userMapper = userMapper;
        this.classMapper = classMapper;
        this.profileMapper = profileMapper;
        this.taskMapper = taskMapper;
        this.passwordHasher = passwordHasher;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public byte[] template() {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("学生导入");
            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);
            Row header = sheet.createRow(0);
            for (int i = 0; i < StudentImportParser.HEADERS.size(); i++) {
                var cell = header.createCell(i);
                cell.setCellValue(StudentImportParser.HEADERS.get(i));
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 18 * 256);
            }
            Sheet tips = workbook.createSheet("填写说明");
            String[] lines = {
                    "1. 仅支持 .xlsx；表头列名与顺序不可调整。",
                    "2. 学号为 8-12 位数字；身份证号 18 位且校验位正确。",
                    "3. 班级编码必须是系统中已启用的班级；辅导员只能导入本班。",
                    "4. 初始密码为身份证号后六位，首次登录后请及时修改。",
                    "5. 合法行入库，非法行进入回执；同一文件内学号/身份证号不可重复。"
            };
            for (int i = 0; i < lines.length; i++) {
                tips.createRow(i).createCell(0).setCellValue(lines[i]);
            }
            tips.setColumnWidth(0, 100 * 256);
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new BusinessException(ResultCode.IMPORT_TEMPLATE_INVALID, "模板生成失败");
        }
    }

    @Override
    public ImportPreviewVO preview(MultipartFile file) {
        ParseResult parsed = parser.parse(file);
        List<RowCheck> checks = validate(parsed.getRows());
        int valid = (int) checks.stream().filter(check -> check.errors.isEmpty()).count();
        ImportPreviewVO vo = new ImportPreviewVO();
        vo.setFileName(file.getOriginalFilename());
        vo.setHeaders(parsed.getHeaders());
        vo.setExpectedHeaders(StudentImportParser.HEADERS);
        vo.setHeaderValid(true);
        vo.setTotalRows(checks.size());
        vo.setValidRows(valid);
        vo.setInvalidRows(checks.size() - valid);
        vo.setRows(checks.stream().limit(20).map(this::toVO).collect(Collectors.toList()));
        return vo;
    }

    @Override
    public ImportResultVO execute(MultipartFile file) {
        ParseResult parsed = parser.parse(file);
        List<RowCheck> checks = validate(parsed.getRows());
        List<ImportRowVO> failures = new ArrayList<>();
        int success = 0;
        for (RowCheck check : checks) {
            if (!check.errors.isEmpty()) {
                failures.add(toVO(check));
                continue;
            }
            try {
                transactionTemplate.executeWithoutResult(status -> insertRow(check.row));
                success++;
            } catch (Exception ex) {
                check.errors.add("入库失败：" + rootMessage(ex));
                failures.add(toVO(check));
                log.warn("导入行失败 row={} studentNo={} reason={}",
                        check.row.getRowNo(), check.row.getStudentNo(), rootMessage(ex));
            }
        }
        int failed = checks.size() - success;
        SysImportTask task = new SysImportTask();
        task.setTaskType(TASK_STUDENT);
        task.setFileName(file.getOriginalFilename());
        task.setTotalRows(checks.size());
        task.setSuccessRows(success);
        task.setFailedRows(failed);
        task.setStatus(failed == 0 ? STATUS_SUCCESS : success == 0 ? STATUS_FAILED : STATUS_PARTIAL);
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
        log.info("学生导入完成 file={} total={} success={} failed={} taskId={}",
                file.getOriginalFilename(), checks.size(), success, failed, task.getId());
        return result;
    }

    @Override
    public List<ImportTaskVO> tasks() {
        return taskMapper.selectList(Wrappers.<SysImportTask>lambdaQuery()
                        .eq(SysImportTask::getTaskType, TASK_STUDENT)
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
                log.warn("导入回执解析失败 taskId={}", taskId, ex);
            }
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("错误回执");
            Row header = sheet.createRow(0);
            String[] columns = {"行号", "学号", "姓名", "错误原因"};
            for (int i = 0; i < columns.length; i++) {
                header.createCell(i).setCellValue(columns[i]);
                sheet.setColumnWidth(i, (i == 3 ? 60 : 18) * 256);
            }
            int rowIndex = 1;
            for (ImportRowVO error : errors) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(error.getRowNo());
                row.createCell(1).setCellValue(nullSafe(error.getStudentNo()));
                row.createCell(2).setCellValue(nullSafe(error.getName()));
                row.createCell(3).setCellValue(String.join("；", error.getErrors()));
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

    private List<RowCheck> validate(List<StudentRow> rows) {
        Set<String> classCodes = rows.stream().map(StudentRow::getClassCode)
                .filter(code -> !TextUtils.isBlank(code)).collect(Collectors.toSet());
        Map<String, OrgClass> classes = classCodes.isEmpty() ? Map.of()
                : classMapper.selectList(Wrappers.<OrgClass>lambdaQuery()
                        .in(OrgClass::getClassCode, classCodes))
                .stream().collect(Collectors.toMap(OrgClass::getClassCode, item -> item, (a, b) -> a));

        Set<String> studentNos = rows.stream().map(StudentRow::getStudentNo)
                .filter(no -> !TextUtils.isBlank(no)).collect(Collectors.toSet());
        Map<String, StuStudent> existingByNo = studentNos.isEmpty() ? Map.of()
                : studentMapper.selectList(Wrappers.<StuStudent>lambdaQuery()
                        .in(StuStudent::getStudentNo, studentNos))
                .stream().collect(Collectors.toMap(StuStudent::getStudentNo, item -> item, (a, b) -> a));
        Set<String> existingIdCards = new HashSet<>();
        Set<String> idCards = rows.stream().map(StudentRow::getIdCard)
                .filter(card -> !TextUtils.isBlank(card)).collect(Collectors.toSet());
        if (!idCards.isEmpty()) {
            for (StuStudent student : studentMapper.selectList(Wrappers.<StuStudent>lambdaQuery()
                    .in(StuStudent::getIdCard, idCards))) {
                if (!TextUtils.isBlank(student.getIdCard())) {
                    existingIdCards.add(student.getIdCard());
                }
            }
        }
        Set<String> existingUsernames = studentNos.isEmpty() ? Set.of()
                : userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                        .in(SysUser::getUsername, studentNos))
                .stream().map(SysUser::getUsername).collect(Collectors.toSet());

        CurrentUser user = UserContext.get();
        Set<String> seenNos = new HashSet<>();
        Set<String> seenIdCards = new HashSet<>();
        List<RowCheck> checks = new ArrayList<>();
        for (StudentRow row : rows) {
            RowCheck check = new RowCheck(row);
            if (TextUtils.isBlank(row.getStudentNo())) {
                check.errors.add("学号不能为空");
            } else if (!row.getStudentNo().matches("\\d{8,12}")) {
                check.errors.add("学号为 8-12 位数字");
            } else if (!seenNos.add(row.getStudentNo())) {
                check.errors.add("学号在文件中重复");
            } else if (existingByNo.containsKey(row.getStudentNo())) {
                check.errors.add("学号已存在");
            }
            if (TextUtils.isBlank(row.getName())) {
                check.errors.add("姓名不能为空");
            } else if (row.getName().length() > 64) {
                check.errors.add("姓名不能超过 64 字");
            }
            String gender = normalizeGender(row.getGender());
            if (gender == null) {
                check.errors.add("性别只能是 M/F（或男/女）");
            } else {
                row.setGender(gender);
            }
            if (TextUtils.isBlank(row.getIdCard())) {
                check.errors.add("身份证号不能为空");
            } else if (!isValidIdCard(row.getIdCard())) {
                check.errors.add("身份证号校验位不正确");
            } else if (!seenIdCards.add(row.getIdCard())) {
                check.errors.add("身份证号在文件中重复");
            } else if (existingIdCards.contains(row.getIdCard())) {
                check.errors.add("身份证号已存在");
            }
            if (TextUtils.isBlank(row.getClassCode())) {
                check.errors.add("班级编码不能为空");
            } else {
                OrgClass orgClass = classes.get(row.getClassCode());
                if (orgClass == null || orgClass.getStatus() == null || orgClass.getStatus() != 1) {
                    check.errors.add("班级不存在或已停用");
                } else if (user.role() == UserRoleEnum.COU
                        && !user.userId().equals(orgClass.getCounselorUserId())) {
                    check.errors.add("只能导入本班学生");
                }
            }
            if (TextUtils.isBlank(row.getEnrollmentYear())) {
                check.errors.add("入学年份不能为空");
            } else {
                try {
                    int year = Integer.parseInt(row.getEnrollmentYear());
                    if (year < 2000 || year > 2100) {
                        check.errors.add("入学年份应为四位年份");
                    }
                } catch (NumberFormatException ex) {
                    // 常见于 Excel 把年份写成 2024.0
                    String value = row.getEnrollmentYear();
                    boolean numeric = value.matches("\\d{4}\\.0");
                    if (numeric) {
                        row.setEnrollmentYear(value.substring(0, 4));
                    } else {
                        check.errors.add("入学年份应为四位年份");
                    }
                }
            }
            if (existingUsernames.contains(row.getStudentNo())) {
                check.errors.add("登录名已存在");
            }
            checks.add(check);
        }
        return checks;
    }

    private String normalizeGender(String raw) {
        if ("M".equalsIgnoreCase(raw) || "男".equals(raw)) {
            return "M";
        }
        if ("F".equalsIgnoreCase(raw) || "女".equals(raw)) {
            return "F";
        }
        return null;
    }

    /** GB 11643 校验位。 */
    private boolean isValidIdCard(String idCard) {
        if (!idCard.matches("\\d{17}[\\dXx]")) {
            return false;
        }
        int[] weights = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
        char[] codes = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            sum += (idCard.charAt(i) - '0') * weights[i];
        }
        char expected = codes[sum % 11];
        return Character.toUpperCase(idCard.charAt(17)) == expected;
    }

    private void insertRow(StudentRow row) {
        long studentId = IdWorker.getId();
        StuStudent student = new StuStudent();
        student.setId(studentId);
        student.setStudentNo(row.getStudentNo());
        student.setName(row.getName());
        student.setGender(row.getGender());
        student.setNation(TextUtils.isBlank(row.getNation()) ? null : row.getNation());
        student.setIdCard(row.getIdCard());
        student.setNativePlace(TextUtils.isBlank(row.getNativePlace()) ? null : row.getNativePlace());
        student.setClassCode(row.getClassCode());
        student.setEnrollmentYear(Integer.parseInt(row.getEnrollmentYear()));
        student.setStatus(1);
        student.setSyncSource("MANUAL");
        studentMapper.insert(student);

        SysUser user = new SysUser();
        user.setId(studentId);
        user.setUsername(row.getStudentNo());
        user.setDisplayName(row.getName());
        user.setRole(UserRoleEnum.STU.name());
        user.setStudentId(studentId);
        user.setPasswordHash(passwordHasher.hash(row.getIdCard().substring(row.getIdCard().length() - 6)));
        user.setStatus(1);
        user.setTempGrant(0);
        userMapper.insert(user);

        for (SectionCodeEnum section : SectionCodeEnum.values()) {
            ArcProfile profile = new ArcProfile();
            profile.setStudentId(studentId);
            profile.setAcademicYear(academicYearOf(row.getEnrollmentYear()));
            profile.setSectionCode(section.name());
            profile.setStatus(ProfileStatusEnum.DRAFT.name());
            profile.setCompleted(false);
            profile.setCompleteness(BigDecimal.ZERO);
            profile.setSubmitCount(0);
            profile.setWithdrawCount(0);
            profile.setVersion(0);
            profileMapper.insert(profile);
        }
        auditLogService.record(AuditLogService.BIZ_IMPORT, AuditLogService.TARGET_STUDENT,
                studentId, studentId, null, null, AuditLogService.ACTION_IMPORT,
                "导入学生：" + row.getStudentNo() + "（" + row.getName() + "）");
    }

    /** 导入学生默认建档学年：当前学年（与批量建档口径一致）。 */
    private String academicYearOf(String enrollmentYear) {
        return com.college.growth.common.util.AcademicYearUtils.current();
    }

    private ImportRowVO toVO(RowCheck check) {
        ImportRowVO vo = new ImportRowVO();
        vo.setRowNo(check.row.getRowNo());
        vo.setStudentNo(check.row.getStudentNo());
        vo.setName(check.row.getName());
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

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    /** 行 + 错误。 */
    private static class RowCheck {

        private final StudentRow row;

        private final List<String> errors = new ArrayList<>();

        RowCheck(StudentRow row) {
            this.row = row;
        }
    }
}
