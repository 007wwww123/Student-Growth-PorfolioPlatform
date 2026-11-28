package com.college.growth.permission.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.context.UserContext;
import com.college.growth.common.enums.SectionCodeEnum;
import com.college.growth.common.enums.UserRoleEnum;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.field.entity.ArcFieldDef;
import com.college.growth.common.field.mapper.ArcFieldDefMapper;
import com.college.growth.common.log.service.AuditLogService;
import com.college.growth.common.result.ResultCode;
import com.college.growth.common.util.TextUtils;
import com.college.growth.permission.common.PermissionConstants;
import com.college.growth.permission.dto.FieldPermissionDTO;
import com.college.growth.permission.dto.PermissionSaveDTO;
import com.college.growth.permission.service.FieldPermissionService;
import com.college.growth.permission.vo.FieldPermissionVO;
import com.college.growth.permission.vo.PermissionMatrixVO;
import com.college.growth.permission.vo.PermissionSaveResultVO;
import com.college.growth.permission.vo.SectionPermissionVO;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 字段编辑权限配置实现。
 *
 * <p>写路径只有一个：更新 {@code arc_field_def.editable_roles}。六个板块的详情接口
 * 都按该列计算 {@code editable}，保存接口再按同一列做越权校验（EC-40301），
 * 因此配置保存后立即生效，且不需要在六个板块各写一套权限判断。</p>
 *
 * <p>三条硬约束：</p>
 * <ol>
 *   <li>只有 ADMIN 能调用，学生与辅导员返回 EC-40303；</li>
 *   <li>ADMIN 永远不能写入可编辑角色——管理员只配置权限，不改档案内容；</li>
 *   <li>系统推导 / 主键类字段固定只读，不允许通过配置放开。</li>
 * </ol>
 */
@Slf4j
@Service
public class FieldPermissionServiceImpl implements FieldPermissionService {

    /** 固定只读：由班级自动推导。 */
    private static final String LOCK_DERIVED = "该字段由系统按班级自动推导，固定只读";

    /** 固定只读：系统生成。 */
    private static final String LOCK_SYSTEM = "该字段由系统生成，固定只读";

    /** 固定只读：学籍唯一标识。 */
    private static final String LOCK_STUDENT_NO = "学号是学籍唯一标识，创建后不可修改";

    /** 唯一键字段：即使来源是 SIS，也不允许配置为可编辑。 */
    private static final String FIELD_STUDENT_NO = "basic.studentNo";

    private final ArcFieldDefMapper fieldDefMapper;

    private final AuditLogService auditLogService;

    public FieldPermissionServiceImpl(ArcFieldDefMapper fieldDefMapper, AuditLogService auditLogService) {
        this.fieldDefMapper = fieldDefMapper;
        this.auditLogService = auditLogService;
    }

    @Override
    public PermissionMatrixVO matrix() {
        requireAdmin();
        List<ArcFieldDef> all = fieldDefMapper.selectList(Wrappers.<ArcFieldDef>lambdaQuery()
                .eq(ArcFieldDef::getStatus, 1)
                .orderByAsc(ArcFieldDef::getSortNo));
        Map<String, List<ArcFieldDef>> bySection = all.stream()
                .collect(Collectors.groupingBy(ArcFieldDef::getSectionCode));

        List<SectionPermissionVO> sections = new ArrayList<>();
        for (SectionCodeEnum section : SectionCodeEnum.values()) {
            List<ArcFieldDef> defs = bySection.getOrDefault(section.name(), List.of());
            sections.add(buildSection(section, defs));
        }
        PermissionMatrixVO matrix = new PermissionMatrixVO();
        matrix.setSections(sections);
        return matrix;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PermissionSaveResultVO save(String sectionCode, PermissionSaveDTO dto) {
        requireAdmin();
        SectionCodeEnum section = requireSection(sectionCode);
        Map<String, ArcFieldDef> defByCode = loadFields(section).stream()
                .collect(Collectors.toMap(ArcFieldDef::getFieldCode, def -> def));

        Set<String> seen = new LinkedHashSet<>();
        int changed = 0;
        for (FieldPermissionDTO item : dto.getFields()) {
            String fieldCode = item.getFieldCode().trim();
            ArcFieldDef def = defByCode.get(fieldCode);
            if (def == null) {
                throw new BusinessException(ResultCode.PARAM_INVALID,
                        "字段「" + fieldCode + "」不属于板块「" + section.getLabel() + "」");
            }
            if (!seen.add(fieldCode)) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "字段「" + fieldCode + "」重复提交");
            }
            List<String> roles = normalizeRoles(item.getEditableRoles(), def);
            String next = String.join(",", roles);
            String current = canonical(def.getEditableRoles());
            if (next.equals(current)) {
                continue;
            }
            String defaultRoles = def.getDefaultEditableRoles() == null
                    ? current : canonical(def.getDefaultEditableRoles());
            updateRoles(def, next, defaultRoles);
            changed++;
            auditLogService.record(AuditLogService.BIZ_PERMISSION_CONFIG, AuditLogService.TARGET_FIELD_DEF,
                    def.getId(), null, def.getSectionCode(), fieldCode, "UPDATE",
                    "可编辑角色：" + displayRoles(current) + " → " + displayRoles(next));
        }
        log.info("管理员配置字段权限 section={} changed={} operator={}", section.name(), changed,
                UserContext.get().userId());
        return result(changed, section);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PermissionSaveResultVO reset(String sectionCode) {
        requireAdmin();
        SectionCodeEnum section = requireSection(sectionCode);
        int changed = 0;
        for (ArcFieldDef def : loadFields(section)) {
            if (def.getDefaultEditableRoles() == null) {
                // 老数据没有默认值快照：保持现状，不猜测默认口径
                continue;
            }
            String current = canonical(def.getEditableRoles());
            String next = canonical(def.getDefaultEditableRoles());
            if (current.equals(next)) {
                continue;
            }
            updateRoles(def, next, next);
            changed++;
            auditLogService.record(AuditLogService.BIZ_PERMISSION_CONFIG, AuditLogService.TARGET_FIELD_DEF,
                    def.getId(), null, def.getSectionCode(), def.getFieldCode(), AuditLogService.ACTION_RESET,
                    "恢复默认可编辑角色：" + displayRoles(next) + "（原为 " + displayRoles(current) + "）");
        }
        log.info("管理员恢复字段默认权限 section={} changed={} operator={}", section.name(), changed,
                UserContext.get().userId());
        return result(changed, section);
    }

