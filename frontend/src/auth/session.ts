import type { SessionUser } from '@/api/types'

/**
 * 登录状态维护。
 *
 * <p>令牌与用户信息放在 localStorage：刷新页面后不必重新登录，也不引入状态管理库。
 * 令牌本身由后端签发并校验，前端只负责保管与携带，不解析内容——解析出来的角色
 * 不能作为权限依据，权限判断始终在服务端。</p>
 *
 * <p>为什么不用内存变量：多标签页共享登录态，localStorage 是浏览器里最简单的实现；
 * 代价是同一浏览器的同源脚本可读，需配合 XSS 防护（不渲染不可信 HTML）。</p>
 */

const TOKEN_KEY = 'growth.token'

const USER_KEY = 'growth.user'

/** 提前判定过期的安全边界（秒），避免临界点请求才失败 */
const EXPIRY_MARGIN_SECONDS = 30

let cachedToken: string | null | undefined

let cachedUser: SessionUser | null | undefined

function safeGet(key: string): string | null {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}

function safeSet(key: string, value: string): void {
  try {
    localStorage.setItem(key, value)
  } catch {
    // 隐私模式下写入失败，退化为仅本次页面有效
  }
}

function safeRemove(key: string): void {
  try {
    localStorage.removeItem(key)
  } catch {
    // 忽略
  }
}

/** 从 JWT 的 exp 声明读过期时间，只用于提前清理，不做任何权限判断。 */
function expiresAtOf(token: string): number | null {
  const parts = token.split('.')
  if (parts.length !== 3) {
    return null
  }
  try {
    const payload = JSON.parse(atob(parts[1].replace(/-/g, '+').replace(/_/g, '/')))
    return typeof payload.exp === 'number' ? payload.exp : null
  } catch {
    return null
  }
}

/** 保存登录结果。 */
export function saveSession(token: string, user: SessionUser): void {
  cachedToken = token
  cachedUser = user
  safeSet(TOKEN_KEY, token)
  safeSet(USER_KEY, JSON.stringify(user))
}

/** 当前令牌；已过期或格式异常时返回 null 并清理本地状态。 */
export function getToken(): string | null {
  if (cachedToken !== undefined) {
    return validateToken(cachedToken)
  }
  cachedToken = safeGet(TOKEN_KEY)
  return validateToken(cachedToken)
}

/** mock 模式签发的假令牌形如 mock.<userId>.<role>.<0|1>：不是 JWT，真实后端一律拒绝。 */
function isMockToken(token: string): boolean {
  return token.startsWith('mock.')
}

function validateToken(token: string | null): string | null {
  if (!token) {
    return null
  }
  /**
   * 关掉 mock 后，浏览器里可能还留着上次 mock 登录的假令牌。
   * 它没有 JWT 的 exp 声明，会被当成「已登录」，随后第一个真实请求必然 401，
   * 表现为「登录后立刻被踢回登录页」；这里直接清掉，避免把残留状态当成会话。
   */
  if (import.meta.env.VITE_USE_MOCK !== 'true' && isMockToken(token)) {
    clearSession()
    return null
  }
  const expiresAt = expiresAtOf(token)
  if (expiresAt != null && expiresAt - EXPIRY_MARGIN_SECONDS <= Date.now() / 1000) {
    clearSession()
    return null
  }
  return token
}

/** 当前用户；没有登录态时返回 null。 */
export function getUser(): SessionUser | null {
  if (cachedUser !== undefined) {
    return cachedUser
  }
  const raw = safeGet(USER_KEY)
  if (!raw) {
    cachedUser = null
    return null
  }
  try {
    cachedUser = JSON.parse(raw) as SessionUser
    return cachedUser
  } catch {
    cachedUser = null
    return null
  }
}

/** 是否已登录（令牌存在且未过期）。 */
export function isLoggedIn(): boolean {
  return getToken() != null && getUser() != null
}

/** 清空登录态。 */
export function clearSession(): void {
  cachedToken = null
  cachedUser = null
  safeRemove(TOKEN_KEY)
  safeRemove(USER_KEY)
}

/** 当前用户是否属于给定角色之一。 */
export function hasRole(...roles: SessionUser['role'][]): boolean {
  const user = getUser()
  return user != null && roles.includes(user.role)
}
