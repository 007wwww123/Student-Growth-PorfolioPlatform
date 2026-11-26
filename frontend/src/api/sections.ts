import { request } from './http'
import type { PendingField, SectionCode, SectionVO, SensitiveField } from './types'

/**
 * 六大板块接口封装。
 *
 * 与后端 Controller 一一对应：每个板块三个接口，
 * GET / PUT / POST submit，路径统一为 /sections/{code}/{studentId}。
 * 以后板块增加接口时（如附件、审核），在同一函数组内追加即可。
 */

/** 查询板块详情。 */
export function fetchSection(
  code: SectionCode,
  studentId: number,
  academicYear?: string,
): Promise<SectionVO> {
  return request<SectionVO>({
    url: `/sections/${code}/${studentId}`,
    method: 'GET',
    params: academicYear ? { academicYear } : undefined,
  })
}

/** 保存草稿。payload 的字段名必须与后端 SaveDTO 一致。 */
export function saveSection(
  code: SectionCode,
  studentId: number,
  payload: Record<string, unknown>,
): Promise<void> {
  return request<void>({
    url: `/sections/${code}/${studentId}`,
    method: 'PUT',
    data: payload,
  })
}

/** 提交板块，由 DRAFT / RETURNED 转为 SUBMITTED。 */
export function submitSection(
  code: SectionCode,
  studentId: number,
  academicYear: string,
): Promise<void> {
  return request<void>({
    url: `/sections/${code}/${studentId}/submit`,
    method: 'POST',
    params: { academicYear },
  })
}

/**
 * 撤回提交（S-1-09）：仅学生本人，SUBMITTED 转 DRAFT。
 *
 * 全生命周期只允许一次，第二次返回 EC-40903，前端不要自行假设还能撤回。
 */
export function withdrawSection(
  code: SectionCode,
  studentId: number,
  academicYear: string,
): Promise<void> {
  return request<void>({
    url: `/sections/${code}/${studentId}/withdraw`,
    method: 'POST',
    params: { academicYear },
  })
}

/**
 * 确认待确认值（BR-F06）：仅该班辅导员。
 *
 * 学生修改自己提交的待确认字段后，新值先进入待确认区，由辅导员确认才生效。
 */
export function confirmPendingField(
  code: SectionCode,
  studentId: number,
  fieldCode: string,
  academicYear: string,
): Promise<PendingField> {
  return request<PendingField>({
    url: `/sections/${code}/${studentId}/fields/${fieldCode}/confirm`,
    method: 'POST',
    params: { academicYear },
  })
}

/** 驳回待确认值：丢弃新值，对外仍返回旧值。 */
export function rejectPendingField(
  code: SectionCode,
  studentId: number,
  fieldCode: string,
  academicYear: string,
): Promise<void> {
  return request<void>({
    url: `/sections/${code}/${studentId}/fields/${fieldCode}/reject`,
    method: 'POST',
    params: { academicYear },
  })
}

/**
 * 查看敏感字段明文（BR-D05）。
 *
 * 每次调用都会写一条审计日志，前端只在用户主动点击「查看完整」时调用，
 * 不要在列表或详情加载时批量预取。
 */
export function revealField(
  code: SectionCode,
  studentId: number,
  fieldCode: string,
  academicYear: string,
): Promise<SensitiveField> {
  return request<SensitiveField>({
    url: `/sections/${code}/${studentId}/fields/${fieldCode}/reveal`,
    method: 'GET',
    params: { academicYear },
  })
}
