import { request } from './http'
import type { PageResult, UserRole } from './types'

/** 账号管理（F-03）接口与视图类型。 */

export interface AccountUser {
  id: number
  username: string
  displayName: string
  role: UserRole
  roleLabel: string
  studentId?: number
  studentNo?: string
  classCode?: string
  className?: string
  status: number
  lastLoginAt?: string
}

export interface CreateAccountPayload {
  username?: string
  displayName: string
  role: UserRole
  studentId?: number
  password?: string
}

export interface CreateAccountResult {
  id: number
  username: string
  displayName: string
  role: string
  roleLabel: string
  initialPassword: string
}

export interface ResetPasswordResult {
  userId: number
  username: string
  newPassword: string
}

export interface BatchOpenResult {
  totalStudents: number
  created: number
  skipped: number
  accounts: Array<{
    studentId: number
    studentNo: string
    studentName: string
    username: string
    initialPassword: string
  }>
}

export interface AccountQuery {
  role?: string
  status?: number
  keyword?: string
  pageNum?: number
  pageSize?: number
}

export function fetchAccounts(query: AccountQuery): Promise<PageResult<AccountUser>> {
  return request<PageResult<AccountUser>>({ url: '/account/users', method: 'GET', params: query })
}

export function createAccount(payload: CreateAccountPayload): Promise<CreateAccountResult> {
  return request<CreateAccountResult>({ url: '/account/users', method: 'POST', data: payload })
}

export function updateAccountStatus(id: number, status: number): Promise<void> {
  return request<void>({ url: `/account/users/${id}/status`, method: 'PUT', data: { status } })
}

export function resetAccountPassword(id: number): Promise<ResetPasswordResult> {
  return request<ResetPasswordResult>({
    url: `/account/users/${id}/reset-password`,
    method: 'POST',
  })
}

export function assignAccountRole(id: number, role: UserRole): Promise<void> {
  return request<void>({ url: `/account/users/${id}/role`, method: 'PUT', data: { role } })
}

export function batchOpenAccounts(classCodes: string[]): Promise<BatchOpenResult> {
  return request<BatchOpenResult>({
    url: '/account/batch-open',
    method: 'POST',
    data: { classCodes },
  })
}
