import { request } from './http'

/** 归档解冻审批（B-6）。 */

export interface UnfreezeRequest {
  id: number
  studentId: number
  studentName?: string
  academicYear: string
  applicantId: number
  applicantName?: string
  reason: string
  status: string
  approverName?: string
  approverRemark?: string
  createdAt: string
}

export function applyUnfreeze(studentId: number, academicYear: string, reason: string): Promise<UnfreezeRequest> {
  return request<UnfreezeRequest>({
    url: '/archive/unfreeze',
    method: 'POST',
    data: { studentId, academicYear, reason },
  })
}

export function fetchUnfreezeRequests(): Promise<UnfreezeRequest[]> {
  return request<UnfreezeRequest[]>({ url: '/archive/unfreeze', method: 'GET' })
}

export function approveUnfreeze(id: number, remark: string): Promise<UnfreezeRequest> {
  return request<UnfreezeRequest>({
    url: `/archive/unfreeze/${id}/approve`,
    method: 'POST',
    data: { remark },
  })
}

export function rejectUnfreeze(id: number, remark: string): Promise<UnfreezeRequest> {
  return request<UnfreezeRequest>({
    url: `/archive/unfreeze/${id}/reject`,
    method: 'POST',
    data: { remark },
  })
}
