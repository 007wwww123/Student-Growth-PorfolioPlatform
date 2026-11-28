package com.college.growth.mentor.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.college.growth.basic.vo.FieldMetaVO;
import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.context.UserContext;
import com.college.growth.common.enums.ProfileStatusEnum;
import com.college.growth.common.enums.UserRoleEnum;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.field.FieldValues;
import com.college.growth.common.field.entity.ArcFieldDef;
import com.college.growth.common.field.service.FieldDefService;
import com.college.growth.common.field.validation.FieldValidatorRegistry;
import com.college.growth.common.log.service.AuditLogService;
import com.college.growth.common.log.service.ChangeLogService;
import com.college.growth.common.org.service.StudentClassService;
import com.college.growth.common.profile.entity.ArcProfile;
import com.college.growth.common.profile.mapper.ArcProfileMapper;
import com.college.growth.common.profile.service.ProfileCompletenessService;
import com.college.growth.common.profile.service.ProfileLocator;
import com.college.growth.common.result.FieldErrorDetail;
import com.college.growth.common.result.ResultCode;
import com.college.growth.common.student.entity.StuStudent;
import com.college.growth.common.student.service.StudentMasterService;
import com.college.growth.common.util.AcademicYearUtils;
import com.college.growth.mentor.common.MentorConstants;
import com.college.growth.mentor.dto.MentorFeedbackDTO;
import com.college.growth.mentor.dto.MentorNoteCreateDTO;
import com.college.growth.mentor.dto.MentorSectionSaveDTO;
import com.college.growth.mentor.entity.ArcMentorNote;
import com.college.growth.mentor.mapper.ArcMentorNoteMapper;
import com.college.growth.mentor.service.MentorSectionService;
import com.college.growth.mentor.util.MentorSectionUtils;
import com.college.growth.mentor.vo.MentorNoteVO;
import com.college.growth.mentor.vo.MentorSectionVO;
import com.college.growth.review.service.ProfileWorkflowService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 板块六 导师寄语（MENTOR）业务实现。
 *
 * <p>与其余五个板块的结构性差别（需求 4.7）：<b>没有主表字段值</b>，
 * 全部内容在 {@code arc_mentor_note} 子表，一行一次谈话，只追加不修改不删除（BR-S07）。
 * 因此本实现不委托 {@code SectionFormService.save}（它会把字段写进 {@code arc_profile_item}），
 * 但状态机、数据范围、字段定义、校验器、双日志、详情信封沿用同一套公共组件，
 * 保证「同一个状态在不同板块能改」这类偏差不会出现。</p>
 *
 * <p>完成度与审核就绪由 {@code MentorCompletenessProvider} 接管，
 * 见 {@code SectionCompletenessProvider} 的说明。</p>
 */
@Service
@RequiredArgsConstructor
public class MentorSectionServiceImpl implements MentorSectionService {

    private static final BigDecimal FULL = new BigDecimal("100.00");

    private final ProfileLocator locator;

    private final ArcProfileMapper profiles;

    private final ArcMentorNoteMapper notes;

    private final FieldDefService definitions;

    private final FieldValidatorRegistry validators;

    private final StudentClassService classes;

    private final StudentMasterService students;

    private final ProfileWorkflowService workflow;

    private final ProfileCompletenessService completeness;

    private final ChangeLogService history;

    private final AuditLogService audit;

