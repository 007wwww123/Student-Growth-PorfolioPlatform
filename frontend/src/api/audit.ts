import { request } from './http'
import type { PageResult } from './types'

/** 审计与登录日志（F-14）。 */

export interface AuditLog {
  id: number
  bizType: string
  targetType?: string
  targetId?: number
  studentId?: number
  sectionCode?: string
  fieldCode?: string
  action: string
  detail?: string
  operatorId?: number
  operatorRole?: string
  traceId?: string
  createdAt: string
}

export interface LoginLog {
  id: number
  userId?: number
  username?: string
  success: number
  reason?: string
  ip?: string
  userAgent?: string
  createdAt: string
}

export interface AuditQuery {
  bizType?: string
  action?: string
  studentId?: number
  operatorId?: number
  from?: string
  to?: string
  pageNum?: number
  pageSize?: number
}

export interface LoginQuery {
  username?: string
  success?: number
  from?: string
  to?: string
  pageNum?: number
  pageSize?: number
}

export function fetchAuditLogs(query: AuditQuery): Promise<PageResult<AuditLog>> {
  return request<PageResult<AuditLog>>({ url: '/audit/logs', method: 'GET', params: query })
}

export function fetchLoginLogs(query: LoginQuery): Promise<PageResult<LoginLog>> {
  return request<PageResult<LoginLog>>({ url: '/audit/login-logs', method: 'GET', params: query })
}
