package com.college.growth.common.enums;

/**
 * 平台三类角色（需求文档 2.1）。
 *
 * <p>角色决定字段级可见与可编辑（BR-F01）。管理员只管系统、配置与权限，
 * 默认不具备业务数据权限，因此不出现在任何字段的 {@code visible_roles} 中。</p>
 */
public enum UserRoleEnum {

    /** 管理员：系统、配置、权限、审计；默认无业务数据权限。 */
    ADMIN("管理员"),
    /** 辅导员：维护本班学生档案，审核提交内容。 */
    COU("辅导员"),
    /** 学生：填写与提交本人档案，只读字段由学校维护。 */
    STU("学生");

    private final String label;

    UserRoleEnum(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 解析角色标识，非法值回退为学生。
     *
     * <p>入参来自 {@code sys_user.role} 与令牌载荷，不再来自请求头；
     * 非法值回退而不是抛异常，是为了避免脏数据把整个登录流程卡死，
     * 但调用方应保证写入时已经过校验。</p>
     */
    public static UserRoleEnum fromCode(String value) {
        if (value == null || value.isBlank()) {
            return STU;
        }
        for (UserRoleEnum role : values()) {
            if (role.name().equalsIgnoreCase(value.trim())) {
                return role;
            }
        }
        return STU;
    }
}