    @Override
    @Transactional(readOnly = true)
    public MentorSectionVO detail(Long studentId, String academicYear) {
        String year = AcademicYearUtils.resolve(academicYear);
        CurrentUser user = UserContext.get();
        // 未持临时授权的管理员没有业务数据权限：返回空视图，与板块一至五一致（需求 2.1）。
        if (!user.canReadBusinessData()) {
            return emptyView(studentId, year);
        }
        classes.requireStudentScope(studentId, year, user);
        ArcProfile profile = locator.find(studentId, MentorConstants.SECTION_CODE, year);
        String status = profile == null ? ProfileStatusEnum.DRAFT.name() : profile.getStatus();
        boolean writable = ProfileStatusEnum.valueOf(status).isWritable();
        boolean counselorView = user.isCounselor() || user.isAdminWithGrant();

        List<ArcFieldDef> defs = definitions.listActive(MentorConstants.SECTION_CODE);
        ArcFieldDef topicDef = defs.stream()
                .filter(def -> "topic".equals(MentorSectionUtils.shortName(def)))
                .findFirst().orElse(null);

        List<FieldMetaVO> metadata = new ArrayList<>();
        for (ArcFieldDef def : defs) {
            if (user.isAdminWithGrant() || hasRole(def.getVisibleRoles(), user)) {
                metadata.add(FieldMetaVO.from(def,
                        writable && !user.isAdminWithGrant() && hasRole(def.getEditableRoles(), user),
                        false));
            }
        }

        // 时间线按 talkDate 倒序；同日用 createdAt / id 兜底，保证顺序稳定（需求 4.7）。
        List<ArcMentorNote> rows = profile == null ? List.of() : notes.selectList(
                Wrappers.<ArcMentorNote>lambdaQuery()
                        .eq(ArcMentorNote::getProfileId, profile.getId())
                        .orderByDesc(ArcMentorNote::getTalkDate, ArcMentorNote::getCreatedAt, ArcMentorNote::getId));
        List<MentorNoteVO> timeline = rows.stream()
                .map(note -> MentorSectionUtils.toView(note, counselorView, user.isStudent(),
                        MentorSectionUtils.topicLabelOf(topicDef, note.getTopic())))
                .toList();

        MentorSectionVO vo = new MentorSectionVO();
        vo.setStudentId(studentId);
        vo.setAcademicYear(year);
        vo.setSectionCode(MentorConstants.SECTION_CODE);
        vo.setSectionName(MentorConstants.SECTION_NAME);
        vo.setStatus(status);
        // 主表字段值恒为空：内容都在子表里，避免把「最新一条谈话」复制成第二份事实。
        vo.setFields(Map.of());
        vo.setFieldMeta(metadata);
        vo.setEditable(metadata.stream().anyMatch(FieldMetaVO::getEditable));
        vo.setNotes(timeline);
        vo.setCanAppend(user.isCounselor() && !ProfileStatusEnum.LOCKED.name().equals(status));
        vo.setCompleteness(profile == null ? BigDecimal.ZERO : completeness.compute(profile));
        vo.setSubmitCount(countOf(profile, true));
        vo.setWithdrawCount(countOf(profile, false));
        vo.setSubmittedAt(profile == null ? null : profile.getSubmittedAt());
        attachReviewTrace(vo, profile);
        return vo;
    }

