import { request } from './http'
import type { PageResult, ReviewDecision, ReviewTask, SectionCode } from './types'

/**
 * 审核接口封装（S-1-06 / S-1-07）。
 *
 * <p>审核是跨板块能力，路径为 {@code /review/...}，板块只作为参数出现，
 * 六个板块共用同一套接口，不要按板块复制文件。</p>
 */

/** 退回理由最小长度（BR-S04），与后端 ReviewConstants.REASON_MIN_LENGTH 一致。 */
export const REASON_MIN_LENGTH = 10

/** 审核意见最大长度，与 arc_change_log.reason 列宽一致。 */
export const COMMENT_MAX_LENGTH = 255

/** 待审列表筛选条件。 */
export interface ReviewTaskQuery {
  /** 板块编码，大小写均可；为空表示全部板块 */
  sectionCode?: string
  /** 状态，默认 SUBMITTED */
  status?: string
  /** 班级编码；辅导员的数据范围始终限定在本班，传了也不会越权 */
  classId?: string
  academicYear?: string
  pageNum?: number
  pageSize?: number
}

/** 查询待审列表。辅导员只看本班，管理员需持临时授权。 */
export function fetchReviewTasks(query: ReviewTaskQuery = {}): Promise<PageResult<ReviewTask>> {
  const params: Record<string, string | number> = {}
  if (query.sectionCode) params.sectionCode = query.sectionCode
  if (query.status) params.status = query.status
  if (query.classId) params.classId = query.classId
  if (query.academicYear) params.academicYear = query.academicYear
  if (query.pageNum) params.pageNum = query.pageNum
  if (query.pageSize) params.pageSize = query.pageSize

  return request<PageResult<ReviewTask>>({
    url: '/review/tasks',
    method: 'GET',
    params,
  })
}

/**
 * 审核通过（S-1-07）。
 *
 * 注意学年在请求体里，不在 query——与板块详情接口的约定不同。
 */
export function approveSection(
  code: SectionCode,
  studentId: number,
  decision: ReviewDecision = {},
): Promise<void> {
  return request<void>({
    url: `/review/${code}/${studentId}/approve`,
    method: 'POST',
    data: decision,
  })
}

/** 审核退回：理由必填且不少于 10 字，学生可见（BR-S04）。 */
export function rejectSection(
  code: SectionCode,
  studentId: number,
  decision: ReviewDecision,
): Promise<void> {
  return request<void>({
    url: `/review/${code}/${studentId}/reject`,
    method: 'POST',
    data: decision,
  })
}
