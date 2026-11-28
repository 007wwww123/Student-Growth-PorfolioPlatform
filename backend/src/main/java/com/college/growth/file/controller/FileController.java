package com.college.growth.file.controller;

import com.college.growth.common.result.Result;
import com.college.growth.file.service.FileService;
import com.college.growth.file.vo.FileVO;
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
import org.springframework.web.multipart.MultipartFile;

/** 附件上传/下载（B-6）。 */
@RestController
@RequestMapping("/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping
    @RequirePermission("archive:profile:write")
    public Result<FileVO> upload(@RequestParam("file") MultipartFile file,
                                 @RequestParam Long studentId,
                                 @RequestParam(required = false) String bizType,
                                 @RequestParam(required = false) Long bizId) {
        return Result.success(fileService.upload(file, studentId, bizType, bizId));
    }

    @GetMapping("/{id}")
    @RequirePermission("archive:profile:read")
    public Result<FileVO> meta(@PathVariable Long id) {
        return Result.success(fileService.meta(id));
    }

    @GetMapping("/{id}/download")
    @RequirePermission("archive:profile:read")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        FileVO meta = fileService.meta(id);
        byte[] bytes = fileService.download(id);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(meta.getOriginalName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(meta.getContentType() == null
                        ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(meta.getContentType()))
                .body(bytes);
    }
}
