package com.college.growth.basic.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.college.growth.basic.common.BasicConstants;
import com.college.growth.basic.dto.BasicSectionSaveDTO;
import com.college.growth.basic.service.BasicSectionService;
import com.college.growth.basic.util.BasicSectionUtils;
import com.college.growth.basic.vo.BasicSectionVO;
import com.college.growth.basic.vo.FieldMetaVO;
import com.college.growth.basic.vo.SensitiveFieldVO;
import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.context.UserContext;
import com.college.growth.common.enums.ProfileStatusEnum;
import com.college.growth.common.enums.UserRoleEnum;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.field.FieldSourceEnum;
import com.college.growth.common.field.FieldValues;
import com.college.growth.common.field.entity.ArcFieldDef;
import com.college.growth.common.field.service.FieldDefService;
import com.college.growth.common.field.validation.FieldValidatorRegistry;
import com.college.growth.common.log.service.AuditLogService;
import com.college.growth.common.log.service.ChangeLogService;
import com.college.growth.common.org.entity.OrgClass;
import com.college.growth.common.org.mapper.OrgClassMapper;
import com.college.growth.common.org.service.StudentClassService;
import com.college.growth.common.profile.FieldValueCodec;
import com.college.growth.common.profile.entity.ArcProfile;
import com.college.growth.common.profile.entity.ArcProfileItem;
import com.college.growth.common.profile.mapper.ArcProfileMapper;
import com.college.growth.common.profile.service.PendingFieldService;
import com.college.growth.common.profile.service.ProfileCompletenessService;
import com.college.growth.common.profile.service.ProfileItemService;
import com.college.growth.common.profile.service.ProfileLocator;
import com.college.growth.common.profile.vo.PendingFieldVO;
import com.college.growth.common.result.FieldErrorDetail;
import com.college.growth.common.result.ResultCode;
import com.college.growth.common.student.service.StudentSyncService;
import com.college.growth.common.util.AcademicYearUtils;
import com.college.growth.common.util.TextUtils;
import com.college.growth.review.service.ProfileWorkflowService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 板块一 基本信息（BASIC）业务实现。
 *
 * <p>本类只负责「板块级」的东西：取字段定义、算权限位、校验字段、写值、算完成度。
 * 状态流转交给公共审核模块的 {@link ProfileWorkflowService}，待确认区处理交给
 * {@link PendingFieldService}，日志交给 {@link ChangeLogService}——这三件事六个板块完全一样，
 * 放在板块里就必然出现六份实现。</p>
 *
 * <p>字段的类型、校验规则、枚举、必填、可编辑角色全部来自 {@code arc_field_def}，
 * 新增字段不需要改本类（BR-F02）。权限三层同时生效（BR-F01）：字段定义标记位 ×
 * 当前角色 × 板块状态，任一不通过即不可见或不可写。</p>
 *
 * <p><b>已知限制</b>：接入统一认证前，读接口与保存接口不校验「当前用户是否就是该学生」，
 * 数据范围依赖 {@code UserContextFilter} 注入的请求头；撤回、审核、待确认处理已强制校验
 * 本人/本班。接认证时必须把同样的校验补到读与写，否则任何调用方都能读写任意学生档案。</p>
 */
@Slf4j
@Service
public class BasicSectionServiceImpl implements BasicSectionService {

    /** 日志中字段值的最大留存长度，避免超长文本撑爆列。 */
    private static final int LOG_VALUE_MAX_LENGTH = 500;

    private final ArcProfileMapper profileMapper;

    private final ProfileLocator profileLocator;

    private final ProfileItemService itemService;

    private final OrgClassMapper orgClassMapper;

    private final FieldDefService fieldDefService;

    private final FieldValidatorRegistry validatorRegistry;

    private final ChangeLogService changeLogService;

    private final AuditLogService auditLogService;

    private final ProfileWorkflowService workflowService;

    private final PendingFieldService pendingFieldService;

