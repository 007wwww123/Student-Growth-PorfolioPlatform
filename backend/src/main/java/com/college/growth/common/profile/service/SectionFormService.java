package com.college.growth.common.profile.service;

import com.college.growth.academic.util.AcademicSectionUtils;
import com.college.growth.interest.util.InterestSectionUtils;
import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.context.UserContext;
import com.college.growth.common.enums.ProfileStatusEnum;
import com.college.growth.common.enums.SectionCodeEnum;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.field.FieldValues;
import com.college.growth.common.field.entity.ArcFieldDef;
import com.college.growth.common.field.service.FieldDefService;
import com.college.growth.common.field.validation.FieldValidatorRegistry;
import com.college.growth.common.log.service.ChangeLogService;
import com.college.growth.common.org.service.StudentClassService;
import com.college.growth.common.profile.FieldValueCodec;
import com.college.growth.common.profile.entity.ArcProfile;
import com.college.growth.common.profile.entity.ArcProfileItem;
import com.college.growth.common.profile.mapper.ArcProfileMapper;
import com.college.growth.common.result.FieldErrorDetail;
import com.college.growth.common.result.ResultCode;
import com.college.growth.common.util.AcademicYearUtils;
import com.college.growth.review.service.ProfileWorkflowService;
import java.util.Arrays;
import java.util.ArrayList;
import com.college.growth.basic.vo.FieldMetaVO;
import com.college.growth.common.field.SensitiveMasker;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 学业发展与兴趣特长的字段表单，共用档案、字段定义和审核状态机。 */
@Service
@RequiredArgsConstructor
public class SectionFormService {
    private final ProfileLocator locator;
    private final ArcProfileMapper profiles;
    private final ProfileItemService itemService;
    private final FieldDefService definitions;
    private final FieldValidatorRegistry validators;
    private final ProfileWorkflowService workflow;
    private final StudentClassService classes;
    private final ChangeLogService history;
    private final ProfileCompletenessService completeness;

    @Transactional(readOnly = true)
    public SectionFormView detail(Long studentId, String academicYear, SectionCodeEnum section) {
        String year = AcademicYearUtils.resolve(academicYear);
        CurrentUser user = UserContext.get();
        if (!user.canReadBusinessData()) {
            return new SectionFormView("DRAFT", Map.of(), false, List.of());
        }
        classes.requireStudentScope(studentId, year, user);
        ArcProfile profile = locator.find(studentId, section.name(), year);
        var items = itemService.loadByProfile(profile == null ? null : profile.getId());
        String status = profile == null ? "DRAFT" : profile.getStatus();
        Map<String, Object> fields = new LinkedHashMap<>();
        var metadata = new ArrayList<FieldMetaVO>();
        boolean writable = ProfileStatusEnum.valueOf(status).isWritable();
        for (var def : definitions.listActive(section.name())) {
            if (user.isAdminWithGrant() || hasRole(def.getVisibleRoles(), user)) {
                int level = def.getSensitiveLevel() == null ? 0 : def.getSensitiveLevel();
                boolean masked = level >= 2 || level == 1 && user.isStudent();
                Object value = FieldValueCodec.read(def, items.get(def.getFieldCode()));
                fields.put(shortName(def), masked ? SensitiveMasker.maskValue(def.getFieldCode(), value) : value);
                metadata.add(FieldMetaVO.from(def, writable && !user.isAdminWithGrant()
                        && hasRole(def.getEditableRoles(), user), masked));
            }
        }
        return new SectionFormView(status, fields, metadata.stream().anyMatch(FieldMetaVO::getEditable), metadata);
    }

