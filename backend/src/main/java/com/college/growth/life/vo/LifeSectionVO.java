package com.college.growth.life.vo;

import java.util.Map;
import lombok.Data;

/**
 * 板块详情视图：生活心理（LIFE）。
 *
 * <p>骨架阶段字段值以 {@code fieldCode -> value} 的松散结构返回，
 * 待 {@code arc_profile_item} 与字段定义落地后，再按需求文档 4.5 收敛为强类型字段。</p>
 */
@Data
public class LifeSectionVO {

    private long version;
    private String returnReason;
    private java.util.List<Map<String, Object>> fieldDefinitions;

    /** 学生 ID。 */
    private Long studentId;

    /** 学年。 */
    private String academicYear;

    /** 板块编码，固定为 LIFE。 */
    private String sectionCode;

    /** 板块名称。 */
    private String sectionName;

    /** 板块状态，取值见 ProfileStatusEnum。 */
    private String status;

    /** 字段值集合，key 为字段编码（如 {@code life.xxx}）。 */
    private Map<String, Object> fields;

    /** 是否可编辑：由来源标记、角色、状态三层同时判定（BR-F01）。 */
    private Boolean editable;
}