    private final ProfileCompletenessService completenessService;

    private final StudentSyncService studentSyncService;

    private final StudentClassService studentClassService;

    public BasicSectionServiceImpl(ArcProfileMapper profileMapper,
                                   ProfileLocator profileLocator,
                                   ProfileItemService itemService,
                                   OrgClassMapper orgClassMapper,
                                   FieldDefService fieldDefService,
                                   FieldValidatorRegistry validatorRegistry,
                                   ChangeLogService changeLogService,
                                   AuditLogService auditLogService,
                                   ProfileWorkflowService workflowService,
                                   PendingFieldService pendingFieldService,
                                   ProfileCompletenessService completenessService,
                                   StudentSyncService studentSyncService,
                                   StudentClassService studentClassService) {
        this.profileMapper = profileMapper;
        this.profileLocator = profileLocator;
        this.itemService = itemService;
        this.orgClassMapper = orgClassMapper;
        this.fieldDefService = fieldDefService;
        this.validatorRegistry = validatorRegistry;
        this.changeLogService = changeLogService;
        this.auditLogService = auditLogService;
        this.workflowService = workflowService;
        this.pendingFieldService = pendingFieldService;
        this.completenessService = completenessService;
        this.studentSyncService = studentSyncService;
        this.studentClassService = studentClassService;
    }

    @Override
    public BasicSectionVO detail(Long studentId, String academicYear) {
        String year = AcademicYearUtils.resolve(academicYear);
        CurrentUser user = UserContext.get();
        if (user.canReadBusinessData()) {
            studentClassService.requireStudentScope(studentId, year, user);
        }
        List<ArcFieldDef> defs = fieldDefService.listActive(BasicConstants.SECTION_CODE);
        ArcProfile profile = profileLocator.find(studentId, BasicConstants.SECTION_CODE, year);
        Map<String, ArcProfileItem> items = itemService.loadByProfile(profile == null ? null : profile.getId());
        String status = profile == null ? ProfileStatusEnum.DRAFT.name() : profile.getStatus();

        Map<String, Object> values = new LinkedHashMap<>();
        List<FieldMetaVO> metas = new ArrayList<>();

        if (user.canReadBusinessData()) {
            for (ArcFieldDef def : defs) {
                if (!isVisible(def, user)) {
                    continue;
                }
                ArcProfileItem item = items.get(def.getFieldCode());
                Object value = FieldValueCodec.read(def, item);
                boolean masked = needMask(def, user);
                boolean editable = isEditable(def, user, status);
                metas.add(buildMeta(def, editable, masked, item));
                values.put(BasicSectionUtils.shortName(def.getFieldCode()),
                        masked ? BasicSectionUtils.maskValue(def.getFieldCode(), value) : value);
            }
            fillDerivedMajor(values);
        } else {
            // 管理员默认无业务数据权限：只返回空视图，接入临时授权后才放行（需求文档 2.1）。
            log.debug("角色 {} 无业务数据权限，返回空字段视图", user.role());
        }

        BasicSectionVO vo = new BasicSectionVO();
        vo.setStudentId(studentId);
        vo.setAcademicYear(year);
        vo.setSectionCode(BasicConstants.SECTION_CODE);
        vo.setSectionName(BasicConstants.SECTION_NAME);
        vo.setStatus(status);
        vo.setFields(values);
        vo.setFieldMeta(metas);
        vo.setEditable(metas.stream().anyMatch(meta -> Boolean.TRUE.equals(meta.getEditable())));
        // 完成度按当前字段值现算：学工系统（SIS）导入字段值不经过保存接口，
        // 若只读 arc_profile.completeness，导完之后学生与辅导员看到的百分比都是过期值。
        // 复用上面已加载的 items，不额外多查一次 arc_profile_item。
        vo.setCompleteness(completenessService.compute(BasicConstants.SECTION_CODE, items));
        vo.setSubmitCount(profile == null || profile.getSubmitCount() == null ? 0 : profile.getSubmitCount());
        vo.setWithdrawCount(profile == null || profile.getWithdrawCount() == null ? 0 : profile.getWithdrawCount());
        vo.setSubmittedAt(profile == null ? null : profile.getSubmittedAt());
        attachReviewTrace(vo, profile);
        return vo;
    }

