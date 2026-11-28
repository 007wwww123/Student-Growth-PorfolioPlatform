package com.college.growth.common.student.service;

import com.college.growth.common.field.FieldValues;
import com.college.growth.common.field.SensitiveMasker;
import com.college.growth.common.field.entity.ArcFieldDef;
import com.college.growth.common.field.service.FieldDefService;
import com.college.growth.common.log.service.ChangeLogService;
import com.college.growth.common.profile.FieldValueCodec;
import com.college.growth.common.profile.entity.ArcProfile;
import com.college.growth.common.profile.entity.ArcProfileItem;
import com.college.growth.common.profile.service.ProfileItemService;
import com.college.growth.common.security.IdCardCrypto;
import com.college.growth.common.student.entity.StuStudent;
import com.college.growth.common.util.TextUtils;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 学生主数据 → 档案字段的同步（物化）。
 *
 * <p><b>为什么是物化而不是读时派生</b>：档案侧的字段值统一存在 {@code arc_profile_item}，
 * 校验、完成度、变更日志、待确认区、审核页全按这一张表读。若改成读时从主数据派生，
 * 这五处都要各加一条分支，且「谁在什么时候把学号改成了什么」将失去留痕。
 * 因此同步只做一件事：把 SIS 值写进字段行，其余逻辑一行不改。</p>
 *
 * <p><b>两种同步语义</b>：</p>
 * <ul>
 *   <li>{@link #fillAbsent 补齐空值}：建档、保存、提交时自动调用，只填档案里还空着的字段，
 *       绝不覆盖已有值——学生已确认的手机号、辅导员维护的宿舍号不能因为一次同步被冲掉；</li>
 *   <li>{@link #overwrite 强制覆盖}：仅由辅导员/管理员显式触发，用于学工系统改正数据后
 *       把新值推到档案，每次覆盖都写 {@code IMPORT} 变更日志。</li>
 * </ul>
 *
 * <p>字段与主数据列的对应关系来自 {@code arc_field_def.source_field}，不在代码里按字段硬编码：
 * 新增一个 SIS 字段只要在定义表里填上列名即可，本类不用改。</p>
 */
@Service
public class StudentSyncService {

    private static final Logger log = LoggerFactory.getLogger(StudentSyncService.class);

    /** 系统同步的操作人标识：0 表示非人工操作，日志里据此与真实用户区分。 */
    private static final long SYSTEM_OPERATOR_ID = 0L;

    /** 允许同步的来源：只有 SIS 字段由主数据维护。 */
    private static final String SOURCE_SIS = "SIS";

    /** 待确认区状态：主数据同步的值不需要学生二次确认，直接落 NONE。 */
    private static final String CONFIRM_NONE = "NONE";

    /** 日志值最大留存长度，避免超长文本撑爆 arc_change_log 的列。 */
    private static final int LOG_VALUE_MAX_LENGTH = 500;

    /**
     * 主数据列名 → 取值函数。
     *
     * <p>用显式函数而不是反射：列名写错在编译期就报错，反射要等线上跑到才发现，
     * 而这类错误的表现是「字段一直是空的」，很难排查。</p>
     */
    private static final Map<String, Function<StuStudent, String>> EXTRACTORS = buildExtractors();

    private final StudentMasterService studentMasterService;

    private final FieldDefService fieldDefService;

    private final ProfileItemService itemService;

    private final ChangeLogService changeLogService;

    public StudentSyncService(StudentMasterService studentMasterService,
                              FieldDefService fieldDefService,
                              ProfileItemService itemService,
                              ChangeLogService changeLogService) {
        this.studentMasterService = studentMasterService;
        this.fieldDefService = fieldDefService;
        this.itemService = itemService;
        this.changeLogService = changeLogService;
    }

    /**
     * 补齐档案中为空的 SIS 字段（幂等，不覆盖已有值）。
     *
     * @param profile 已存在的板块实例
     * @param defs    该板块启用的字段定义
     * @param items   已加载的字段值，命中同步的项会被就地更新，调用方无需重查
     * @return 实际写入的字段数
     */
    public int fillAbsent(ArcProfile profile, List<ArcFieldDef> defs, Map<String, ArcProfileItem> items) {
        return apply(profile, defs, items, false);
    }

    /** 同上，但按主数据强制覆盖已存在的值，仅用于显式的重新同步。 */
    public int overwrite(ArcProfile profile, List<ArcFieldDef> defs, Map<String, ArcProfileItem> items) {
        return apply(profile, defs, items, true);
    }

    /** 便捷入口：自行加载字段定义后补齐空值。 */
    public int fillAbsent(ArcProfile profile) {
        if (profile == null) {
            return 0;
        }
        List<ArcFieldDef> defs = fieldDefService.listActive(profile.getSectionCode());
        return fillAbsent(profile, defs, itemService.loadByProfile(profile.getId()));
    }

    private int apply(ArcProfile profile, List<ArcFieldDef> defs,
                      Map<String, ArcProfileItem> items, boolean force) {
        if (profile == null || defs == null || defs.isEmpty()) {
            return 0;
        }
        Optional<StuStudent> student = studentMasterService.find(profile.getStudentId());
        if (student.isEmpty()) {
            // 主数据尚未同步进来：保持档案原样，不要用空值把已有内容冲掉。
            log.debug("学生 {} 无主数据记录，跳过同步", profile.getStudentId());
            return 0;
        }
        int written = 0;
        for (ArcFieldDef def : defs) {
            if (!SOURCE_SIS.equalsIgnoreCase(def.getSource()) || TextUtils.isBlank(def.getSourceField())) {
                continue;
            }
            Function<StuStudent, String> extractor = EXTRACTORS.get(def.getSourceField().trim());
            if (extractor == null) {
                log.warn("字段 {} 的 source_field={} 无对应主数据列，已跳过",
                        def.getFieldCode(), def.getSourceField());
                continue;
            }
            String incoming = TextUtils.sanitize(extractor.apply(student.get()));
            if (TextUtils.isBlank(incoming)) {
                continue;
            }
            ArcProfileItem item = items.get(def.getFieldCode());
            Object current = FieldValueCodec.read(def, item);
            if (!force && !FieldValues.isEmpty(current)) {
                continue;
            }
            if (force && FieldValueCodec.same(def, current, incoming)) {
                continue;
            }
            if (write(profile, def, item, items, incoming)) {
                written++;
            }
        }
        if (written > 0) {
            log.info("学生 {} 板块 {} 同步主数据 {} 个字段（force={}）",
                    profile.getStudentId(), profile.getSectionCode(), written, force);
        }
        return written;
    }

    private boolean write(ArcProfile profile, ArcFieldDef def, ArcProfileItem item,
                          Map<String, ArcProfileItem> items, String incoming) {
        Object oldValue = FieldValueCodec.read(def, item);
        boolean newItem = item == null;
        if (newItem) {
            item = new ArcProfileItem();
            item.setProfileId(profile.getId());
            item.setFieldCode(def.getFieldCode());
            // SIS 字段由学校维护，不需要学生二次确认。
            item.setConfirmStatus(CONFIRM_NONE);
        }
        FieldValueCodec.write(item, def, incoming);
        item.setUpdatedBy(SYSTEM_OPERATOR_ID);
        itemService.save(item);
        items.put(def.getFieldCode(), item);

        changeLogService.recordImport(profile, def.getFieldCode(),
                describe(def, oldValue), describe(def, incoming));
        return true;
    }

    /** 日志值：敏感字段只写脱敏值，与手工修改的口径保持一致（BR-S11）。 */
    private String describe(ArcFieldDef def, Object value) {
        if (FieldValues.isEmpty(value)) {
            return null;
        }
        Object display = value;
        if (def.getSensitiveLevel() != null && def.getSensitiveLevel() > 0) {
            display = SensitiveMasker.maskValue(def.getFieldCode(), value);
        }
        String text = display instanceof String textValue ? textValue : FieldValues.asText(display);
        return FieldValues.truncate(text, LOG_VALUE_MAX_LENGTH);
    }

    private static Map<String, Function<StuStudent, String>> buildExtractors() {
        Map<String, Function<StuStudent, String>> map = new LinkedHashMap<>();
        map.put("name", StuStudent::getName);
        map.put("student_no", StuStudent::getStudentNo);
        map.put("gender", StuStudent::getGender);
        map.put("nation", StuStudent::getNation);
        map.put("id_card", student -> IdCardCrypto.plain(student.getIdCard(), student.getIdCardCipher()));
        map.put("native_place", StuStudent::getNativePlace);
        map.put("class_code", StuStudent::getClassCode);
        map.put("enrollment_year",
                student -> student.getEnrollmentYear() == null ? null : String.valueOf(student.getEnrollmentYear()));
        return Map.copyOf(map);
    }
}
