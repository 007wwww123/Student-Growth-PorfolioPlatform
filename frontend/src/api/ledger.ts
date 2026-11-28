import { request } from './http'
import type { LedgerClassRow, LedgerRow, LedgerTaskQuery, PageResult } from './types'

/**
 * 班级台账接口封装（F-07 / AC-020）。
 *
 * <p>两个接口都只传学年、筛选与分页，不传「看谁的数据」：
 * 数据范围由服务端按登录身份收口，辅导员永远只看到本班。
 * 前端再过滤一遍会导致「页面看不到、接口其实查得到」的错觉，因此不复制权限逻辑。</p>
 */

/** 班级完成度汇总；辅导员只返回带班，管理员需持临时授权。 */
export function fetchLedgerClasses(academicYear?: string): Promise<LedgerClassRow[]> {
  const params: Record<string, string> = {}
  if (academicYear) {
    params.academicYear = academicYear
  }
  return request<LedgerClassRow[]>({
    url: '/ledger/classes',
    method: 'GET',
    params,
  })
}

/** 台账明细（学生 × 板块，含未建档行）；total 与筛选条件同源，可直接展示。 */
export function fetchLedgerTasks(query: LedgerTaskQuery = {}): Promise<PageResult<LedgerRow>> {
  const params: Record<string, string | number> = {}
  if (query.academicYear) params.academicYear = query.academicYear
  if (query.classCode) params.classCode = query.classCode
  if (query.status) params.status = query.status
  if (query.keyword) params.keyword = query.keyword
  if (query.pageNum) params.pageNum = query.pageNum
  if (query.pageSize) params.pageSize = query.pageSize

  return request<PageResult<LedgerRow>>({
    url: '/ledger/tasks',
    method: 'GET',
    params,
  })
}
