import { request } from './http'
import type { ExportTask } from './export'

/** 班级 Excel 与敏感列审批（F-13）。 */

export interface ClassExportApproval {
  id: number
  applicantId: number
  applicantName?: string
  classCode: string
  reason: string
  status: string
  approverName?: string
  approverRemark?: string
  expireAt?: string
  createdAt: string
  active: boolean
}

export interface ClassExportRequest {
  classCode: string
  academicYear: string
  columns: string[]
  approvalId?: number
}

export function createClassExport(body: ClassExportRequest): Promise<ExportTask> {
  return request<ExportTask>({ url: '/export/class', method: 'POST', data: body })
}

export function applySensitiveExport(classCode: string, reason: string): Promise<ClassExportApproval> {
  return request<ClassExportApproval>({
    url: '/export/class/approvals',
    method: 'POST',
    data: { classCode, reason },
  })
}

export function fetchClassExportApprovals(status = ''): Promise<ClassExportApproval[]> {
  return request<ClassExportApproval[]>({
    url: '/export/class/approvals',
    method: 'GET',
    params: { status: status || undefined },
  })
}

export function approveClassExport(id: number, remark: string): Promise<ClassExportApproval> {
  return request<ClassExportApproval>({
    url: `/export/class/approvals/${id}/approve`,
    method: 'POST',
    data: { remark },
  })
}

export function rejectClassExport(id: number, remark: string): Promise<ClassExportApproval> {
  return request<ClassExportApproval>({
    url: `/export/class/approvals/${id}/reject`,
    method: 'POST',
    data: { remark },
  })
}
