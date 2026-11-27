import { request } from './http'
import type { DashboardOverview } from './types'

/**
 * 首页进度聚合接口（需求未编号，按「首页进度聚合」实现）。
 *
 * <p>一个请求取回首页所有数字。数据范围由服务端按角色收口，
 * 这里不传「看谁的数据」这类参数——辅导员永远只统计自己的带班。</p>
 */

/** 汇总本学年进度；辅导员只统计带班，管理员需持临时授权。 */
export function fetchDashboardOverview(
  academicYear?: string,
  classCode?: string,
): Promise<DashboardOverview> {
  const params: Record<string, string> = {}
  if (academicYear) {
    params.academicYear = academicYear
  }
  if (classCode) {
    params.classCode = classCode
  }
  return request<DashboardOverview>({
    url: '/dashboard/overview',
    method: 'GET',
    params,
  })
}