    // ------------------------------------------------------------------
    // 校验与写入
    // ------------------------------------------------------------------

    private void requireAdmin() {
        CurrentUser user = UserContext.get();
        if (user.role() != UserRoleEnum.ADMIN) {
            throw new BusinessException(ResultCode.PERMISSION_DENIED, "仅管理员可以配置字段编辑权限");
        }
    }

    private SectionCodeEnum requireSection(String sectionCode) {
        SectionCodeEnum section = SectionCodeEnum.tryFromCode(sectionCode);
        if (section == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "未知板块编码：" + sectionCode);
        }
        return section;
    }

    private List<ArcFieldDef> loadFields(SectionCodeEnum section) {
        return fieldDefMapper.selectList(Wrappers.<ArcFieldDef>lambdaQuery()
                .eq(ArcFieldDef::getSectionCode, section.name())
                .eq(ArcFieldDef::getStatus, 1)
                .orderByAsc(ArcFieldDef::getSortNo));
    }

    /**
     * 归一化角色列表：只允许 STU / COU，去重并按固定顺序输出。
     *
     * <p>ADMIN 被显式拒绝而不是静默忽略：若静默丢弃，调用方会以为配置成功，
     * 实际管理员仍然不能编辑，问题会被带到验收阶段。</p>
     */
    private List<String> normalizeRoles(List<String> roles, ArcFieldDef def) {
        List<String> normalized = new ArrayList<>();
        if (roles != null) {
            for (String role : roles) {
                if (TextUtils.isBlank(role)) {
                    continue;
                }
                String value = role.trim().toUpperCase();
                if (PermissionConstants.ROLE_ADMIN.equals(value)) {
                    throw new BusinessException(ResultCode.PARAM_INVALID,
                            "管理员只负责配置权限，不能作为内容编辑角色");
                }
                if (!PermissionConstants.ROLE_STUDENT.equals(value)
                        && !PermissionConstants.ROLE_COUNSELOR.equals(value)) {
                    throw new BusinessException(ResultCode.PARAM_INVALID,
                            "可编辑角色只能是 STU（学生）或 COU（老师）");
                }
                if (!roleVisible(def, value)) {
                    String label = PermissionConstants.ROLE_STUDENT.equals(value) ? "学生" : "老师";
                    throw new BusinessException(ResultCode.PARAM_INVALID,
                            "字段「" + def.getFieldName() + "」对" + label + "不可见，不能配置为可编辑");
                }
                if (!normalized.contains(value)) {
                    normalized.add(value);
                }
            }
        }
        String lockedReason = lockedReason(def);
        if (lockedReason != null && !normalized.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_INVALID,
                    "字段「" + def.getFieldName() + "」不可配置：" + lockedReason);
        }
        return order(normalized);
    }

    /** 角色是否可见该字段：看不见的字段不能配置为可编辑，否则会出现「能写但看不到」。 */
    private boolean roleVisible(ArcFieldDef def, String role) {
        if (TextUtils.isBlank(def.getVisibleRoles())) {
            return false;
        }
        return Arrays.stream(def.getVisibleRoles().split(","))
                .map(String::trim)
                .anyMatch(item -> item.equalsIgnoreCase(role));
    }

    private void updateRoles(ArcFieldDef def, String editableRoles, String defaultEditableRoles) {
        fieldDefMapper.update(null, Wrappers.<ArcFieldDef>lambdaUpdate()
                .eq(ArcFieldDef::getId, def.getId())
                .set(ArcFieldDef::getEditableRoles, editableRoles)
                .set(ArcFieldDef::getDefaultEditableRoles, defaultEditableRoles));
        def.setEditableRoles(editableRoles);
        def.setDefaultEditableRoles(defaultEditableRoles);
    }

    private PermissionSaveResultVO result(int changed, SectionCodeEnum section) {
        PermissionSaveResultVO result = new PermissionSaveResultVO();
        result.setChanged(changed);
        result.setSection(buildSection(section, loadFields(section)));
        return result;
    }

    // ------------------------------------------------------------------
    // 视图组装
    // ------------------------------------------------------------------

    private SectionPermissionVO buildSection(SectionCodeEnum section, List<ArcFieldDef> defs) {
        List<FieldPermissionVO> fields = defs.stream().map(this::toVO).toList();
        SectionPermissionVO vo = new SectionPermissionVO();
        vo.setSectionCode(section.name());
        vo.setSectionName(section.getLabel());
        vo.setFields(fields);
        vo.setStudentEditable((int) fields.stream()
                .filter(field -> field.getEditableRoles().contains(PermissionConstants.ROLE_STUDENT))
                .count());
        vo.setCounselorEditable((int) fields.stream()
                .filter(field -> field.getEditableRoles().contains(PermissionConstants.ROLE_COUNSELOR))
                .count());
        vo.setReadonly((int) fields.stream().filter(field -> field.getEditableRoles().isEmpty()).count());
        vo.setCustomized((int) fields.stream().filter(field -> Boolean.TRUE.equals(field.getCustomized())).count());
        return vo;
    }

    private FieldPermissionVO toVO(ArcFieldDef def) {
        List<String> roles = parseRoles(def.getEditableRoles());
        List<String> defaults = def.getDefaultEditableRoles() == null
                ? roles : parseRoles(def.getDefaultEditableRoles());
        String lockedReason = lockedReason(def);

        FieldPermissionVO vo = new FieldPermissionVO();
        vo.setFieldCode(def.getFieldCode());
        vo.setFieldName(def.getFieldName());
        vo.setFieldType(def.getFieldType());
        vo.setSource(def.getSource());
        vo.setRequired(def.isRequiredFlag());
        vo.setSensitive(def.getSensitiveLevel() == null ? 0 : def.getSensitiveLevel());
        vo.setEditableRoles(roles);
        vo.setDefaultEditableRoles(defaults);
        vo.setPermissionMode(modeOf(roles));
        vo.setCustomized(!roles.equals(defaults));
        vo.setStudentVisible(roleVisible(def, PermissionConstants.ROLE_STUDENT));
        vo.setCounselorVisible(roleVisible(def, PermissionConstants.ROLE_COUNSELOR));
        vo.setLocked(lockedReason != null);
        vo.setLockedReason(lockedReason);
        return vo;
    }

    /** 固定只读判定：系统推导、系统生成、学籍唯一标识三类。 */
    private String lockedReason(ArcFieldDef def) {
        if ("DERIVED".equals(def.getValidator())) {
            return LOCK_DERIVED;
        }
        if ("SYS".equals(def.getSource())) {
            return LOCK_SYSTEM;
        }
        if (FIELD_STUDENT_NO.equals(def.getFieldCode())) {
            return LOCK_STUDENT_NO;
        }
        return null;
    }

    private List<String> parseRoles(String roles) {
        if (TextUtils.isBlank(roles)) {
            return List.of();
        }
        return order(Arrays.stream(roles.split(","))
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .map(String::toUpperCase)
                .collect(Collectors.toList()));
    }

    private String canonical(String roles) {
        return String.join(",", parseRoles(roles));
    }

    private List<String> order(List<String> roles) {
        List<String> ordered = new ArrayList<>();
        if (roles.contains(PermissionConstants.ROLE_STUDENT)) {
            ordered.add(PermissionConstants.ROLE_STUDENT);
        }
        if (roles.contains(PermissionConstants.ROLE_COUNSELOR)) {
            ordered.add(PermissionConstants.ROLE_COUNSELOR);
        }
        return List.copyOf(ordered);
    }

    private String modeOf(List<String> roles) {
        boolean student = roles.contains(PermissionConstants.ROLE_STUDENT);
        boolean counselor = roles.contains(PermissionConstants.ROLE_COUNSELOR);
        if (student && counselor) {
            return PermissionConstants.MODE_BOTH;
        }
        if (student) {
            return PermissionConstants.MODE_STUDENT;
        }
        if (counselor) {
            return PermissionConstants.MODE_COUNSELOR;
        }
        return PermissionConstants.MODE_NONE;
    }

    private String displayRoles(String roles) {
        List<String> values = parseRoles(roles);
        if (values.isEmpty()) {
            return "均不可修改";
        }
        List<String> labels = new ArrayList<>();
        for (String value : values) {
            labels.add(PermissionConstants.ROLE_STUDENT.equals(value) ? "学生" : "老师");
        }
        return String.join("、", labels) + "可修改";
    }
}
