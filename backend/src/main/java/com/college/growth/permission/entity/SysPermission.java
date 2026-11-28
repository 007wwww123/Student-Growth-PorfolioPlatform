package com.college.growth.permission.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 权限点字典（{@code sys_permission}，脚本 15）。 */
@Data
@TableName("sys_permission")
public class SysPermission {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 权限码，如 org:class:manage。 */
    private String code;

    private String name;

    /** 所属模块：auth/org/account/config/archive/import/export/audit/grant/stat/system。 */
    private String module;

    private String description;

    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
