import { request } from './http'
import type { ArchiveCandidate, ArchiveResult } from './types'

/**
 * 毕业归档接口（S-1-11）。
 *
 * 与后端 {@code ArchiveController} 一一对应：先看候选人完成情况，再提交名单执行归档。
 * 名单内只要有一人未完成，整批不执行并返回未完成清单（EX-12）。
 */

/** 归档候选人：本班学生 + 六板块完成情况。辅导员只看本班，管理员需临时授权。 */
export function fetchArchiveCandidates(
  academicYear?: string,
  classCode?: string,
): Promise<ArchiveCandidate[]> {
  return request<ArchiveCandidate[]>({
    url: '/archive/candidates',
    method: 'GET',
    params: {
      ...(academicYear ? { academicYear } : {}),
      ...(classCode ? { classCode } : {}),
    },
  })
}

/** 批量归档：六板块全部通过才执行，任一未完成整批拒绝。 */
export function archiveBatch(payload: {
  academicYear: string
  studentIds: number[]
}): Promise<ArchiveResult> {
  return request<ArchiveResult>({
    url: '/archive/batch',
    method: 'POST',
    data: payload,
  })
}
