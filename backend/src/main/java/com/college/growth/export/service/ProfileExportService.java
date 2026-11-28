package com.college.growth.export.service;

import com.college.growth.export.vo.ExportTaskVO;

/** 单人档案 PDF 导出（F-12 / S-3）。 */
public interface ProfileExportService {

    ExportTaskVO create(Long studentId, String academicYear);

    ExportTaskVO status(Long taskId);

    byte[] download(Long taskId);
}
