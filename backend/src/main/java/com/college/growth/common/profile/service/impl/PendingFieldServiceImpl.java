package com.college.growth.common.profile.service.impl;

import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.context.UserContext;
import com.college.growth.common.enums.SectionCodeEnum;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.field.FieldValues;
import com.college.growth.common.field.SensitiveMasker;
import com.college.growth.common.field.entity.ArcFieldDef;
import com.college.growth.common.field.service.FieldDefService;
import com.college.growth.common.log.service.AuditLogService;
import com.college.growth.common.log.service.ChangeLogService;
import com.college.growth.common.org.service.StudentClassService;
import com.college.growth.common.profile.FieldValueCodec;
import com.college.growth.common.profile.entity.ArcProfile;
import com.college.growth.common.profile.entity.ArcProfileItem;
import com.college.growth.common.profile.service.PendingFieldService;
import com.college.growth.common.profile.service.ProfileItemService;
import com.college.growth.common.profile.service.ProfileLocator;
import com.college.growth.common.profile.vo.PendingFieldVO;
import com.college.growth.common.result.ResultCode;
import com.college.growth.common.util.AcademicYearUtils;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 待确认区处理实现（BR-F06）。
 *
 * <p>权限按「该班现任辅导员」判定，与审核同一套数据范围；学生不能自己确认自己的值，
 * 管理员不参与业务数据修改。确认/驳回都会清空 {@code pending_*}，
 * 避免下一次修改被误判为「已有待确认值」而永远挂起。</p>
 */
@Service
public class PendingFieldServiceImpl implements PendingFieldService {

    /** 待确认状态：辅导员已确认。 */
    private static final String CONFIRM_APPROVED = "APPROVED";

    /** 待确认状态：辅导员已驳回。 */
    private static final String CONFIRM_REJECTED = "REJECTED";

    /** 审计动作：确认。 */
    private static final String ACTION_CONFIRM = "CONFIRM";

    /** 审计动作：驳回。 */
    private static final String ACTION_DISCARD = "DISCARD";

    /** 审计目标类型：字段行。 */
    private static final String TARGET_FIELD = "FIELD";

    /** 日志值最大长度，与 arc_change_log 列宽一致。 */
    private static final int LOG_VALUE_MAX_LENGTH = 500;

    private final ProfileLocator profileLocator;

    private final ProfileItemService itemService;

    private final FieldDefService fieldDefService;

    private final StudentClassService studentClassService;

    private final ChangeLogService changeLogService;

    private final AuditLogService auditLogService;

