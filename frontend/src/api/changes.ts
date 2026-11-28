import { request } from './http'
import type { PageResult } from './types'

/** 变更记录与申诉（F-18）。 */

export interface ChangeLog {
  id: number
  studentId: number
  studentName?: string
  sectionCode: string
  sectionName?: string
  bizType: string
  fieldCode?: string
  fieldName?: string
  oldValue?: string
  newValue?: string
  fromStatus?: string
  toStatus?: string
  operatorId?: number
  operatorName?: string
  operatorRole?: string
  source?: string
  reason?: string
  createdAt: string
  appealId?: number
  appealStatus?: string
}

export interface Appeal {
  id: number
  changeId: number
  studentId: number
  studentName?: string
  sectionName?: string
  fieldName?: string
  oldValue?: string
  newValue?: string
  reason: string
  status: string
  approverName?: string
  approverRemark?: string
  createdAt: string
  approvedAt?: string
}

export interface ChangeQuery {
  studentId?: number
  sectionCode?: string
  bizType?: string
  from?: string
  to?: string
  pageNum?: number
  pageSize?: number
}

export function fetchChanges(query: ChangeQuery): Promise<PageResult<ChangeLog>> {
  return request<PageResult<ChangeLog>>({ url: '/changes', method: 'GET', params: query })
}

export function createAppeal(changeId: number, reason: string): Promise<Appeal> {
  return request<Appeal>({
    url: `/changes/${changeId}/appeals`,
    method: 'POST',
    data: { reason },
  })
}

export function fetchAppeals(status = '', pageNum = 1, pageSize = 20): Promise<PageResult<Appeal>> {
  return request<PageResult<Appeal>>({
    url: '/changes/appeals',
    method: 'GET',
    params: { status: status || undefined, pageNum, pageSize },
  })
}

export function approveAppeal(id: number, remark: string): Promise<Appeal> {
  return request<Appeal>({ url: `/changes/appeals/${id}/approve`, method: 'POST', data: { remark } })
}

export function rejectAppeal(id: number, remark: string): Promise<Appeal> {
  return request<Appeal>({ url: `/changes/appeals/${id}/reject`, method: 'POST', data: { remark } })
}
