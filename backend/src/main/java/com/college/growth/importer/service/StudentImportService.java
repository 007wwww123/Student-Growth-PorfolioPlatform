package com.college.growth.importer.service;

import com.college.growth.importer.vo.ImportPreviewVO;
import com.college.growth.importer.vo.ImportResultVO;
import com.college.growth.importer.vo.ImportTaskVO;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/** 学生批量导入（F-09 / S-2）：模板、预览、执行、回执。 */
public interface StudentImportService {

    byte[] template();

    ImportPreviewVO preview(MultipartFile file);

    ImportResultVO execute(MultipartFile file);

    List<ImportTaskVO> tasks();

    byte[] receipt(Long taskId);
}
