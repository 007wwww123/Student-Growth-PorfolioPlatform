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
