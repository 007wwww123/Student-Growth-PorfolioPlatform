package com.college.growth.academic.record.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.college.growth.academic.record.dto.CertificateSaveDTO;
import com.college.growth.academic.record.dto.EventSaveDTO;
import com.college.growth.academic.record.dto.VerifyDTO;
import com.college.growth.academic.record.entity.ArcCertificate;
import com.college.growth.academic.record.entity.ArcEvent;
import com.college.growth.academic.record.entity.ArcRank;
import com.college.growth.academic.record.mapper.ArcCertificateMapper;
import com.college.growth.academic.record.mapper.ArcEventMapper;
import com.college.growth.academic.record.mapper.ArcRankMapper;
import com.college.growth.academic.record.service.AcademicRecordService;
import com.college.growth.academic.record.vo.AcademicRecordVO;
import com.college.growth.academic.record.vo.CertificateVO;
import com.college.growth.academic.record.vo.EventVO;
import com.college.growth.academic.record.vo.RankVO;
import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.context.UserContext;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.log.service.AuditLogService;
import com.college.growth.common.org.service.StudentClassService;
import com.college.growth.common.result.ResultCode;
import com.college.growth.common.student.entity.StuStudent;
import com.college.growth.common.student.mapper.StuStudentMapper;
import com.college.growth.common.util.AcademicYearUtils;
import com.college.growth.common.util.TextUtils;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 学业成果实现（F-10 / 4.8 / 4.9）。
 *
 * <p>成绩轨迹只读（由教务 Excel 导入）；证书/竞赛由学生本人新增与修改，
 * COU 只能做认定（PASSED/REJECTED），认定后学生不可再改。只有 PASSED 计入成果统计。</p>
 */
@Service
public class AcademicRecordServiceImpl implements AcademicRecordService {

    private static final Set<String> CERT_TYPES = Set.of("外语", "计算机", "职业资格", "技能", "其他");

    private static final Set<String> EVENT_TYPES = Set.of("COMPETITION", "PROJECT", "RESEARCH");

    private static final Set<String> EVENT_LEVELS = Set.of("SCHOOL", "PROVINCE", "NATIONAL", "INTERNATIONAL");

    private static final String PENDING = "PENDING";

    private static final String PASSED = "PASSED";

    private static final String REJECTED = "REJECTED";

    private final ArcRankMapper rankMapper;

    private final ArcCertificateMapper certificateMapper;

    private final ArcEventMapper eventMapper;

    private final StuStudentMapper studentMapper;

    private final StudentClassService studentClassService;

    private final AuditLogService auditLogService;

    public AcademicRecordServiceImpl(ArcRankMapper rankMapper, ArcCertificateMapper certificateMapper,
                                     ArcEventMapper eventMapper, StuStudentMapper studentMapper,
                                     StudentClassService studentClassService,
                                     AuditLogService auditLogService) {
        this.rankMapper = rankMapper;
        this.certificateMapper = certificateMapper;
        this.eventMapper = eventMapper;
        this.studentMapper = studentMapper;
        this.studentClassService = studentClassService;
        this.auditLogService = auditLogService;
    }

