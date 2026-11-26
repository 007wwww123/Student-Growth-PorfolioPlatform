package com.college.growth.common.profile.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 字段值（物理表 {@code arc_profile_item}）：一行一个字段。
 *
 * <p>用 EAV 变体而非「一板块一张宽表」，是因为六板块共 51 个主表字段且会随政策增删，
 * 宽表意味着每次加字段都要改表结构与实体；这里加字段只加一行定义。</p>
 *
 * <p>STU_CFM 字段的修改先写 {@code pendingText/pendingJson}，{@code confirmStatus=PENDING}，
 * 辅导员确认后才替换正式值（BR-F06）。</p>
 */
@Data
@TableName("arc_profile_item")
public class ArcProfileItem {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属板块实例。 */
    private Long profileId;

    /** 字段编码，如 basic.phone。 */
    private String fieldCode;

    /** 文本类字段值。 */
    private String valueText;

    /** JSON 类字段值。 */
    private String valueJson;

    /** 待确认文本值。 */
    private String pendingText;

    /** 待确认 JSON 值。 */
    private String pendingJson;

    /** NONE / PENDING / APPROVED / REJECTED。 */
    private String confirmStatus;

    private Long confirmedBy;

    private LocalDateTime confirmedAt;

    private Long updatedBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** 是否存在待确认值。 */
    public boolean hasPending() {
        return "PENDING".equals(confirmStatus);
    }
}
