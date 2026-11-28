package com.college.growth.permission.vo;

import lombok.Data;

/** 权限点视图（当前用户的授权或管理员看到的字典）。 */
@Data
public class PermissionVO {

    private String code;

    private String name;

    private String module;

    private String description;

    /** 当前角色的数据范围：ALL / CLASS / SELF；字典视图为 null。 */
    private String scope;
}