    @Override
    public AcademicRecordVO records(Long studentId, String academicYear) {
        String year = AcademicYearUtils.resolve(academicYear);
        studentClassService.requireStudentScope(studentId, year, UserContext.get());
        List<ArcRank> ranks = rankMapper.selectList(Wrappers.<ArcRank>lambdaQuery()
                .eq(ArcRank::getStudentId, studentId)
                .orderByDesc(ArcRank::getAcademicYear));
        List<ArcCertificate> certificates = certificateMapper.selectList(
                Wrappers.<ArcCertificate>lambdaQuery()
                        .eq(ArcCertificate::getStudentId, studentId)
                        .orderByDesc(ArcCertificate::getId));
        List<ArcEvent> events = eventMapper.selectList(Wrappers.<ArcEvent>lambdaQuery()
                .eq(ArcEvent::getStudentId, studentId)
                .orderByDesc(ArcEvent::getId));

        AcademicRecordVO vo = new AcademicRecordVO();
        vo.setStudentId(studentId);
        vo.setRanks(ranks.stream().map(this::toVO).collect(Collectors.toList()));
        vo.setCertificates(certificates.stream().map(this::toVO).collect(Collectors.toList()));
        vo.setEvents(events.stream().map(this::toVO).collect(Collectors.toList()));
        vo.setPassedCertificates((int) certificates.stream()
                .filter(item -> PASSED.equals(item.getVerifyStatus())).count());
        vo.setPassedEvents((int) events.stream()
                .filter(item -> PASSED.equals(item.getVerifyStatus())).count());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CertificateVO createCertificate(Long studentId, CertificateSaveDTO dto) {
        requireStudentSelf(studentId);
        StuStudent student = requireStudent(studentId);
        validateCertificate(dto, student);
        long duplicate = certificateMapper.selectCount(Wrappers.<ArcCertificate>lambdaQuery()
                .eq(ArcCertificate::getStudentId, studentId)
                .eq(ArcCertificate::getCertName, dto.getCertName().trim())
                .in(ArcCertificate::getVerifyStatus, PENDING, PASSED));
        if (duplicate > 0) {
            throw new BusinessException(ResultCode.STATE_CONFLICT, "同名证书已提交或已认定，请勿重复提交");
        }
        ArcCertificate entity = new ArcCertificate();
        entity.setStudentId(studentId);
        apply(entity, dto);
        entity.setVerifyStatus(PENDING);
        certificateMapper.insert(entity);
        audit(studentId, "academic.certificates", AuditLogService.ACTION_CREATE,
                "新增证书：" + entity.getCertName());
        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CertificateVO updateCertificate(Long studentId, Long id, CertificateSaveDTO dto) {
        requireStudentSelf(studentId);
        ArcCertificate entity = requireCertificate(studentId, id);
        requirePending(entity.getVerifyStatus());
        StuStudent student = requireStudent(studentId);
        validateCertificate(dto, student);
        apply(entity, dto);
        certificateMapper.updateById(entity);
        audit(studentId, "academic.certificates", AuditLogService.ACTION_UPDATE,
                "修改证书：" + entity.getCertName());
        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCertificate(Long studentId, Long id) {
        requireStudentSelf(studentId);
        ArcCertificate entity = requireCertificate(studentId, id);
        requirePending(entity.getVerifyStatus());
        certificateMapper.deleteById(id);
        audit(studentId, "academic.certificates", AuditLogService.ACTION_DISABLE,
                "删除待认定证书：" + entity.getCertName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CertificateVO verifyCertificate(Long studentId, Long id, VerifyDTO dto) {
        requireCounselorOf(studentId);
        ArcCertificate entity = requireCertificate(studentId, id);
        applyVerify(dto);
        entity.setVerifyStatus(dto.getStatus().trim().toUpperCase());
        entity.setVerifyUserId(UserContext.get().userId());
        entity.setVerifyAt(LocalDateTime.now());
        entity.setVerifyRemark(TextUtils.isBlank(dto.getRemark()) ? null : dto.getRemark().trim());
        certificateMapper.updateById(entity);
        audit(studentId, "academic.certificates", AuditLogService.ACTION_UPDATE,
                "证书认定：" + entity.getCertName() + " -> " + entity.getVerifyStatus());
        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EventVO createEvent(Long studentId, EventSaveDTO dto) {
        requireStudentSelf(studentId);
        validateEvent(dto);
        ArcEvent entity = new ArcEvent();
        entity.setStudentId(studentId);
        apply(entity, dto);
        entity.setVerifyStatus(PENDING);
        eventMapper.insert(entity);
        audit(studentId, "academic.events", AuditLogService.ACTION_CREATE,
                "新增竞赛/科研：" + entity.getTitle());
        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EventVO updateEvent(Long studentId, Long id, EventSaveDTO dto) {
        requireStudentSelf(studentId);
        ArcEvent entity = requireEvent(studentId, id);
        requirePending(entity.getVerifyStatus());
        validateEvent(dto);
        apply(entity, dto);
        eventMapper.updateById(entity);
        audit(studentId, "academic.events", AuditLogService.ACTION_UPDATE,
                "修改竞赛/科研：" + entity.getTitle());
        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteEvent(Long studentId, Long id) {
        requireStudentSelf(studentId);
        ArcEvent entity = requireEvent(studentId, id);
        requirePending(entity.getVerifyStatus());
        eventMapper.deleteById(id);
        audit(studentId, "academic.events", AuditLogService.ACTION_DISABLE,
                "删除待认定竞赛/科研：" + entity.getTitle());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EventVO verifyEvent(Long studentId, Long id, VerifyDTO dto) {
        requireCounselorOf(studentId);
        ArcEvent entity = requireEvent(studentId, id);
        applyVerify(dto);
        entity.setVerifyStatus(dto.getStatus().trim().toUpperCase());
        entity.setVerifyUserId(UserContext.get().userId());
        entity.setVerifyAt(LocalDateTime.now());
        entity.setVerifyRemark(TextUtils.isBlank(dto.getRemark()) ? null : dto.getRemark().trim());
        eventMapper.updateById(entity);
        audit(studentId, "academic.events", AuditLogService.ACTION_UPDATE,
                "竞赛/科研认定：" + entity.getTitle() + " -> " + entity.getVerifyStatus());
        return toVO(entity);
    }

    // ------------------------------------------------------------------
    // 校验与权限
    // ------------------------------------------------------------------

    private void validateCertificate(CertificateSaveDTO dto, StuStudent student) {
        String certType = dto.getCertType() == null ? "" : dto.getCertType().trim();
        if (!CERT_TYPES.contains(certType)) {
            throw new BusinessException(ResultCode.PARAM_INVALID,
                    "证书类型只能是：外语/计算机/职业资格/技能/其他");
        }
        if (dto.getObtainDate() == null || dto.getObtainDate().isAfter(LocalDate.now())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "取得日期不能晚于今天");
        }
        LocalDate enrollFrom = LocalDate.of(student.getEnrollmentYear() == null
                ? 2000 : student.getEnrollmentYear(), 1, 1);
        if (dto.getObtainDate().isBefore(enrollFrom)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "取得日期不能早于入学日期");
        }
        String score = dto.getScore() == null ? "" : dto.getScore().trim();
        if (score.matches("\\d+") && (score.length() > 3 || Integer.parseInt(score) > 999)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "成绩为纯数字时不能超过 999");
        }
    }

    private void validateEvent(EventSaveDTO dto) {
        String type = dto.getEventType() == null ? "" : dto.getEventType().trim().toUpperCase();
        if (!EVENT_TYPES.contains(type)) {
            throw new BusinessException(ResultCode.PARAM_INVALID,
                    "竞赛类型只能是 COMPETITION/PROJECT/RESEARCH");
        }
        String level = dto.getLevel() == null ? "" : dto.getLevel().trim().toUpperCase();
        if (!EVENT_LEVELS.contains(level)) {
            throw new BusinessException(ResultCode.PARAM_INVALID,
                    "级别只能是 SCHOOL/PROVINCE/NATIONAL/INTERNATIONAL");
        }
        if (dto.getStartDate() == null || dto.getStartDate().isAfter(LocalDate.now())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "开始日期不能晚于今天");
        }
        if (dto.getEndDate() != null && dto.getEndDate().isBefore(dto.getStartDate())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "结束日期不能早于开始日期");
        }
        dto.setEventType(type);
        dto.setLevel(level);
    }

    private void applyVerify(VerifyDTO dto) {
        String status = dto.getStatus() == null ? "" : dto.getStatus().trim().toUpperCase();
        if (!PASSED.equals(status) && !REJECTED.equals(status)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "认定结论只能是 PASSED 或 REJECTED");
        }
        if (REJECTED.equals(status) && TextUtils.isBlank(dto.getRemark())) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "退回时必须填写认定意见");
        }
        dto.setStatus(status);
    }

    private void requireStudentSelf(Long studentId) {
        CurrentUser user = UserContext.get();
        if (!user.isStudent() || user.userId() == null || !user.userId().equals(studentId)) {
            throw new BusinessException(ResultCode.PERMISSION_DENIED, "仅学生本人可以维护自己的证书与竞赛");
        }
    }

    private void requireCounselorOf(Long studentId) {
        CurrentUser user = UserContext.get();
        if (!user.isCounselor() || user.userId() == null
                || !studentClassService.isCounselorOf(studentId, AcademicYearUtils.current(), user.userId())) {
            throw new BusinessException(ResultCode.DATA_SCOPE_DENIED, "仅本班辅导员可以认定");
        }
    }

    private StuStudent requireStudent(Long studentId) {
        StuStudent student = studentId == null ? null : studentMapper.selectById(studentId);
        if (student == null) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "学生不存在：" + studentId);
        }
        return student;
    }

    private ArcCertificate requireCertificate(Long studentId, Long id) {
        ArcCertificate entity = id == null ? null : certificateMapper.selectById(id);
        if (entity == null || !studentId.equals(entity.getStudentId())) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "证书不存在：" + id);
        }
        return entity;
    }

    private ArcEvent requireEvent(Long studentId, Long id) {
        ArcEvent entity = id == null ? null : eventMapper.selectById(id);
        if (entity == null || !studentId.equals(entity.getStudentId())) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "竞赛/科研不存在：" + id);
        }
        return entity;
    }

    private void requirePending(String status) {
        if (!PENDING.equals(status)) {
            throw new BusinessException(ResultCode.STATE_CONFLICT, "已认定的记录不能修改或删除");
        }
    }

    private void apply(ArcCertificate entity, CertificateSaveDTO dto) {
        entity.setCertType(dto.getCertType().trim());
        entity.setCertName(dto.getCertName().trim());
        entity.setScore(TextUtils.isBlank(dto.getScore()) ? null : dto.getScore().trim());
        entity.setObtainDate(dto.getObtainDate());
        entity.setEvidenceFileId(dto.getEvidenceFileId());
    }

    private void apply(ArcEvent entity, EventSaveDTO dto) {
        entity.setEventType(dto.getEventType().trim().toUpperCase());
        entity.setTitle(dto.getTitle().trim());
        entity.setRoleName(TextUtils.isBlank(dto.getRoleName()) ? null : dto.getRoleName().trim());
        entity.setLevel(dto.getLevel().trim().toUpperCase());
        entity.setStartDate(dto.getStartDate());
        entity.setEndDate(dto.getEndDate());
        entity.setDescription(TextUtils.isBlank(dto.getDescription()) ? null : dto.getDescription().trim());
        entity.setEvidenceFileId(dto.getEvidenceFileId());
    }

    private void audit(Long studentId, String fieldCode, String action, String detail) {
        auditLogService.record(AuditLogService.BIZ_ACADEMIC, AuditLogService.TARGET_STUDENT,
                studentId, studentId, "ACADEMIC", fieldCode, action, detail);
    }

    private RankVO toVO(ArcRank entity) {
        RankVO vo = new RankVO();
        vo.setId(entity.getId());
        vo.setAcademicYear(entity.getAcademicYear());
        vo.setClassRank(entity.getClassRank());
        vo.setMajorRank(entity.getMajorRank());
        vo.setGpa(entity.getGpa());
        vo.setSource(entity.getSource());
        return vo;
    }

    private CertificateVO toVO(ArcCertificate entity) {
        CertificateVO vo = new CertificateVO();
        vo.setId(entity.getId());
        vo.setStudentId(entity.getStudentId());
        vo.setCertType(entity.getCertType());
        vo.setCertName(entity.getCertName());
        vo.setScore(entity.getScore());
        vo.setObtainDate(entity.getObtainDate());
        vo.setEvidenceFileId(entity.getEvidenceFileId());
        vo.setVerifyStatus(entity.getVerifyStatus());
        vo.setVerifyUserId(entity.getVerifyUserId());
        vo.setVerifyAt(entity.getVerifyAt());
        vo.setVerifyRemark(entity.getVerifyRemark());
        return vo;
    }

    private EventVO toVO(ArcEvent entity) {
        EventVO vo = new EventVO();
        vo.setId(entity.getId());
        vo.setStudentId(entity.getStudentId());
        vo.setEventType(entity.getEventType());
        vo.setTitle(entity.getTitle());
        vo.setRoleName(entity.getRoleName());
        vo.setLevel(entity.getLevel());
        vo.setStartDate(entity.getStartDate());
        vo.setEndDate(entity.getEndDate());
        vo.setDescription(entity.getDescription());
        vo.setEvidenceFileId(entity.getEvidenceFileId());
        vo.setVerifyStatus(entity.getVerifyStatus());
        vo.setVerifyUserId(entity.getVerifyUserId());
        vo.setVerifyAt(entity.getVerifyAt());
        vo.setVerifyRemark(entity.getVerifyRemark());
        return vo;
    }
}
