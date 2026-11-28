package com.college.growth.academic.record.service;

import com.college.growth.academic.record.dto.CertificateSaveDTO;
import com.college.growth.academic.record.dto.EventSaveDTO;
import com.college.growth.academic.record.dto.VerifyDTO;
import com.college.growth.academic.record.vo.AcademicRecordVO;
import com.college.growth.academic.record.vo.CertificateVO;
import com.college.growth.academic.record.vo.EventVO;

/** 学业成果（F-10 / 4.8 / 4.9）：成绩、证书、竞赛与认定。 */
public interface AcademicRecordService {

    AcademicRecordVO records(Long studentId, String academicYear);

    CertificateVO createCertificate(Long studentId, CertificateSaveDTO dto);

    CertificateVO updateCertificate(Long studentId, Long id, CertificateSaveDTO dto);

    void deleteCertificate(Long studentId, Long id);

    CertificateVO verifyCertificate(Long studentId, Long id, VerifyDTO dto);

    EventVO createEvent(Long studentId, EventSaveDTO dto);

    EventVO updateEvent(Long studentId, Long id, EventSaveDTO dto);

    void deleteEvent(Long studentId, Long id);

    EventVO verifyEvent(Long studentId, Long id, VerifyDTO dto);
}
