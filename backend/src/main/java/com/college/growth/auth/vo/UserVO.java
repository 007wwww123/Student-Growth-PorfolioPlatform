package com.college.growth.auth.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 当前登录用户。
 *
 * <p>只含前端渲染所需信息：角色决定菜单与按钮，studentId 决定学生端读哪一份档案。
 * 不含手机号、身份证等业务字段——那些仍走板块接口并按字段权限脱敏。</p>
 */
@Data
@AllArgsConstructor
public class UserVO {

    private Long userId;

    private String username;

    private String displayName;

    /** STU / COU / ADMIN。 */
    private String role;

    /** 角色中文名，前端展示用。 */
    private String roleLabel;

    /** 学生账号关联的学生 ID，其他角色为空。 */
    private Long studentId;

    /** 管理员是否持临时只读授权。 */
    private boolean tempGrant;
}