    public PendingFieldServiceImpl(ProfileLocator profileLocator,
                                   ProfileItemService itemService,
                                   FieldDefService fieldDefService,
                                   StudentClassService studentClassService,
                                   ChangeLogService changeLogService,
                                   AuditLogService auditLogService) {
        this.profileLocator = profileLocator;
        this.itemService = itemService;
        this.fieldDefService = fieldDefService;
        this.studentClassService = studentClassService;
        this.changeLogService = changeLogService;
        this.auditLogService = auditLogService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PendingFieldVO confirm(Long studentId, String sectionCode, String fieldCode, String academicYear) {
        return handle(studentId, sectionCode, fieldCode, academicYear, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long studentId, String sectionCode, String fieldCode, String academicYear) {
        handle(studentId, sectionCode, fieldCode, academicYear, false);
    }

    private PendingFieldVO handle(Long studentId, String sectionCode, String fieldCode,
                                  String academicYear, boolean confirm) {
        String year = AcademicYearUtils.resolve(academicYear);
        // 先判权限再查数据：无权者不应通过「字段不存在 / 没有待确认值」这类差异探测到别人的数据。
        ArcProfile profile = requireScopedProfile(studentId, sectionCode, year);
        ArcFieldDef def = requireFieldDef(sectionCode, fieldCode);
        ArcProfileItem item = requirePendingItem(profile, def, fieldCode);
        Object pending = FieldValueCodec.readPending(def, item);
        Object current = FieldValueCodec.read(def, item);

        CurrentUser user = UserContext.get();
        if (confirm) {
            FieldValueCodec.write(item, def, pending);
            item.setConfirmStatus(CONFIRM_APPROVED);
        } else {
            // 驳回：正式值保持不变，只丢弃待确认值。
            item.setConfirmStatus(CONFIRM_REJECTED);
        }
        FieldValueCodec.clearPending(item);
        item.setConfirmedBy(user.userId());
        item.setConfirmedAt(LocalDateTime.now());
        item.setUpdatedBy(user.userId());
        itemService.save(item);

        String action = confirm ? ACTION_CONFIRM : ACTION_DISCARD;
        String note = (confirm ? "确认待确认值：" : "驳回待确认值：") + def.getFieldName();
        changeLogService.record(profile, ChangeLogService.BIZ_REVIEW, fieldCode,
                text(def, current), confirm ? text(def, pending) : null, null, null, note);
        auditLogService.record(AuditLogService.BIZ_FIELD_CONFIRM, TARGET_FIELD, item.getId(),
                studentId, profile.getSectionCode(), fieldCode, action, note);

        PendingFieldVO vo = new PendingFieldVO();
        vo.setFieldCode(fieldCode);
        vo.setFieldName(def.getFieldName());
        vo.setValue(display(def, FieldValueCodec.read(def, item)));
        vo.setConfirmStatus(item.getConfirmStatus());
        return vo;
    }

    private ArcFieldDef requireFieldDef(String sectionCode, String fieldCode) {
        ArcFieldDef def = fieldDefService.getByCode(fieldCode);
        if (def == null || !sectionCode.equalsIgnoreCase(def.getSectionCode())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "字段不存在或不属于该板块");
        }
        return def;
    }

    /** 校验操作人是该班现任辅导员，并取回板块实例。 */
    private ArcProfile requireScopedProfile(Long studentId, String sectionCode, String year) {
        CurrentUser user = UserContext.get();
        boolean allowed = user.isCounselor()
                && studentClassService.isCounselorOf(studentId, year, user.userId());
        if (!allowed) {
            throw new BusinessException(ResultCode.DATA_SCOPE_DENIED, "仅该班现任辅导员可以处理待确认值");
        }

        SectionCodeEnum section = SectionCodeEnum.tryFromCode(sectionCode);
        if (section == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "未知板块编码：" + sectionCode);
        }
        // 数据库里 section_code 是大写枚举名，接口路径是小写编码，这里统一收敛。
        return profileLocator.require(studentId, section.name(), year, section.getLabel());
    }

    /** 校验该字段确实有等待确认的新值。 */
    private ArcProfileItem requirePendingItem(ArcProfile profile, ArcFieldDef def, String fieldCode) {
        ArcProfileItem item = itemService.find(profile.getId(), fieldCode);
        if (item == null || !item.hasPending()) {
            throw new BusinessException(ResultCode.STATE_CONFLICT,
                    "「" + def.getFieldName() + "」当前没有待确认的值");
        }
        return item;
    }

    /** 对外展示值：敏感字段脱敏，其余原样返回。 */
    private Object display(ArcFieldDef def, Object value) {
        if (FieldValues.isEmpty(value)) {
            return null;
        }
        return isSensitive(def) ? SensitiveMasker.maskValue(def.getFieldCode(), value) : value;
    }

    /** 日志文本：敏感字段脱敏，长文本截断。 */
    private String text(ArcFieldDef def, Object value) {
        Object shown = display(def, value);
        if (shown == null) {
            return null;
        }
        String raw = shown instanceof String textValue ? textValue : FieldValueCodec.toJson(shown);
        return FieldValues.truncate(raw, LOG_VALUE_MAX_LENGTH);
    }

    private boolean isSensitive(ArcFieldDef def) {
        return def.getSensitiveLevel() != null && def.getSensitiveLevel() > 0;
    }

}
