package com.college.growth.importer.controller;

import com.college.growth.common.result.Result;
import com.college.growth.importer.service.StudentImportService;
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
 * 学生批量导入接口（F-09 / S-2 / AC-030–032）。
 *
 * <p>模板 → 预览 → 执行 → 回执四步；预览与执行都重新上传同一文件，服务端每次都完整校验。</p>
 */
@RestController
@RequestMapping("/admin/import")
@RequirePermission("import:student:execute")
public class StudentImportController {

    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final StudentImportService studentImportService;

    public StudentImportController(StudentImportService studentImportService) {
        this.studentImportService = studentImportService;
    }

    @GetMapping("/students/template")
    public ResponseEntity<byte[]> template() {
        return download(studentImportService.template(), "student-import-template.xlsx");
    }

    @PostMapping("/students/preview")
    public Result<ImportPreviewVO> preview(@RequestParam("file") MultipartFile file) {
        return Result.success(studentImportService.preview(file));
    }

    @PostMapping("/students/execute")
    public Result<ImportResultVO> execute(@RequestParam("file") MultipartFile file) {
        return Result.success(studentImportService.execute(file));
    }

    @GetMapping("/tasks")
    public Result<List<ImportTaskVO>> tasks() {
        return Result.success(studentImportService.tasks());
    }

    @GetMapping("/tasks/{id}/receipt")
    public ResponseEntity<byte[]> receipt(@PathVariable Long id) {
        return download(studentImportService.receipt(id), "student-import-receipt-" + id + ".xlsx");
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
