package com.college.growth.common.context;

import com.college.growth.common.enums.UserRoleEnum;

/**
 * 当前请求的操作人。
 *
 * @param userId    用户 ID；骨架期匿名请求为 0
 * @param role      角色，决定字段级可见与可编辑
 * @param tempGrant 管理员是否持有临时只读授权（审批通过后最长 4 小时）
 */
public record CurrentUser(Long userId, UserRoleEnum role, boolean tempGrant) {

    /** 是否学生本人视角。 */
    public boolean isStudent() {
        return role == UserRoleEnum.STU;
    }

    /** 是否辅导员视角。 */
    public boolean isCounselor() {
        return role == UserRoleEnum.COU;
    }

    /** 管理员是否可读业务数据：必须持有临时授权。 */
    public boolean isAdminWithGrant() {
        return role == UserRoleEnum.ADMIN && tempGrant;
    }

    /** 是否具备读取业务数据的资格。 */
    public boolean canReadBusinessData() {
        return isStudent() || isCounselor() || isAdminWithGrant();
    }
}