    @Override
    public void save(Long studentId, MentorSectionSaveDTO dto) {
        String year = AcademicYearUtils.resolve(dto.getAcademicYear());
        CurrentUser user = UserContext.get();
        denyAdminContentEdit(user);
        classes.requireStudentScope(studentId, year, user);
        if (!dto.getFields().isEmpty()) {
            // 静默丢弃会让人以为保存成功、实际什么都没写；明确拒绝并指路。
            throw new BusinessException(ResultCode.PARAM_INVALID,
                    "导师寄语没有主表字段，请使用 POST /sections/mentor/{studentId}/notes 追加谈话记录");
        }
        // 空 fields 是幂等空操作：不建档、不写日志，保持六板块 PUT 端点结构一致。
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long studentId, String academicYear) {
        String year = AcademicYearUtils.resolve(academicYear);
        requireOwner(studentId);
        ArcProfile profile = locator.require(studentId, MentorConstants.SECTION_CODE, year,
                MentorConstants.SECTION_NAME);
        // 必填内容在子表，不能用 SectionFormService 的 arc_profile_item 必填校验。
        if (completeness.compute(profile).compareTo(FULL) < 0) {
            throw new BusinessException(ResultCode.REQUIRED_FIELD_MISSING,
                    "请至少追加一条完整谈话记录后再提交");
        }
        workflow.submit(profile, "学生提交");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void withdraw(Long studentId, String academicYear) {
        requireOwner(studentId);
        workflow.withdraw(locator.require(studentId, MentorConstants.SECTION_CODE,
                AcademicYearUtils.resolve(academicYear), MentorConstants.SECTION_NAME));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MentorNoteVO append(Long studentId, MentorNoteCreateDTO dto) {
        String year = AcademicYearUtils.resolve(dto.getAcademicYear());
        CurrentUser user = UserContext.get();
        if (!user.isCounselor()) {
            throw new BusinessException(ResultCode.PERMISSION_DENIED, "仅该班辅导员可追加谈话记录");
        }
        classes.requireStudentScope(studentId, year, user);
        ArcProfile profile = ensureProfile(studentId, year);
        if (ProfileStatusEnum.LOCKED.name().equals(profile.getStatus())) {
            throw new BusinessException(ResultCode.STATE_CONFLICT,
                    "板块已归档，仅可追加更正记录，暂未开放该入口");
        }

        Map<String, ArcFieldDef> defs = defMap();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("talkDate", dto.getTalkDate() == null ? null : dto.getTalkDate().toString());
        values.put("topic", dto.getTopic());
        values.put("advice", dto.getAdvice());
        values.put("starLevel", String.valueOf(dto.getStarLevel() == null ? 0 : dto.getStarLevel()));
        values.put("internalRemark", dto.getInternalRemark());
        // 学生反馈只能通过 feedback 端点写入，追加接口不接受它。
        validateValues(defs, values);

        Integer enrollmentYear = students.find(studentId).map(StuStudent::getEnrollmentYear).orElse(null);
        if (!MentorSectionUtils.isValidTalkDate(dto.getTalkDate(), enrollmentYear)) {
            throw new BusinessException(ResultCode.FIELD_VALIDATION_FAILED,
                    "谈话日期不晚于今天且不早于入学日期",
                    List.of(new FieldErrorDetail("mentor.talkDate", "TEXT_LEN",
                            "谈话日期不晚于今天且不早于入学日期")));
        }

        ArcMentorNote note = new ArcMentorNote();
        note.setProfileId(profile.getId());
        note.setStudentId(studentId);
        note.setAcademicYear(year);
        note.setTalkDate(dto.getTalkDate());
        note.setTopic(dto.getTopic().trim());
        note.setAdvice(dto.getAdvice().trim());
        note.setStarLevel(dto.getStarLevel() == null ? 0 : dto.getStarLevel());
        note.setInternalRemark(blankToNull(dto.getInternalRemark()));
        note.setCounselorId(user.userId());
        notes.insert(note);

        // 完成度按子表现算并写回（走扩展点），避免待审列表拿到过期的 0%。
        completeness.refresh(profile, Map.of());
        history.record(profile, MentorConstants.BIZ_MENTOR_APPEND, MentorConstants.FIELD_NOTE,
                null, MentorSectionUtils.noteSummary(note), null, null, "追加谈话记录");
        audit.record(MentorConstants.BIZ_MENTOR_APPEND, AuditLogService.TARGET_PROFILE,
                note.getId(), studentId, MentorConstants.SECTION_CODE, MentorConstants.FIELD_NOTE,
                MentorConstants.ACTION_APPEND, MentorSectionUtils.noteSummary(note));

        ArcFieldDef topicDef = defs.get("topic");
        return MentorSectionUtils.toView(note, true, false,
                MentorSectionUtils.topicLabelOf(topicDef, note.getTopic()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MentorNoteVO correct(Long studentId, com.college.growth.mentor.dto.MentorCorrectionDTO dto) {
        String year = AcademicYearUtils.resolve(dto.getAcademicYear());
        CurrentUser user = UserContext.get();
        if (!user.isCounselor()) {
            throw new BusinessException(ResultCode.PERMISSION_DENIED, "仅该班辅导员可追加更正记录");
        }
        classes.requireStudentScope(studentId, year, user);
        ArcProfile profile = locator.find(studentId, MentorConstants.SECTION_CODE, year);
        if (profile == null || !ProfileStatusEnum.LOCKED.name().equals(profile.getStatus())) {
            throw new BusinessException(ResultCode.STATE_CONFLICT, "只有已归档的 MENTOR 板块才能追加更正记录");
        }
        ArcMentorNote original = dto.getNoteId() == null ? null : notes.selectById(dto.getNoteId());
        if (original == null || !profile.getId().equals(original.getProfileId())) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "原谈话记录不存在");
        }
        ArcMentorNote note = new ArcMentorNote();
        note.setProfileId(profile.getId());
        note.setStudentId(studentId);
        note.setAcademicYear(year);
        note.setTalkDate(java.time.LocalDate.now());
        note.setTopic(original.getTopic());
        note.setAdvice(dto.getAdvice().trim());
        note.setStarLevel(original.getStarLevel() == null ? 0 : original.getStarLevel());
        note.setCounselorId(user.userId());
        note.setCorrectsNoteId(original.getId());
        note.setCorrectionReason(dto.getReason().trim());
        notes.insert(note);
        history.record(profile, MentorConstants.BIZ_MENTOR_APPEND, MentorConstants.FIELD_NOTE,
                null, "更正记录 #" + note.getId(), null, null, dto.getReason().trim());
        audit.record("ARCHIVE", AuditLogService.TARGET_PROFILE, profile.getId(), studentId,
                MentorConstants.SECTION_CODE, null, "CORRECT", "归档后更正：" + dto.getReason().trim());
        return MentorSectionUtils.toView(note, true, false, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MentorNoteVO feedback(Long studentId, Long noteId, MentorFeedbackDTO dto) {
        String year = AcademicYearUtils.resolve(dto.getAcademicYear());
        requireOwner(studentId);
        ArcProfile profile = locator.require(studentId, MentorConstants.SECTION_CODE, year,
                MentorConstants.SECTION_NAME);
        ArcMentorNote note = notes.selectById(noteId);
        if (note == null || !profile.getId().equals(note.getProfileId())
                || !studentId.equals(note.getStudentId())) {
            throw new BusinessException(ResultCode.DATA_SCOPE_DENIED, "无权对该谈话记录提交反馈");
        }
        if (note.getFeedbackAt() != null) {
            // 提交后锁定，不随板块重提解锁（需求 4.7）。
            throw new BusinessException(ResultCode.STATE_CONFLICT, "该条谈话已反馈，不可重复提交");
        }
        String feedback = dto.getFeedback() == null ? null : dto.getFeedback().trim();
        if (FieldValues.isEmpty(feedback)) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "反馈内容不能为空");
        }
        ArcFieldDef feedbackDef = defMap().get("feedback");
        if (feedbackDef != null) {
            Optional<String> error = validators.validate(feedbackDef, feedback);
            if (error.isPresent()) {
                throw new BusinessException(ResultCode.FIELD_VALIDATION_FAILED, error.get(),
                        List.of(new FieldErrorDetail(feedbackDef.getFieldCode(),
                                feedbackDef.getValidator(), error.get())));
            }
        }

        // 只 set 反馈相关三列：BR-S07 要求记录只追加不修改，用 updateById 会重写整行，
        // 一旦将来有人给别的列加了默认值就会把历史内容改掉。
        LocalDateTime now = LocalDateTime.now();
        notes.update(null, Wrappers.<ArcMentorNote>lambdaUpdate()
                .eq(ArcMentorNote::getId, noteId)
                .set(ArcMentorNote::getFeedback, feedback)
                .set(ArcMentorNote::getFeedbackAt, now)
                .set(ArcMentorNote::getUpdatedAt, now));

        history.record(profile, MentorConstants.BIZ_MENTOR_FEEDBACK, "mentor.feedback",
                null, "已提交反馈", null, null, "学生反馈");
        audit.record(MentorConstants.BIZ_MENTOR_FEEDBACK, AuditLogService.TARGET_PROFILE,
                noteId, studentId, MentorConstants.SECTION_CODE, "mentor.feedback",
                MentorConstants.ACTION_FEEDBACK, "学生反馈");

        ArcMentorNote saved = notes.selectById(noteId);
        ArcFieldDef topicDef = defMap().get("topic");
        return MentorSectionUtils.toView(saved, false, true,
                MentorSectionUtils.topicLabelOf(topicDef, saved.getTopic()));
    }

    /** 按字段短名索引启用字段定义，供逐字段校验复用。 */
    private Map<String, ArcFieldDef> defMap() {
        Map<String, ArcFieldDef> defs = new LinkedHashMap<>();
        for (ArcFieldDef def : definitions.listActive(MentorConstants.SECTION_CODE)) {
            defs.put(MentorSectionUtils.shortName(def), def);
        }
        return defs;
    }

    /**
     * 逐字段校验：类型/长度/枚举全部走 {@code arc_field_def.validator} 对应的校验器，
     * 与其余板块同一口径（BR-F02），不在本板块复制规则。
     */
    private void validateValues(Map<String, ArcFieldDef> defs, Map<String, Object> values) {
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            ArcFieldDef def = defs.get(entry.getKey());
            if (def == null) {
                throw new BusinessException(ResultCode.FIELD_VALIDATION_FAILED,
                        "字段不存在或已停用：" + entry.getKey());
            }
            Object value = entry.getValue();
            if (def.isRequiredFlag() && FieldValues.isEmpty(value)) {
                throw new BusinessException(ResultCode.REQUIRED_FIELD_MISSING,
                        "必填内容未填写：" + def.getFieldName(),
                        List.of(new FieldErrorDetail(def.getFieldCode(), "REQUIRED",
                                "必填内容未填写")));
            }
            Optional<String> error = validators.validate(def, value);
            if (error.isPresent()) {
                throw new BusinessException(ResultCode.FIELD_VALIDATION_FAILED, error.get(),
                        List.of(new FieldErrorDetail(def.getFieldCode(), def.getValidator(), error.get())));
            }
        }
    }

    /** 空白串归一为 NULL：库里不堆一堆空字符串，计数与导出也更好判空。 */
    private String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    /**
     * 取（或首次创建）MENTOR 板块实例。
     *
     * <p>{@code arc_profile} 仍保留 MENTOR 实例：它承载状态机、完成度、提交/撤回次数，
     * 是六板块统一信封与审核列表的锚点；只是不往 {@code arc_profile_item} 写字段值。</p>
     */
    private ArcProfile ensureProfile(Long studentId, String year) {
        ArcProfile profile = locator.find(studentId, MentorConstants.SECTION_CODE, year);
        if (profile != null) {
            return profile;
        }
        ArcProfile created = new ArcProfile();
        created.setStudentId(studentId);
        created.setSectionCode(MentorConstants.SECTION_CODE);
        created.setAcademicYear(year);
        created.setStatus(ProfileStatusEnum.DRAFT.name());
        created.setCompleted(false);
        created.setCompleteness(BigDecimal.ZERO);
        created.setSubmitCount(0);
        created.setWithdrawCount(0);
        created.setVersion(0);
        try {
            profiles.insert(created);
            return created;
        } catch (DuplicateKeyException ex) {
            ArcProfile concurrent = locator.find(studentId, MentorConstants.SECTION_CODE, year);
            if (concurrent == null) {
                throw new BusinessException(ResultCode.VERSION_CONFLICT);
            }
            return concurrent;
        }
    }

    private void requireOwner(Long studentId) {
        CurrentUser user = UserContext.get();
        if (!user.isStudent() || !studentId.equals(user.userId())) {
            throw new BusinessException(ResultCode.DATA_SCOPE_DENIED, "仅学生本人可操作");
        }
    }

    /** 管理员只配置权限，不写内容，与其余板块同一口径。 */
    private void denyAdminContentEdit(CurrentUser user) {
        if (user.role() == UserRoleEnum.ADMIN) {
            throw new BusinessException(ResultCode.PERMISSION_DENIED,
                    "管理员只负责配置字段权限，不能修改档案内容");
        }
    }

    private boolean hasRole(String roles, CurrentUser user) {
        return roles != null && Arrays.stream(roles.split(","))
                .anyMatch(role -> role.trim().equals(user.role().name()));
    }

    /** 无业务数据权限时的空视图：只有信封，没有字段、权限位与时间线。 */
    private MentorSectionVO emptyView(Long studentId, String year) {
        MentorSectionVO vo = new MentorSectionVO();
        vo.setStudentId(studentId);
        vo.setAcademicYear(year);
        vo.setSectionCode(MentorConstants.SECTION_CODE);
        vo.setSectionName(MentorConstants.SECTION_NAME);
        vo.setStatus(ProfileStatusEnum.DRAFT.name());
        vo.setFields(Map.of());
        vo.setFieldMeta(List.of());
        vo.setEditable(false);
        vo.setCompleteness(BigDecimal.ZERO);
        vo.setSubmitCount(0);
        vo.setWithdrawCount(0);
        vo.setNotes(List.of());
        vo.setCanAppend(false);
        return vo;
    }

    /** 仅在 RETURNED 时回填最近一次退回理由（BR-S04），与其余板块一致。 */
    private void attachReviewTrace(MentorSectionVO vo, ArcProfile profile) {
        if (profile == null || !ProfileStatusEnum.RETURNED.name().equals(vo.getStatus())) {
            return;
        }
        history.findLatestReturnNote(profile.getId()).ifPresent(note -> {
            vo.setReviewReason(note.getReason());
            vo.setReviewedAt(note.getCreatedAt());
        });
    }

    private int countOf(ArcProfile profile, boolean submit) {
        if (profile == null) {
            return 0;
        }
        Integer value = submit ? profile.getSubmitCount() : profile.getWithdrawCount();
        return value == null ? 0 : value;
    }
}
