import { request } from './http'

/** 临时授权（F-17）。 */

export interface TempGrant {
  id: number
  applicantId: number
  applicantName?: string
  hours: number
  reason: string
  status: string
  approverId?: number
  approverName?: string
  approverRemark?: string
  approvedAt?: string
  expireAt?: string
  createdAt: string
  active: boolean
}

export function applyTempGrant(hours: number, reason: string): Promise<TempGrant> {
  return request<TempGrant>({
    url: '/admin/temp-grants/applications',
    method: 'POST',
    data: { hours, reason },
  })
}

export function fetchTempGrants(): Promise<TempGrant[]> {
  return request<TempGrant[]>({ url: '/admin/temp-grants/applications', method: 'GET' })
}

export function fetchActiveTempGrant(): Promise<TempGrant[]> {
  return request<TempGrant[]>({ url: '/admin/temp-grants/active', method: 'GET' })
}

export function approveTempGrant(id: number, remark: string): Promise<TempGrant> {
  return request<TempGrant>({
    url: `/admin/temp-grants/applications/${id}/approve`,
    method: 'POST',
    data: { remark },
  })
}

export function rejectTempGrant(id: number, remark: string): Promise<TempGrant> {
  return request<TempGrant>({
    url: `/admin/temp-grants/applications/${id}/reject`,
    method: 'POST',
    data: { remark },
  })
}

export function revokeTempGrant(id: number): Promise<TempGrant> {
  return request<TempGrant>({
    url: `/admin/temp-grants/applications/${id}/revoke`,
    method: 'POST',
  })
}