    @Transactional
    public void save(Long studentId, String academicYear, SectionCodeEnum section, Map<String, Object> fields) {
        String year = AcademicYearUtils.resolve(academicYear);
        CurrentUser user = UserContext.get();
        classes.requireStudentScope(studentId, year, user);
        if (fields.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "没有需要保存的字段");
        }
        ArcProfile profile = locator.find(studentId, section.name(), year);
        var items = itemService.loadByProfile(profile == null ? null : profile.getId());
        if (profile != null && !ProfileStatusEnum.valueOf(profile.getStatus()).isWritable()) {
            throw new BusinessException(ResultCode.STATE_CONFLICT, "当前状态不能修改内容");
        }
        Map<String, ArcFieldDef> defs = new LinkedHashMap<>();
        Map<String, Object> values = new LinkedHashMap<>();
        for (var def : definitions.listActive(section.name())) {
            defs.put(shortName(def), def);
            values.put(shortName(def), FieldValueCodec.read(def, items.get(def.getFieldCode())));
        }
        for (var entry : fields.entrySet()) {
            var def = defs.get(entry.getKey());
            if (def == null) {
                throw new BusinessException(ResultCode.FIELD_VALIDATION_FAILED, "字段不存在或已停用：" + entry.getKey());
            }
            if (user.isAdminWithGrant() || !hasRole(def.getEditableRoles(), user)) {
                throw new BusinessException(ResultCode.FIELD_NOT_EDITABLE, "无权修改：" + def.getFieldName());
            }
            Object value = entry.getValue();
            if (value != null && !(value instanceof String) && !(value instanceof List<?>)) {
                throw new BusinessException(ResultCode.FIELD_VALIDATION_FAILED, "字段值类型错误：" + def.getFieldName());
            }
            boolean listField = "STRING_LIST".equals(def.getValidator());
            if (value != null && listField != (value instanceof List<?>)) {
                throw new BusinessException(ResultCode.FIELD_VALIDATION_FAILED, "字段值类型错误：" + def.getFieldName());
            }
            validate(def, value instanceof String ? FieldValueCodec.toText(value) : value);
        }
        values.putAll(fields);
        validateForm(section, values);
        if (profile == null) {
            profile = new ArcProfile();
            profile.setStudentId(studentId);
            profile.setSectionCode(section.name());
            profile.setAcademicYear(year);
            profile.setStatus("DRAFT");
            profile.setVersion(0);
            try {
                profiles.insert(profile);
            } catch (DuplicateKeyException ex) {
                throw new BusinessException(ResultCode.VERSION_CONFLICT);
            }
        }
        for (var entry : fields.entrySet()) {
            var def = defs.get(entry.getKey());
            var item = items.computeIfAbsent(def.getFieldCode(), key -> new ArcProfileItem());
            var before = FieldValueCodec.read(def, item);
            if (item.getId() == null) {
                item.setConfirmStatus("NONE");
            }
            item.setProfileId(profile.getId());
            item.setFieldCode(def.getFieldCode());
            item.setUpdatedBy(user.userId());
            FieldValueCodec.write(item, def, entry.getValue());
            itemService.save(item);
            history.record(profile, ChangeLogService.BIZ_FIELD_EDIT, def.getFieldCode(),
                    logValue(def, before), logValue(def, FieldValueCodec.read(def, item)), null, null, null);
        }
        completeness.refresh(profile, items);
    }

    @Transactional
    public void submit(Long studentId, String academicYear, SectionCodeEnum section) {
        String year = AcademicYearUtils.resolve(academicYear);
        requireOwner(studentId);
        ArcProfile profile = locator.find(studentId, section.name(), year);
        if (profile == null) {
            throw new BusinessException(ResultCode.REQUIRED_FIELD_MISSING, "档案尚未填写");
        }
        var items = itemService.loadByProfile(profile.getId());
        Map<String, Object> values = new LinkedHashMap<>();
        var defs = definitions.listActive(section.name());
        if (defs.isEmpty()) {
            throw new BusinessException(ResultCode.STATE_CONFLICT, "板块尚未配置字段");
        }
        for (var def : defs) {
            var value = FieldValueCodec.read(def, items.get(def.getFieldCode()));
            if (def.isRequired() && FieldValues.isEmpty(value)) {
                throw new BusinessException(ResultCode.REQUIRED_FIELD_MISSING, "必填内容未填写",
                        List.of(new FieldErrorDetail(def.getFieldCode(), "REQUIRED", "必填内容未填写")));
            }
            validate(def, value);
            values.put(shortName(def), value);
        }
        validateForm(section, values);
        workflow.submit(profile, "学生提交");
    }

    @Transactional
    public void withdraw(Long studentId, String year, SectionCodeEnum section) {
        requireOwner(studentId);
        workflow.withdraw(locator.require(studentId, section.name(), AcademicYearUtils.resolve(year), section.getLabel()));
    }

    private void requireOwner(Long studentId) {
        var user = UserContext.get();
        if (!user.isStudent() || !studentId.equals(user.userId())) {
            throw new BusinessException(ResultCode.DATA_SCOPE_DENIED, "仅学生本人可操作");
        }
    }

    private String shortName(ArcFieldDef def) {
        return def.getFieldCode().substring(def.getFieldCode().indexOf('.') + 1);
    }

    private String logValue(ArcFieldDef def, Object value) {
        if (value != null && def.getSensitiveLevel() != null && def.getSensitiveLevel() > 0) {
            return "***";
        }
        return value == null ? null : FieldValues.truncate(value instanceof String s ? s : FieldValueCodec.toJson(value), 500);
    }

    private boolean hasRole(String roles, CurrentUser user) {
        return roles != null && Arrays.stream(roles.split(",")).anyMatch(s -> s.trim().equals(user.role().name()));
    }

    private void validate(ArcFieldDef def, Object value) {
        validators.validate(def, value).ifPresent(message -> {
            throw new BusinessException(ResultCode.FIELD_VALIDATION_FAILED, message,
                    List.of(new FieldErrorDetail(def.getFieldCode(), def.getValidator(), message)));
        });
    }

    private void validateForm(SectionCodeEnum section, Map<String, Object> values) {
        switch (section) {
            case ACADEMIC -> AcademicSectionUtils.validateFields(values);
            case INTEREST -> InterestSectionUtils.validateFields(values);
            default -> throw new IllegalArgumentException("不支持的表单：" + section);
        }
    }
}
