package com.college.growth.permission.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 角色权限绑定（{@code sys_role_permission}，脚本 15）。 */
@Data
@TableName("sys_role_permission")
public class SysRolePermission {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** STU / COU / ADMIN。 */
    private String role;

    private String permissionCode;

    /** 数据范围：ALL / CLASS / SELF。 */
    private String scope;

    private LocalDateTime createdAt;
}
