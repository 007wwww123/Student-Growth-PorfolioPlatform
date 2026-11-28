package com.college.growth.academic.record.controller;

import com.college.growth.academic.record.dto.CertificateSaveDTO;
import com.college.growth.academic.record.dto.EventSaveDTO;
import com.college.growth.academic.record.dto.VerifyDTO;
import com.college.growth.academic.record.service.AcademicRecordService;
import com.college.growth.academic.record.vo.AcademicRecordVO;
import com.college.growth.academic.record.vo.CertificateVO;
import com.college.growth.academic.record.vo.EventVO;
import com.college.growth.common.result.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学业成果接口（F-10 / 4.8 / 4.9）。
 *
 * <p>学生维护本人证书/竞赛，COU 认定；成绩轨迹只读。</p>
 */
@RestController
@RequestMapping("/academic/records")
public class AcademicRecordController {

    private final AcademicRecordService academicRecordService;

    public AcademicRecordController(AcademicRecordService academicRecordService) {
        this.academicRecordService = academicRecordService;
    }

    @GetMapping("/{studentId}")
    public Result<AcademicRecordVO> records(@PathVariable Long studentId,
                                            @RequestParam(required = false) String academicYear) {
        return Result.success(academicRecordService.records(studentId, academicYear));
    }

    @PostMapping("/{studentId}/certificates")
    public Result<CertificateVO> createCertificate(@PathVariable Long studentId,
                                                   @Valid @RequestBody CertificateSaveDTO dto) {
        return Result.success(academicRecordService.createCertificate(studentId, dto));
    }

    @PutMapping("/{studentId}/certificates/{id}")
    public Result<CertificateVO> updateCertificate(@PathVariable Long studentId,
                                                   @PathVariable Long id,
                                                   @Valid @RequestBody CertificateSaveDTO dto) {
        return Result.success(academicRecordService.updateCertificate(studentId, id, dto));
    }

    @DeleteMapping("/{studentId}/certificates/{id}")
    public Result<Void> deleteCertificate(@PathVariable Long studentId, @PathVariable Long id) {
        academicRecordService.deleteCertificate(studentId, id);
        return Result.success();
    }

    @PutMapping("/{studentId}/certificates/{id}/verify")
    public Result<CertificateVO> verifyCertificate(@PathVariable Long studentId,
                                                   @PathVariable Long id,
                                                   @Valid @RequestBody VerifyDTO dto) {
        return Result.success(academicRecordService.verifyCertificate(studentId, id, dto));
    }

    @PostMapping("/{studentId}/events")
    public Result<EventVO> createEvent(@PathVariable Long studentId,
                                       @Valid @RequestBody EventSaveDTO dto) {
        return Result.success(academicRecordService.createEvent(studentId, dto));
    }

    @PutMapping("/{studentId}/events/{id}")
    public Result<EventVO> updateEvent(@PathVariable Long studentId,
                                       @PathVariable Long id,
                                       @Valid @RequestBody EventSaveDTO dto) {
        return Result.success(academicRecordService.updateEvent(studentId, id, dto));
    }

    @DeleteMapping("/{studentId}/events/{id}")
    public Result<Void> deleteEvent(@PathVariable Long studentId, @PathVariable Long id) {
        academicRecordService.deleteEvent(studentId, id);
        return Result.success();
    }

    @PutMapping("/{studentId}/events/{id}/verify")
    public Result<EventVO> verifyEvent(@PathVariable Long studentId,
                                       @PathVariable Long id,
                                       @Valid @RequestBody VerifyDTO dto) {
        return Result.success(academicRecordService.verifyEvent(studentId, id, dto));
    }
}
