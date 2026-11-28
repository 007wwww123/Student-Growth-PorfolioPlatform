package com.college.growth.export.classic.service;

import com.college.growth.export.classic.dto.ClassExportDTO;
import com.college.growth.export.classic.dto.SensitiveExportApplyDTO;
import com.college.growth.export.classic.vo.SensitiveExportApprovalVO;
import com.college.growth.export.vo.ExportTaskVO;
import java.util.List;

/** 班级 Excel 导出与敏感列审批（F-13 / AC-044）。 */
public interface ClassExportService {

    ExportTaskVO create(ClassExportDTO dto);

    SensitiveExportApprovalVO apply(SensitiveExportApplyDTO dto);

    List<SensitiveExportApprovalVO> approvals(String status);

    SensitiveExportApprovalVO approve(Long id, String remark);

    SensitiveExportApprovalVO reject(Long id, String remark);
}
