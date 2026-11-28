package com.college.growth.export.controller;

import com.college.growth.common.result.Result;
import com.college.growth.export.service.ProfileExportService;
import com.college.growth.export.vo.ExportTaskVO;
import com.college.growth.permission.support.RequirePermission;
import java.nio.charset.StandardCharsets;
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

/**
 * 单人档案 PDF 导出接口（F-12 / S-3）。
 *
 * <p>创建任务 → 查询状态 → 24 小时内下载；下载过期返回 EC-41001。</p>
 */
@RestController
@RequestMapping("/export")
public class ProfileExportController {

    private static final MediaType PDF = MediaType.APPLICATION_PDF;

    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ProfileExportService profileExportService;

    public ProfileExportController(ProfileExportService profileExportService) {
        this.profileExportService = profileExportService;
    }

    @PostMapping("/profiles/{studentId}")
    @RequirePermission("export:profile:pdf")
    public Result<ExportTaskVO> create(@PathVariable Long studentId,
                                       @RequestParam(required = false) String academicYear) {
        return Result.success(profileExportService.create(studentId, academicYear));
    }

    @GetMapping("/tasks/{id}")
    public Result<ExportTaskVO> status(@PathVariable Long id) {
        return Result.success(profileExportService.status(id));
    }

    @GetMapping("/tasks/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        ExportTaskVO task = profileExportService.status(id);
        byte[] bytes = profileExportService.download(id);
        boolean excel = task.getFileName() != null && task.getFileName().toLowerCase().endsWith(".xlsx");
        String fileName = task.getFileName() == null
                ? (excel ? "export.xlsx" : "student-profile.pdf") : task.getFileName();
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(fileName, StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(excel ? XLSX : PDF)
                .body(bytes);
    }
}
