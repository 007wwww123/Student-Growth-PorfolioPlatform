package com.college.growth.permission.common;

/**
 * 字段编辑权限配置常量。
 *
 * <p>权限配置只解决一个问题：某个板块的某个字段，允许哪些角色直接修改内容。
 * 角色只有学生（STU）与辅导员（COU）两类，管理员永远不在可编辑角色中——
 * 管理员负责配置权限，不参与档案内容编辑（需求文档 2.1）。</p>
 */
public final class PermissionConstants {

    /** 接口基础路径。 */
    public static final String API_BASE_PATH = "/admin/permissions";

    /** 学生角色。 */
    public static final String ROLE_STUDENT = "STU";

    /** 辅导员（老师）角色。 */
    public static final String ROLE_COUNSELOR = "COU";

    /** 管理员角色：禁止写入 editable_roles。 */
    public static final String ROLE_ADMIN = "ADMIN";

    /** 权限模式：均不可编辑。 */
    public static final String MODE_NONE = "NONE";

    /** 权限模式：仅学生可编辑。 */
    public static final String MODE_STUDENT = "STU";

    /** 权限模式：仅老师可编辑。 */
    public static final String MODE_COUNSELOR = "COU";

    /** 权限模式：师生均可编辑。 */
    public static final String MODE_BOTH = "BOTH";

    private PermissionConstants() {
    }
}
