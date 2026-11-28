package com.college.growth.permission.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明接口所需的权限点（需求 7.2）。
 *
 * <p>由 {@code PermissionInterceptor} 在进入 Controller 前校验；没有该注解的接口
 * 仍沿用原有角色/范围判定，便于逐步迁移，不会因为引入权限点而一次性改动全部业务。</p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /** 权限码，如 {@code org:class:manage}。 */
    String value();
}
