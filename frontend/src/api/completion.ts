import { request } from './http'
import type { CompletionRefreshResult, CompletionStatus } from './types'

/**
 * 完成标记接口（BR-S10）。
 *
 * <p>「六板块是否全部完成」由服务端判定并以 {@code arc_profile.completed} 落库，
 * 前端只读展示，不要在前端按 status 数组再数一遍。</p>
 */

/** 读取某学生某学年的完成标记与逐板块状态；学年为空取当前学年。 */
export function fetchCompletionStatus(
  studentId: number,
  academicYear?: string,
): Promise<CompletionStatus> {
  const params: Record<string, string> = {}
  if (academicYear) {
    params.academicYear = academicYear
  }
  return request<CompletionStatus>({
    url: `/completion/students/${studentId}`,
    method: 'GET',
    params,
  })
}

/**
 * 手动重算完成标记（运维补偿）。
 *
 * <p>正常流转路径不需要调用；{@code studentId} 为空表示整学年重算，
 * 仅持临时授权的管理员可用，辅导员只能传本班学生。</p>
 */
export function refreshCompletion(
  academicYear?: string,
  studentId?: number,
): Promise<CompletionRefreshResult> {
  const params: Record<string, string | number> = {}
  if (academicYear) {
    params.academicYear = academicYear
  }
  if (studentId) {
    params.studentId = studentId
  }
  return request<CompletionRefreshResult>({
    url: '/completion/refresh',
    method: 'POST',
    params,
  })
}
