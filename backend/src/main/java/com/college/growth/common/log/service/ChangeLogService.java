package com.college.growth.common.log.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.context.UserContext;
import com.college.growth.common.enums.ProfileStatusEnum;
import com.college.growth.common.log.entity.ArcChangeLog;
import com.college.growth.common.log.mapper.ArcChangeLogMapper;
import com.college.growth.common.profile.entity.ArcProfile;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 档案变更日志写入入口（物理表 {@code arc_change_log}）。
 *
 * <p>字段修改、状态流转、审核、敏感字段查看都经本类写日志，保证六板块与审核模块
 * 用的是同一套「谁、何时、把什么改成了什么」（BR-S11：必须与业务同一事务，
 * 因此本类不自己开事务，由调用方的事务传播）。</p>
 */
@Service
public class ChangeLogService {

    /** 日志业务类型：字段修改。 */
    public static final String BIZ_FIELD_EDIT = "FIELD_EDIT";

    /** 日志业务类型：状态流转。 */
    public static final String BIZ_STATUS_CHANGE = "STATUS_CHANGE";

    /** 日志业务类型：审核或敏感字段查看。 */
    public static final String BIZ_REVIEW = "REVIEW";

    /** 日志业务类型：毕业归档（S-1-11，六板块批量转 LOCKED）。 */
    public static final String BIZ_ARCHIVE = "ARCHIVE";

    /** 操作来源：页面。 */
    public static final String SOURCE_WEB = "WEB";

    /** 操作来源：外部系统同步（学工/教务主数据）。 */
    public static final String SOURCE_IMPORT = "IMPORT";

    /** 系统操作人标识：0 表示非人工触发，便于与真实用户区分。 */
    public static final long SYSTEM_OPERATOR_ID = 0L;

    /** 系统操作人角色。 */
    public static final String ROLE_SYSTEM = "SYS";

    private final ArcChangeLogMapper changeLogMapper;

    public ChangeLogService(ArcChangeLogMapper changeLogMapper) {
        this.changeLogMapper = changeLogMapper;
    }

    /**
     * 写一条板块级日志。
     *
     * @param profile   所属板块实例，不能为空
     * @param bizType   FIELD_EDIT / STATUS_CHANGE / REVIEW
     * @param fieldCode 字段级操作时的字段编码，状态流转传 null
     * @param oldValue  变更前值，敏感字段只传脱敏值
     * @param newValue  变更后值，敏感字段只传脱敏值
     * @param fromStatus 流转前状态，非状态类操作传 null
     * @param toStatus  流转后状态，非状态类操作传 null
     * @param reason    操作原因，审核与敏感字段查看必填
     */
    public void record(ArcProfile profile, String bizType, String fieldCode, String oldValue,
                       String newValue, String fromStatus, String toStatus, String reason) {
        CurrentUser user = UserContext.get();
        insert(profile, bizType, fieldCode, oldValue, newValue, fromStatus, toStatus, reason,
                SOURCE_WEB, user.userId(), user.role().name());
    }

    /**
     * 写一条「外部系统同步」日志。
     *
     * <p>同步由学工主数据触发，不是某个用户点的按钮，因此操作人固定记 0 / SYS、来源记 IMPORT；
     * 否则日志里会出现「学生自己改了学号」这种误导性记录。</p>
     */
    public void recordImport(ArcProfile profile, String fieldCode, String oldValue, String newValue) {
        insert(profile, BIZ_FIELD_EDIT, fieldCode, oldValue, newValue, null, null,
                "学工系统同步", SOURCE_IMPORT, SYSTEM_OPERATOR_ID, ROLE_SYSTEM);
    }

    private void insert(ArcProfile profile, String bizType, String fieldCode, String oldValue,
                        String newValue, String fromStatus, String toStatus, String reason,
                        String source, Long operatorId, String operatorRole) {
        ArcChangeLog changeLog = new ArcChangeLog();
        changeLog.setProfileId(profile.getId());
        changeLog.setStudentId(profile.getStudentId());
        changeLog.setSectionCode(profile.getSectionCode());
        changeLog.setBizType(bizType);
        changeLog.setFieldCode(fieldCode);
        changeLog.setOldValue(oldValue);
        changeLog.setNewValue(newValue);
        changeLog.setFromStatus(fromStatus);
        changeLog.setToStatus(toStatus);
        changeLog.setOperatorId(operatorId);
        changeLog.setOperatorRole(operatorRole);
        changeLog.setSource(source);
        changeLog.setReason(reason);
        changeLogMapper.insert(changeLog);
    }

    /**
     * 取最近一次「退回」留痕。
     *
     * <p>退回理由不单独建列，就存在状态流转日志的 {@code reason} 里（BR-S04：理由对学生可见，
     * 且必须与状态变更同事务）。详情接口靠本方法把理由带回前端，六个板块共用。</p>
     *
     * @param profileId 板块实例 ID
     * @return 最近一条 {@code REVIEW} + {@code to_status=RETURNED} 的日志；从未被退回时为空
     */
    public Optional<ArcChangeLog> findLatestReturnNote(Long profileId) {
        if (profileId == null) {
            return Optional.empty();
        }
        ArcChangeLog row = changeLogMapper.selectOne(Wrappers.<ArcChangeLog>lambdaQuery()
                .eq(ArcChangeLog::getProfileId, profileId)
                .eq(ArcChangeLog::getBizType, BIZ_REVIEW)
                .eq(ArcChangeLog::getToStatus, ProfileStatusEnum.RETURNED.name())
                .orderByDesc(ArcChangeLog::getId)
                .last("limit 1"));
        return Optional.ofNullable(row);
    }
}
