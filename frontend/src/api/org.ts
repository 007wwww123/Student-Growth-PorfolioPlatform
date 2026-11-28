import { request } from './http'
import type { SessionUser } from './types'

/** 组织架构（F-02）接口与视图类型。 */

export interface OrgCollege {
  id: number
  collegeCode: string
  collegeName: string
  status: number
  majorCount?: number
}

export interface OrgMajor {
  id: number
  collegeId: number
  collegeName?: string
  majorCode: string
  majorName: string
  status: number
}

export interface OrgClassRow {
  id: number
  classCode: string
  className: string
  collegeId?: number
  collegeName?: string
  majorId?: number
  majorName?: string
  gradeYear: number
  counselorUserId?: number
  counselorName?: string
  status: number
  studentCount: number
}

export interface OrgOptions {
  colleges: OrgCollege[]
  majors: OrgMajor[]
  classes: OrgClassRow[]
  counselors: SessionUser[]
}

export interface CollegePayload {
  collegeCode: string
  collegeName: string
}

export interface MajorPayload {
  collegeId: number
  majorCode: string
  majorName: string
}

export interface ClassPayload {
  classCode: string
  className: string
  majorId: number
  gradeYear: number
  counselorUserId?: number
}

export function fetchOrgOptions(): Promise<OrgOptions> {
  return request<OrgOptions>({ url: '/org/options', method: 'GET' })
}

export function createCollege(payload: CollegePayload): Promise<OrgCollege> {
  return request<OrgCollege>({ url: '/org/colleges', method: 'POST', data: payload })
}

export function updateCollege(id: number, payload: CollegePayload): Promise<OrgCollege> {
  return request<OrgCollege>({ url: `/org/colleges/${id}`, method: 'PUT', data: payload })
}

export function updateCollegeStatus(id: number, status: number): Promise<void> {
  return request<void>({ url: `/org/colleges/${id}/status`, method: 'PUT', data: { status } })
}

export function createMajor(payload: MajorPayload): Promise<OrgMajor> {
  return request<OrgMajor>({ url: '/org/majors', method: 'POST', data: payload })
}

export function updateMajor(id: number, payload: MajorPayload): Promise<OrgMajor> {
  return request<OrgMajor>({ url: `/org/majors/${id}`, method: 'PUT', data: payload })
}

export function updateMajorStatus(id: number, status: number): Promise<void> {
  return request<void>({ url: `/org/majors/${id}/status`, method: 'PUT', data: { status } })
}

export function createClass(payload: ClassPayload): Promise<OrgClassRow> {
  return request<OrgClassRow>({ url: '/org/classes', method: 'POST', data: payload })
}

export function updateClass(id: number, payload: ClassPayload): Promise<OrgClassRow> {
  return request<OrgClassRow>({ url: `/org/classes/${id}`, method: 'PUT', data: payload })
}

export function assignCounselor(id: number, counselorUserId: number): Promise<OrgClassRow> {
  return request<OrgClassRow>({
    url: `/org/classes/${id}/counselor`,
    method: 'PUT',
    data: { counselorUserId },
  })
}

export function updateClassStatus(id: number, status: number): Promise<void> {
  return request<void>({ url: `/org/classes/${id}/status`, method: 'PUT', data: { status } })
}
