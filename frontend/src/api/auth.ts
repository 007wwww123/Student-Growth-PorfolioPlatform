import { request } from './http'
import type { LoginResult, SessionUser } from './types'

/**
 * 登录与会话接口（S-1-02）。
 *
 * <p>只有 {@code /auth/login} 可以匿名访问；{@code /auth/me} 与 {@code /auth/logout}
 * 依赖请求头里的 Bearer 令牌，由 http.ts 统一注入。</p>
 */

/** 账号密码登录。 */
export function login(username: string, password: string): Promise<LoginResult> {
  return request<LoginResult>({
    url: '/auth/login',
    method: 'POST',
    data: { username, password },
  })
}

/** 刷新页面后恢复会话，顺带确认账号仍处于启用状态。 */
export function fetchMe(): Promise<SessionUser> {
  return request<SessionUser>({
    url: '/auth/me',
    method: 'GET',
  })
}

/** 登出：后端把令牌写入吊销名单，前端清本地状态。 */
export function logout(): Promise<void> {
  return request<void>({
    url: '/auth/logout',
    method: 'POST',
  })
}

/**
 * 修改本人密码（F-01 / auth:password:self）。
 *
 * <p>后端改密成功后令牌版本号加一，旧令牌立即失效，因此这里返回新的登录结果，
 * 调用方必须用它覆盖本地会话，否则会被下一个 401 踢回登录页。</p>
 */
export function changePassword(oldPassword: string, newPassword: string): Promise<LoginResult> {
  return request<LoginResult>({
    url: '/auth/password',
    method: 'POST',
    data: { oldPassword, newPassword },
  })
}
