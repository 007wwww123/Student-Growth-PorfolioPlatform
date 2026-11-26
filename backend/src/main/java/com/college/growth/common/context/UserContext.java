package com.college.growth.common.context;

import com.college.growth.common.enums.UserRoleEnum;

/**
 * 当前请求用户上下文。
 *
 * <p>由 {@code UserContextFilter} 在请求进入时写入、返回时清理，业务代码只读，
 * 禁止在 Service 内部改写，避免权限判断出现两个来源。</p>
 */
public final class UserContext {

    private static final CurrentUser ANONYMOUS = new CurrentUser(0L, UserRoleEnum.STU, false);

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    /**
     * 取当前用户。
     *
     * <p>受保护接口由 {@code JwtAuthFilter} 保证上下文一定存在；这里的匿名兜底只服务于
     * 内部调用与单元测试。业务代码若拿到 {@code userId=0}，应当按无权限处理，
     * 不要把它当成「默认学生」。</p>
     */
    public static CurrentUser get() {
        CurrentUser user = HOLDER.get();
        return user == null ? ANONYMOUS : user;
    }

    /** 是否已写入上下文（过滤器内使用）。 */
    public static boolean isPresent() {
        return HOLDER.get() != null;
    }

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    /** 必须在 finally 中调用，防止线程复用导致越权。 */
    public static void clear() {
        HOLDER.remove();
    }
}
