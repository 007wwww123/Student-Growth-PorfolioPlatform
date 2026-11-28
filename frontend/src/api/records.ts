import { request } from './http'

/** 学业成果（成绩/证书/竞赛）接口与类型。 */

export interface Rank {
  id: number
  academicYear: string
  classRank?: number
  majorRank?: number
  gpa?: number
  source: string
}

export interface Certificate {
  id: number
  studentId: number
  certType: string
  certName: string
  score?: string
  obtainDate: string
  verifyStatus: 'PENDING' | 'PASSED' | 'REJECTED'
  verifyRemark?: string
  verifyAt?: string
}

export interface AcademicEvent {
  id: number
  studentId: number
  eventType: string
  title: string
  roleName?: string
  level: string
  startDate: string
  endDate?: string
  description?: string
  verifyStatus: 'PENDING' | 'PASSED' | 'REJECTED'
  verifyRemark?: string
}

export interface AcademicRecords {
  studentId: number
  ranks: Rank[]
  certificates: Certificate[]
  events: AcademicEvent[]
  passedCertificates: number
  passedEvents: number
}

export interface CertificatePayload {
  certType: string
  certName: string
  score?: string
  obtainDate: string
}

export interface EventPayload {
  eventType: string
  title: string
  roleName?: string
  level: string
  startDate: string
  endDate?: string
  description?: string
}

export function fetchAcademicRecords(studentId: number): Promise<AcademicRecords> {
  return request<AcademicRecords>({ url: `/academic/records/${studentId}`, method: 'GET' })
}

export function createCertificate(studentId: number, payload: CertificatePayload): Promise<Certificate> {
  return request<Certificate>({
    url: `/academic/records/${studentId}/certificates`,
    method: 'POST',
    data: payload,
  })
}

export function verifyCertificate(
  studentId: number,
  id: number,
  status: 'PASSED' | 'REJECTED',
  remark?: string,
): Promise<Certificate> {
  return request<Certificate>({
    url: `/academic/records/${studentId}/certificates/${id}/verify`,
    method: 'PUT',
    data: { status, remark },
  })
}

export function createEvent(studentId: number, payload: EventPayload): Promise<AcademicEvent> {
  return request<AcademicEvent>({
    url: `/academic/records/${studentId}/events`,
    method: 'POST',
    data: payload,
  })
}

export function verifyEvent(
  studentId: number,
  id: number,
  status: 'PASSED' | 'REJECTED',
  remark?: string,
): Promise<AcademicEvent> {
  return request<AcademicEvent>({
    url: `/academic/records/${studentId}/events/${id}/verify`,
    method: 'PUT',
    data: { status, remark },
  })
}