    /**
     * 回填最近一次退回理由（BR-S04）。
     *
     * <p>只在「已退回」状态返回：退回后学生保存草稿不会改变状态，理由照常可见；
     * 一旦重新提交就不再返回。DRAFT 不返回是为了避开「退回 → 重新提交 → 撤回」这条路径——
     * 那时状态回到 DRAFT，但最近一条审核结论已经过期，展示出来反而误导。</p>
     */
    private void attachReviewTrace(BasicSectionVO vo, ArcProfile profile) {
        if (profile == null || !ProfileStatusEnum.RETURNED.name().equals(vo.getStatus())) {
            return;
        }
        changeLogService.findLatestReturnNote(profile.getId()).ifPresent(note -> {
            vo.setReviewReason(note.getReason());
            vo.setReviewedAt(note.getCreatedAt());
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(Long studentId, BasicSectionSaveDTO dto) {
        CurrentUser user = UserContext.get();
        // 管理员只配置字段权限，不参与内容编辑：即使持临时授权，也不能借保存接口改学生档案。
        // 字段级 editable 位同样会把 ADMIN 判为不可写，这里提前拒绝是为了给出明确原因（EC-40303）。
        denyAdminContentEdit(user);
        String year = AcademicYearUtils.resolve(dto.getAcademicYear());
        studentClassService.requireStudentScope(studentId, year, user);
        Map<String, Object> submitted = dto.getFields();
        if (submitted == null || submitted.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "没有需要保存的字段");
        }
        if (submitted.size() > BasicConstants.MAX_FIELDS_PER_SAVE) {
            throw new BusinessException(ResultCode.PARAM_INVALID,
                    "单次保存字段数不能超过 " + BasicConstants.MAX_FIELDS_PER_SAVE);
        }

        List<ArcFieldDef> defs = fieldDefService.listActive(BasicConstants.SECTION_CODE);
        Map<String, ArcFieldDef> defByKey = defs.stream()
                .collect(Collectors.toMap(def -> BasicSectionUtils.shortName(def.getFieldCode()), def -> def));

        ArcProfile profile = profileLocator.find(studentId, BasicConstants.SECTION_CODE, year);
        if (profile == null) {
            profile = createProfile(studentId, year);
        }
        assertWritable(profile);

        Map<String, ArcProfileItem> items = itemService.loadByProfile(profile.getId());
        // SIS 字段（姓名、学号、班级等）任何角色都写不进来，值只来自学生主数据。
        // 建档与保存是学生能触发的第一个写节点，在这里补齐空值，
        // 学生才可能仅凭填写自己的字段就通过提交校验。
        studentSyncService.fillAbsent(profile, defs, items);

        // 先全量校验，再统一写入：避免「前几个字段已落库、后一个字段报错」留下半成品。
        // 权限问题单独收集：越权写与格式错误是两类问题，前者必须是 403（BR-F01）。
        List<FieldErrorDetail> permissionErrors = new ArrayList<>();
        List<FieldErrorDetail> errors = new ArrayList<>();
        Map<String, ArcFieldDef> accepted = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : submitted.entrySet()) {
            String key = entry.getKey();
            ArcFieldDef def = defByKey.get(key);
            if (def == null) {
                errors.add(new FieldErrorDetail(key, "UNKNOWN_FIELD", "字段不存在或已停用"));
                continue;
            }
            if (!isEditable(def, user, profile.getStatus())) {
                permissionErrors.add(new FieldErrorDetail(def.getFieldCode(), "NOT_EDITABLE",
                        "该字段由学校维护，无法修改"));
                continue;
            }
            Optional<String> message = validatorRegistry.validate(def, entry.getValue());
            if (message.isPresent()) {
                errors.add(new FieldErrorDetail(def.getFieldCode(), def.getValidator(), message.get()));
                continue;
            }
            accepted.put(key, def);
        }
        if (!permissionErrors.isEmpty()) {
            throw new BusinessException(ResultCode.FIELD_NOT_EDITABLE,
                    "存在由学校维护的字段，无法修改", permissionErrors);
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(ResultCode.FIELD_VALIDATION_FAILED, "字段校验未通过", errors);
        }

        // items 上面已加载并含同步结果，这里直接复用，不重复查库。
        for (Map.Entry<String, ArcFieldDef> entry : accepted.entrySet()) {
            applyFieldSave(profile, entry.getValue(), submitted.get(entry.getKey()), items, user);
        }
        completenessService.refresh(profile, items);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long studentId, String academicYear) {
        denyAdminContentEdit(UserContext.get());
        String year = AcademicYearUtils.resolve(academicYear);
        CurrentUser user = UserContext.get();
        if (!user.isStudent() || !studentId.equals(user.userId())) {
            throw new BusinessException(ResultCode.DATA_SCOPE_DENIED, "仅学生本人可提交");
        }
        List<ArcFieldDef> defs = fieldDefService.listActive(BasicConstants.SECTION_CODE);
        ArcProfile profile = profileLocator.find(studentId, BasicConstants.SECTION_CODE, year);
        if (profile == null) {
            throw new BusinessException(ResultCode.REQUIRED_FIELD_MISSING,
                    "档案尚未填写，无法提交", requiredDetails(defs));
        }
        assertWritable(profile);

        Map<String, ArcProfileItem> items = itemService.loadByProfile(profile.getId());
        // 提交前再补一次空值：从建档到提交之间主数据可能刚同步进来（学籍补录、转专业），
        // 不补就会出现「学校明明有数据、学生却被判必填缺失」。
        studentSyncService.fillAbsent(profile, defs, items);
        List<FieldErrorDetail> missing = new ArrayList<>();
        List<FieldErrorDetail> invalid = new ArrayList<>();
        for (ArcFieldDef def : defs) {
            if (isDerived(def)) {
                // 专业由班级推导，不落 arc_profile_item，也不应因「未填写」被判为缺失。
                continue;
            }
            Object value = effectiveValue(def, items.get(def.getFieldCode()));
            if (FieldValues.isEmpty(value)) {
                if (def.isRequiredFlag()) {
                    missing.add(new FieldErrorDetail(def.getFieldCode(), "REQUIRED", "必填内容未填写"));
                }
                continue;
            }
            validatorRegistry.validate(def, value).ifPresent(message ->
                    invalid.add(new FieldErrorDetail(def.getFieldCode(), def.getValidator(), message)));
        }
        if (!missing.isEmpty() || !invalid.isEmpty()) {
            List<FieldErrorDetail> details = new ArrayList<>(missing);
            details.addAll(invalid);
            throw new BusinessException(ResultCode.REQUIRED_FIELD_MISSING,
                    "还有 " + details.size() + " 项未通过校验，请修正后重新提交", details);
        }

        // 状态流转、提交次数、双日志由公共审核模块统一处理（S-1-05 / BR-S05 / S-1-12）。
        workflowService.submit(profile, "学生提交");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void withdraw(Long studentId, String academicYear) {
        String year = AcademicYearUtils.resolve(academicYear);
        ArcProfile profile = profileLocator.require(studentId, BasicConstants.SECTION_CODE, year,
                BasicConstants.SECTION_NAME);
        workflowService.withdraw(profile);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PendingFieldVO confirmField(Long studentId, String fieldCode, String academicYear) {
        return pendingFieldService.confirm(studentId, BasicConstants.SECTION_CODE, fieldCode, academicYear);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectField(Long studentId, String fieldCode, String academicYear) {
        pendingFieldService.reject(studentId, BasicConstants.SECTION_CODE, fieldCode, academicYear);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SensitiveFieldVO reveal(Long studentId, String fieldCode, String academicYear) {
        CurrentUser user = UserContext.get();
        if (!user.isCounselor() && !user.isAdminWithGrant()) {
            throw new BusinessException(ResultCode.DATA_SCOPE_DENIED, "无权查看该字段的完整内容");
        }
        String year = AcademicYearUtils.resolve(academicYear);
        studentClassService.requireStudentScope(studentId, year, user);
        ArcFieldDef def = fieldDefService.getByCode(fieldCode);
        if (def == null || !BasicConstants.SECTION_CODE.equals(def.getSectionCode())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "字段不存在");
        }
        if (def.getSensitiveLevel() == null || def.getSensitiveLevel() == 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "该字段不是敏感字段");
        }

        ArcProfile profile = profileLocator.require(studentId, BasicConstants.SECTION_CODE, year,
                BasicConstants.SECTION_NAME);
        ArcProfileItem item = itemService.find(profile.getId(), fieldCode);
        Object value = FieldValueCodec.read(def, item);
        if (FieldValues.isEmpty(value)) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "该字段尚未填写");
        }

        // 审计不写明文：只记录「谁在什么时候看了哪个字段」，变更日志与审计日志各一条（S-1-12）。
        String note = "查看敏感字段明文：" + def.getFieldName();
        changeLogService.record(profile, ChangeLogService.BIZ_REVIEW, fieldCode, null, null, null, null, note);
        auditLogService.record(AuditLogService.BIZ_SENSITIVE_VIEW, AuditLogService.TARGET_PROFILE,
                profile.getId(), studentId, profile.getSectionCode(), fieldCode, "REVEAL", note);
        return new SensitiveFieldVO(def.getFieldCode(), def.getFieldName(), FieldValues.asText(value));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int syncStudent(Long studentId, String academicYear) {
        CurrentUser user = UserContext.get();
        // 同步会覆盖字段值，属于内容修改；管理员即使持临时授权也只能看，不能触发同步。
        denyAdminContentEdit(user);
        String year = AcademicYearUtils.resolve(academicYear);
        // 与待确认区同一套数据范围：本班现任辅导员，或持临时授权的管理员。
        if (!studentClassService.isCounselorOf(studentId, year, user.userId()) && !user.isAdminWithGrant()) {
            throw new BusinessException(ResultCode.DATA_SCOPE_DENIED, "无权同步该学生的基础信息");
        }
        ArcProfile profile = profileLocator.require(studentId, BasicConstants.SECTION_CODE, year,
                BasicConstants.SECTION_NAME);
        // 已通过/锁定的档案不能被一次同步悄悄改掉内容，否则审核结论就失去了依据。
        assertWritable(profile);

        List<ArcFieldDef> defs = fieldDefService.listActive(BasicConstants.SECTION_CODE);
        Map<String, ArcProfileItem> items = itemService.loadByProfile(profile.getId());
        int written = studentSyncService.overwrite(profile, defs, items);
        completenessService.refresh(profile, items);
        auditLogService.record(AuditLogService.BIZ_IMPORT, AuditLogService.TARGET_PROFILE,
                profile.getId(), studentId, profile.getSectionCode(), null,
                AuditLogService.ACTION_SYNC, "学工系统数据重新同步，更新 " + written + " 个字段");
        return written;
    }

    // ------------------------------------------------------------------
    // 写入
    // ------------------------------------------------------------------

    /**
     * 写入单个字段。
     *
     * <p>学生填写的待确认字段只写候选区，正式值由辅导员确认后更新。</p>
     */
    private void applyFieldSave(ArcProfile profile, ArcFieldDef def, Object value,
                                Map<String, ArcProfileItem> items, CurrentUser user) {
        ArcProfileItem item = items.get(def.getFieldCode());
        boolean newItem = item == null;
        if (newItem) {
            item = new ArcProfileItem();
            item.setProfileId(profile.getId());
            item.setFieldCode(def.getFieldCode());
            item.setConfirmStatus(BasicConstants.CONFIRM_NONE);
        }

        Object oldValue = FieldValueCodec.read(def, item);
        FieldSourceEnum source = FieldSourceEnum.fromCode(def.getSource());
        if (source.requiresConfirmation() && user.isStudent()) {
            if (!item.hasPending() && FieldValueCodec.same(def, oldValue, value)) {
                return;
            }
            FieldValueCodec.writePending(item, def, value);
            item.setConfirmStatus(BasicConstants.CONFIRM_PENDING);
            item.setConfirmedAt(null);
            item.setConfirmedBy(null);
        } else {
            FieldValueCodec.write(item, def, value);
            FieldValueCodec.clearPending(item);
            boolean confirmed = source.requiresConfirmation();
            item.setConfirmStatus(confirmed ? BasicConstants.CONFIRM_APPROVED : BasicConstants.CONFIRM_NONE);
            if (confirmed) {
                item.setConfirmedBy(user.userId());
                item.setConfirmedAt(java.time.LocalDateTime.now());
            }
        }
        item.setUpdatedBy(user.userId());
        itemService.save(item);
        if (newItem) {
            items.put(item.getFieldCode(), item);
        }
        changeLogService.record(profile, ChangeLogService.BIZ_FIELD_EDIT, def.getFieldCode(),
                describeValue(def, oldValue), describeValue(def, value), null, null, null);
    }

    private ArcProfile createProfile(Long studentId, String year) {
        ArcProfile profile = new ArcProfile();
        profile.setStudentId(studentId);
        profile.setAcademicYear(year);
        profile.setSectionCode(BasicConstants.SECTION_CODE);
        profile.setStatus(ProfileStatusEnum.DRAFT.name());
        profile.setCompleted(false);
        profile.setCompleteness(BigDecimal.ZERO);
        profile.setSubmitCount(0);
        profile.setWithdrawCount(0);
        profile.setVersion(0);
        profileMapper.insert(profile);
        return profile;
    }

    // ------------------------------------------------------------------
    // 读取与视图
    // ------------------------------------------------------------------
    private List<FieldErrorDetail> requiredDetails(List<ArcFieldDef> defs) {
        return defs.stream()
                .filter(ArcFieldDef::isRequiredFlag)
                .filter(def -> !isDerived(def))
                .map(def -> new FieldErrorDetail(def.getFieldCode(), "REQUIRED", "必填内容未填写"))
                .toList();
    }

    private FieldMetaVO buildMeta(ArcFieldDef def, boolean editable, boolean masked, ArcProfileItem item) {
        FieldMetaVO meta = FieldMetaVO.from(def, editable, masked);
        boolean pending = item != null && item.hasPending();
        meta.setPendingConfirm(pending);
        if (pending) {
            Object pendingValue = FieldValueCodec.readPending(def, item);
            meta.setPendingValue(masked
                    ? BasicSectionUtils.maskValue(def.getFieldCode(), pendingValue) : pendingValue);
        }
        return meta;
    }

    /** 专业由班级推导：用 org_class 的 major_name 覆盖，避免与学生提交值冲突。 */
    private void fillDerivedMajor(Map<String, Object> values) {
        Object classValue = values.get(BasicConstants.SHORT_CLASS);
        if (FieldValues.isEmpty(classValue)) {
            return;
        }
        OrgClass orgClass = findOrgClass(FieldValues.asText(classValue));
        if (orgClass != null) {
            values.put(BasicConstants.SHORT_MAJOR, orgClass.getMajorName());
        }
    }

    private OrgClass findOrgClass(String classValue) {
        if (TextUtils.isBlank(classValue)) {
            return null;
        }
        return orgClassMapper.selectOne(Wrappers.<OrgClass>lambdaQuery()
                .eq(OrgClass::getStatus, 1)
                .and(wrapper -> wrapper.eq(OrgClass::getClassCode, classValue)
                        .or()
                        .eq(OrgClass::getClassName, classValue))
                .last("limit 1"));
    }

    private boolean isVisible(ArcFieldDef def, CurrentUser user) {
        // 管理员默认不在 visible_roles 中；只有持临时授权（≤4 小时）时才按只读通道放行。
        return containsRole(def.getVisibleRoles(), user.role()) || user.isAdminWithGrant();
    }

    private boolean isEditable(ArcFieldDef def, CurrentUser user, String currentStatus) {
        // 管理员只负责配置权限，不参与内容编辑；即使有人手工把 ADMIN 写进 editable_roles，
        // 这里也必须拒绝，避免绕过权限配置页直接改档案。
        if (user.role() == UserRoleEnum.ADMIN) {
            return false;
        }
        if (!def.isEnabled() || !isWritableStatus(currentStatus)) {
            return false;
        }
        // 看不见的字段不允许写：权限配置可能被误设，保存侧必须与展示侧同口径。
        return isVisible(def, user) && containsRole(def.getEditableRoles(), user.role());
    }

    /** 管理员调用内容写接口时给出明确业务码，而不是笼统的「字段由学校维护」。 */
    private void denyAdminContentEdit(CurrentUser user) {
        if (user.role() == UserRoleEnum.ADMIN) {
            throw new BusinessException(ResultCode.PERMISSION_DENIED,
                    "管理员只负责配置字段权限，不能修改档案内容");
        }
    }

    private boolean isWritableStatus(String status) {
        try {
            return ProfileStatusEnum.valueOf(status).isWritable();
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private boolean containsRole(String roles, UserRoleEnum role) {
        if (TextUtils.isBlank(roles) || role == null) {
            return false;
        }
        return Arrays.stream(roles.split(","))
                .map(String::trim)
                .anyMatch(item -> item.equalsIgnoreCase(role.name()));
    }

    private boolean needMask(ArcFieldDef def, CurrentUser user) {
        int sensitive = def.getSensitiveLevel() == null ? 0 : def.getSensitiveLevel();
        if (sensitive >= 2) {
            return true;
        }
        return sensitive == 1 && user.isStudent();
    }

    private boolean isDerived(ArcFieldDef def) {
        return BasicConstants.VALIDATOR_DERIVED.equals(def.getValidator());
    }

    // ------------------------------------------------------------------
    // 值处理
    // ------------------------------------------------------------------

    /** 有效值：正式值为空时取待确认值，供必填统计与提交校验使用。 */
    private Object effectiveValue(ArcFieldDef def, ArcProfileItem item) {
        Object value = FieldValueCodec.read(def, item);
        if (!FieldValues.isEmpty(value)) {
            return value;
        }
        return FieldValueCodec.readPending(def, item);
    }

    /** 日志值：敏感字段只写脱敏值，超长截断。 */
    private String describeValue(ArcFieldDef def, Object value) {
        if (FieldValues.isEmpty(value)) {
            return null;
        }
        Object display = value;
        if (def.getSensitiveLevel() != null && def.getSensitiveLevel() > 0) {
            display = BasicSectionUtils.maskValue(def.getFieldCode(), value);
        }
        String text = display instanceof String textValue ? textValue : FieldValueCodec.toJson(display);
        return FieldValues.truncate(text, LOG_VALUE_MAX_LENGTH);
    }

    private void assertWritable(ArcProfile profile) {
        ProfileStatusEnum status = ProfileStatusEnum.valueOf(profile.getStatus());
        if (!status.isWritable()) {
            throw new BusinessException(ResultCode.STATE_CONFLICT,
                    "当前状态为「" + status.getLabel() + "」，不能修改内容");
        }
    }
}
