package com.college.growth.common.enums;

/**
 * 档案板块五状态（需求文档 5.2 状态机）。
 *
 * <p>状态集合固定为五个，禁止新增状态；任何"临时状态"用字段标记表达，不扩展状态机。</p>
 */
public enum ProfileStatusEnum {

    /** 草稿：可写字段值。 */
    DRAFT("草稿"),
    /** 已提交：待辅导员审核，字段锁定。 */
    SUBMITTED("已提交"),
    /** 已退回：可修改后重提，不消耗撤回次数。 */
    RETURNED("已退回"),
    /** 已通过：内容更正须走审批并留原值。 */
    APPROVED("已通过"),
    /** 已归档：终态，仅 ADMIN 可解冻。 */
    LOCKED("已归档");

    private final String label;

    ProfileStatusEnum(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** BR-S01：仅 DRAFT、RETURNED 允许写字段值。 */
    public boolean isWritable() {
        return this == DRAFT || this == RETURNED;
    }
}
