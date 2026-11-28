package com.college.growth.config.field.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.context.UserContext;
import com.college.growth.common.enums.SectionCodeEnum;
import com.college.growth.common.enums.UserRoleEnum;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.field.FieldOption;
import com.college.growth.common.field.FieldSourceEnum;
import com.college.growth.common.field.FieldTypeEnum;
import com.college.growth.common.field.entity.ArcFieldDef;
import com.college.growth.common.field.mapper.ArcFieldDefMapper;
import com.college.growth.common.field.validation.FieldValidatorRegistry;
import com.college.growth.common.log.service.AuditLogService;
import com.college.growth.common.profile.entity.ArcProfileItem;
import com.college.growth.common.profile.mapper.ArcProfileItemMapper;
import com.college.growth.common.result.ResultCode;
import com.college.growth.common.util.TextUtils;
import com.college.growth.config.field.dto.FieldDefSaveDTO;
import com.college.growth.config.field.service.FieldDefAdminService;
import com.college.growth.config.field.vo.FieldConfigMetaVO;
import com.college.growth.config.field.vo.FieldDefVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 字段定义配置实现（F-04 / AC-007）。
 *
 * <p>字段定义是表单渲染、校验、可见/可编辑与必填统计的唯一来源，因此这里只改
 * {@code arc_field_def}，不缓存副本；保存后下一次请求即生效（AC-007）。</p>
 *
 * <p>安全边界：</p>
 * <ol>
 *   <li>字段编码与板块创建后不可改，避免历史值失联；</li>
 *   <li>已有字段值时不允许改类型，避免 {@code value_text}/{@code value_json} 口径错位；</li>
 *   <li>系统维护字段（SIS/SYS/DERIVED）不可停用，SYS/DERIVED 不可设为必填。</li>
 * </ol>
 */
@Service
public class FieldDefAdminServiceImpl implements FieldDefAdminService {

    private static final Logger log = LoggerFactory.getLogger(FieldDefAdminServiceImpl.class);

    private static final Set<String> ENUM_TYPES = Set.of("SELECT", "MULTI_SELECT", "RADIO", "CHECKBOX");

    private static final Set<String> ROLES = Set.of("STU", "COU");

    private final ArcFieldDefMapper fieldDefMapper;

    private final ArcProfileItemMapper itemMapper;

    private final FieldValidatorRegistry validatorRegistry;

    private final ObjectMapper objectMapper;

    private final AuditLogService auditLogService;

    public FieldDefAdminServiceImpl(ArcFieldDefMapper fieldDefMapper, ArcProfileItemMapper itemMapper,
                                    FieldValidatorRegistry validatorRegistry, ObjectMapper objectMapper,
                                    AuditLogService auditLogService) {
        this.fieldDefMapper = fieldDefMapper;
        this.itemMapper = itemMapper;
        this.validatorRegistry = validatorRegistry;
        this.objectMapper = objectMapper;
        this.auditLogService = auditLogService;
    }

