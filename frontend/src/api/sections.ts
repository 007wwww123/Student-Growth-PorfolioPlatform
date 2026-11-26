import { request } from './http'
import type { SectionCode, SectionVO } from './types'

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
