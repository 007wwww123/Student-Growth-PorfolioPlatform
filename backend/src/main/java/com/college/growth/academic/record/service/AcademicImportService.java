package com.college.growth.academic.record.service;

import com.college.growth.importer.vo.ImportPreviewVO;
import com.college.growth.importer.vo.ImportResultVO;
import com.college.growth.importer.vo.ImportTaskVO;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/** 成绩/证书导入（F-10 / AC-033–035）。 */
public interface AcademicImportService {

    byte[] rankTemplate();

    byte[] certificateTemplate();

    ImportPreviewVO previewRanks(MultipartFile file);

    ImportResultVO executeRanks(MultipartFile file);

    ImportPreviewVO previewCertificates(MultipartFile file);

    ImportResultVO executeCertificates(MultipartFile file);

    List<ImportTaskVO> tasks();

    byte[] receipt(Long taskId);
}