    @Override
    public List<FieldDefVO> list(String sectionCode) {
        requireAdmin();
        SectionCodeEnum section = TextUtils.isBlank(sectionCode) ? null
                : SectionCodeEnum.tryFromCode(sectionCode);
        if (!TextUtils.isBlank(sectionCode) && section == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "未知板块编码：" + sectionCode);
        }
        List<ArcFieldDef> defs = fieldDefMapper.selectList(Wrappers.<ArcFieldDef>lambdaQuery()
                .eq(section != null, ArcFieldDef::getSectionCode, section == null ? null : section.name())
                .orderByAsc(ArcFieldDef::getSectionCode)
                .orderByAsc(ArcFieldDef::getSortNo));
        Set<String> withValues = fieldCodesWithValues(defs);
        return defs.stream().map(def -> toVO(def, withValues.contains(def.getFieldCode())))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FieldDefVO create(FieldDefSaveDTO dto) {
        requireAdmin();
        SectionCodeEnum section = requireSection(dto.getSectionCode());
        String fieldCode = requireNewFieldCode(section, dto.getFieldCode());
        if (findByCode(fieldCode) != null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "字段编码已存在：" + fieldCode);
        }
        ArcFieldDef def = new ArcFieldDef();
        def.setSectionCode(section.name());
        def.setFieldCode(fieldCode);
        apply(def, dto, true);
        def.setDefaultEditableRoles(def.getEditableRoles());
        def.setStatus(1);
        fieldDefMapper.insert(def);
        audit(def, AuditLogService.ACTION_CREATE, "新增字段：" + def.getFieldCode());
        log.info("新增字段定义 id={} code={} section={}", def.getId(), fieldCode, section);
        return toVO(def, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FieldDefVO update(Long id, FieldDefSaveDTO dto) {
        requireAdmin();
        ArcFieldDef def = requireField(id);
        if (!TextUtils.isBlank(dto.getFieldCode())
                && !def.getFieldCode().equals(dto.getFieldCode().trim())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "字段编码创建后不可修改");
        }
        if (!TextUtils.isBlank(dto.getSectionCode())
                && !def.getSectionCode().equalsIgnoreCase(dto.getSectionCode().trim())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "字段所属板块不可修改");
        }
        boolean hasValues = hasValues(def.getFieldCode());
        apply(def, dto, false);
        fieldDefMapper.updateById(def);
        audit(def, AuditLogService.ACTION_UPDATE, "修改字段：" + def.getFieldCode());
        return toVO(def, hasValues);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, int status) {
        requireAdmin();
        ArcFieldDef def = requireField(id);
        if (status == 0 && systemOwned(def)) {
            throw new BusinessException(ResultCode.STATE_CONFLICT,
                    "系统维护字段不能停用：" + def.getFieldCode());
        }
        def.setStatus(status);
        fieldDefMapper.updateById(def);
        audit(def, status == 1 ? AuditLogService.ACTION_ENABLE : AuditLogService.ACTION_DISABLE,
                "字段状态：" + status);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRequired(Long id, int required) {
        requireAdmin();
        ArcFieldDef def = requireField(id);
        if (required == 1 && computed(def)) {
            throw new BusinessException(ResultCode.STATE_CONFLICT,
                    "系统推导字段不能设为必填：" + def.getFieldCode());
        }
        def.setRequired(required);
        fieldDefMapper.updateById(def);
        audit(def, AuditLogService.ACTION_UPDATE, "必填开关：" + required);
    }

    @Override
    public FieldConfigMetaVO meta() {
        requireAdmin();
        List<FieldConfigMetaVO.Item> types = Arrays.stream(FieldTypeEnum.values())
                .map(type -> new FieldConfigMetaVO.Item(type.name(), type.getLabel()))
                .toList();
        List<FieldConfigMetaVO.Item> sources = Arrays.stream(FieldSourceEnum.values())
                .map(source -> new FieldConfigMetaVO.Item(source.name(), source.getLabel()))
                .toList();
        List<String> validators = validatorRegistry.codes().stream().sorted().toList();
        return new FieldConfigMetaVO(types, sources, validators, List.of("STU", "COU"));
    }

    // ------------------------------------------------------------------
    // 校验与映射
    // ------------------------------------------------------------------

    private void apply(ArcFieldDef def, FieldDefSaveDTO dto, boolean creating) {
        String type = requireType(dto.getFieldType());
        String oldType = def.getFieldType();
        if (!creating && oldType != null && !oldType.equals(type) && hasValues(def.getFieldCode())) {
            throw new BusinessException(ResultCode.STATE_CONFLICT,
                    "字段已有数据，不能修改类型：" + def.getFieldCode());
        }
        def.setFieldType(type);
        def.setFieldName(dto.getFieldName().trim());
        def.setRequired(dto.getRequired() == null ? 0 : dto.getRequired());
        def.setSource(requireSource(dto.getSource()));
        def.setVisibleRoles(joinRoles(dto.getVisibleRoles(), false));
        def.setEditableRoles(joinRoles(dto.getEditableRoles(), true));
        if (!rolesOf(def.getEditableRoles()).stream().allMatch(rolesOf(def.getVisibleRoles())::contains)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "可编辑角色必须是可见角色的子集");
        }
        def.setValidator(requireValidator(dto.getValidator(), type));
        def.setRuleJson(requireJson(dto.getRuleJson(), "校验规则"));
        def.setOptionsJson(requireOptions(dto.getOptionsJson(), type));
        def.setSensitiveLevel(dto.getSensitiveLevel() == null ? 0 : dto.getSensitiveLevel());
        if (def.getSensitiveLevel() < 0 || def.getSensitiveLevel() > 2) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "敏感级别只能是 0/1/2");
        }
        if (dto.getSortNo() != null) {
            def.setSortNo(dto.getSortNo());
        } else if (creating) {
            def.setSortNo(nextSortNo(def.getSectionCode()));
        }
        if (def.getSortNo() == null || def.getSortNo() <= 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "排序号必须为正整数");
        }
        def.setRemark(dto.getRemark());
        if (def.getRequired() == 1 && computed(def)) {
            throw new BusinessException(ResultCode.STATE_CONFLICT, "系统推导字段不能设为必填");
        }
    }

    private String requireNewFieldCode(SectionCodeEnum section, String raw) {
        if (TextUtils.isBlank(raw)) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "字段编码不能为空");
        }
        String code = raw.trim();
        String prefix = section.name().toLowerCase(Locale.ROOT) + ".";
        if (!code.startsWith(prefix)) {
            throw new BusinessException(ResultCode.PARAM_INVALID,
                    "字段编码必须以 " + prefix + " 开头");
        }
        if (!code.matches("[a-z][a-z0-9]*\\.[a-zA-Z][a-zA-Z0-9]*")) {
            throw new BusinessException(ResultCode.PARAM_INVALID,
                    "字段编码格式应为 basic.name 这类字母数字点号组合");
        }
        return code;
    }

    private SectionCodeEnum requireSection(String code) {
        SectionCodeEnum section = SectionCodeEnum.tryFromCode(code);
        if (section == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "未知板块编码：" + code);
        }
        return section;
    }

    private String requireType(String code) {
        for (FieldTypeEnum type : FieldTypeEnum.values()) {
            if (type.name().equalsIgnoreCase(code == null ? "" : code.trim())) {
                return type.name();
            }
        }
        throw new BusinessException(ResultCode.PARAM_INVALID, "未知字段类型：" + code);
    }

    private String requireSource(String code) {
        for (FieldSourceEnum source : FieldSourceEnum.values()) {
            if (source.name().equalsIgnoreCase(code == null ? "" : code.trim())) {
                return source.name();
            }
        }
        throw new BusinessException(ResultCode.PARAM_INVALID, "未知字段来源：" + code);
    }

    private String requireValidator(String validator, String type) {
        String value = TextUtils.isBlank(validator) ? defaultValidator(type) : validator.trim().toUpperCase(Locale.ROOT);
        if (!validatorRegistry.codes().contains(value)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "未注册的校验器：" + value);
        }
        return value;
    }

    private String defaultValidator(String type) {
        return switch (type) {
            case "SELECT", "RADIO" -> "ENUM";
            case "MULTI_SELECT", "CHECKBOX" -> "STRING_LIST";
            case "FILE" -> "FILE_ID";
            default -> "TEXT_LEN";
        };
    }

    private String requireOptions(String optionsJson, String type) {
        boolean enumType = ENUM_TYPES.contains(type);
        if (TextUtils.isBlank(optionsJson)) {
            if (enumType) {
                throw new BusinessException(ResultCode.PARAM_MISSING, "枚举字段必须配置选项");
            }
            return null;
        }
        if (!enumType) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "非枚举字段不能配置选项");
        }
        JsonNode node;
        try {
            node = objectMapper.readTree(optionsJson);
        } catch (Exception ex) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "选项不是合法 JSON");
        }
        if (!node.isArray() || node.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "选项必须是非空数组");
        }
        List<FieldOption> options = FieldOption.parse(optionsJson);
        if (options.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "选项必须包含 value");
        }
        Set<String> values = new LinkedHashSet<>();
        for (FieldOption option : options) {
            if (!values.add(option.value())) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "选项值重复：" + option.value());
            }
        }
        return optionsJson;
    }

    private String requireJson(String json, String label) {
        if (TextUtils.isBlank(json)) {
            return null;
        }
        try {
            objectMapper.readTree(json);
            return json;
        } catch (Exception ex) {
            throw new BusinessException(ResultCode.PARAM_INVALID, label + "不是合法 JSON");
        }
    }

    private String joinRoles(List<String> roles, boolean editable) {
        if (roles == null) {
            roles = List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String role : roles) {
            if (TextUtils.isBlank(role)) {
                continue;
            }
            String value = role.trim().toUpperCase(Locale.ROOT);
            if (!ROLES.contains(value)) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "角色只能是 STU 或 COU：" + role);
            }
            normalized.add(value);
        }
        if (normalized.isEmpty() && !editable) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "至少选择一个可见角色");
        }
        List<String> ordered = new ArrayList<>();
        if (normalized.contains("STU")) {
            ordered.add("STU");
        }
        if (normalized.contains("COU")) {
            ordered.add("COU");
        }
        return String.join(",", ordered);
    }

    private Set<String> rolesOf(String roles) {
        if (TextUtils.isBlank(roles)) {
            return Set.of();
        }
        return Arrays.stream(roles.split(",")).map(String::trim).collect(Collectors.toSet());
    }

    private List<String> parseRoles(String roles) {
        if (TextUtils.isBlank(roles)) {
            return List.of();
        }
        return Arrays.stream(roles.split(",")).map(String::trim).filter(item -> !item.isEmpty()).toList();
    }

    private boolean hasValues(String fieldCode) {
        Long count = itemMapper.selectCount(Wrappers.<ArcProfileItem>lambdaQuery()
                .eq(ArcProfileItem::getFieldCode, fieldCode));
        return count != null && count > 0;
    }

    private Set<String> fieldCodesWithValues(List<ArcFieldDef> defs) {
        if (defs.isEmpty()) {
            return Set.of();
        }
        List<String> codes = defs.stream().map(ArcFieldDef::getFieldCode).toList();
        QueryWrapper<ArcProfileItem> query = new QueryWrapper<>();
        query.select("DISTINCT field_code").in("field_code", codes);
        return itemMapper.selectObjs(query).stream()
                .filter(java.util.Objects::nonNull)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    private int nextSortNo(String sectionCode) {
        ArcFieldDef last = fieldDefMapper.selectOne(Wrappers.<ArcFieldDef>lambdaQuery()
                .eq(ArcFieldDef::getSectionCode, sectionCode)
                .orderByDesc(ArcFieldDef::getSortNo)
                .last("limit 1"));
        return last == null || last.getSortNo() == null ? 10 : last.getSortNo() + 10;
    }

    private ArcFieldDef requireField(Long id) {
        ArcFieldDef def = id == null ? null : fieldDefMapper.selectById(id);
        if (def == null) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "字段定义不存在：" + id);
        }
        return def;
    }

    private ArcFieldDef findByCode(String fieldCode) {
        return fieldDefMapper.selectOne(Wrappers.<ArcFieldDef>lambdaQuery()
                .eq(ArcFieldDef::getFieldCode, fieldCode).last("limit 1"));
    }

    private boolean systemOwned(ArcFieldDef def) {
        return "SIS".equals(def.getSource()) || computed(def);
    }

    private boolean computed(ArcFieldDef def) {
        return "SYS".equals(def.getSource()) || "DERIVED".equals(def.getValidator());
    }

    private void requireAdmin() {
        CurrentUser user = UserContext.get();
        if (user.role() != UserRoleEnum.ADMIN) {
            throw new BusinessException(ResultCode.PERMISSION_DENIED, "仅管理员可以维护字段定义");
        }
    }

    private void audit(ArcFieldDef def, String action, String detail) {
        auditLogService.record(AuditLogService.BIZ_FIELD_CONFIG, AuditLogService.TARGET_FIELD_DEF,
                def.getId(), null, def.getSectionCode(), def.getFieldCode(), action, detail);
    }

    private FieldDefVO toVO(ArcFieldDef def, boolean hasValues) {
        FieldDefVO vo = new FieldDefVO();
        vo.setId(def.getId());
        vo.setSectionCode(def.getSectionCode());
        SectionCodeEnum section = SectionCodeEnum.tryFromCode(def.getSectionCode());
        vo.setSectionName(section == null ? def.getSectionCode() : section.getLabel());
        vo.setFieldCode(def.getFieldCode());
        vo.setFieldName(def.getFieldName());
        vo.setFieldType(def.getFieldType());
        vo.setFieldTypeLabel(FieldTypeEnum.fromCode(def.getFieldType()).getLabel());
        vo.setRequired(def.getRequired());
        vo.setSource(def.getSource());
        vo.setSourceLabel(FieldSourceEnum.fromCode(def.getSource()).getLabel());
        vo.setSourceField(def.getSourceField());
        vo.setVisibleRoles(parseRoles(def.getVisibleRoles()));
        vo.setEditableRoles(parseRoles(def.getEditableRoles()));
        vo.setDefaultEditableRoles(parseRoles(def.getDefaultEditableRoles()));
        vo.setValidator(def.getValidator());
        vo.setRuleJson(def.getRuleJson());
        vo.setOptionsJson(def.getOptionsJson());
        vo.setOptions(FieldOption.parse(def.getOptionsJson()).stream()
                .map(option -> new FieldDefVO.Option(option.value(), option.label())).toList());
        vo.setSensitiveLevel(def.getSensitiveLevel());
        vo.setSortNo(def.getSortNo());
        vo.setStatus(def.getStatus());
        vo.setRemark(def.getRemark());
        vo.setHasValues(hasValues);
        vo.setSystemOwned(systemOwned(def));
        return vo;
    }
}
