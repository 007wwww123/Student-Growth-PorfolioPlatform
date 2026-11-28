package com.college.growth.academic.record.controller;

import com.college.growth.academic.record.service.AcademicImportService;
import com.college.growth.common.result.Result;
import com.college.growth.importer.vo.ImportPreviewVO;
import com.college.growth.importer.vo.ImportResultVO;
import com.college.growth.importer.vo.ImportTaskVO;
import com.college.growth.permission.support.RequirePermission;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 成绩/证书导入接口（F-10 / AC-033–035）。
 *
 * <p>模板 → 预览 → 执行 → 回执；成绩按学生+学期覆盖，证书以 PENDING 入库。</p>
 */
@RestController
@RequestMapping("/academic/records/import")
public class AcademicImportController {

    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final AcademicImportService academicImportService;

    public AcademicImportController(AcademicImportService academicImportService) {
        this.academicImportService = academicImportService;
    }

    @GetMapping("/ranks/template")
    @RequirePermission("import:rank:execute")
    public ResponseEntity<byte[]> rankTemplate() {
        return download(academicImportService.rankTemplate(), "academic-rank-template.xlsx");
    }

    @PostMapping("/ranks/preview")
    @RequirePermission("import:rank:execute")
    public Result<ImportPreviewVO> previewRanks(@RequestParam("file") MultipartFile file) {
        return Result.success(academicImportService.previewRanks(file));
    }

    @PostMapping("/ranks/execute")
    @RequirePermission("import:rank:execute")
    public Result<ImportResultVO> executeRanks(@RequestParam("file") MultipartFile file) {
        return Result.success(academicImportService.executeRanks(file));
    }

    @GetMapping("/certificates/template")
    @RequirePermission("import:cert:verify")
    public ResponseEntity<byte[]> certificateTemplate() {
        return download(academicImportService.certificateTemplate(), "academic-cert-template.xlsx");
    }

    @PostMapping("/certificates/preview")
    @RequirePermission("import:cert:verify")
    public Result<ImportPreviewVO> previewCertificates(@RequestParam("file") MultipartFile file) {
        return Result.success(academicImportService.previewCertificates(file));
    }

    @PostMapping("/certificates/execute")
    @RequirePermission("import:cert:verify")
    public Result<ImportResultVO> executeCertificates(@RequestParam("file") MultipartFile file) {
        return Result.success(academicImportService.executeCertificates(file));
    }

    @GetMapping("/tasks")
    @RequirePermission("import:rank:execute")
    public Result<List<ImportTaskVO>> tasks() {
        return Result.success(academicImportService.tasks());
    }

    @GetMapping("/tasks/{id}/receipt")
    @RequirePermission("import:rank:execute")
    public ResponseEntity<byte[]> receipt(@PathVariable Long id) {
        return download(academicImportService.receipt(id), "academic-import-receipt-" + id + ".xlsx");
    }

    private ResponseEntity<byte[]> download(byte[] bytes, String fileName) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(fileName, StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(XLSX)
                .body(bytes);
    }
}
