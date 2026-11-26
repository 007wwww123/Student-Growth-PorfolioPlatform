import { HttpResponse, http } from 'msw'
import { API_BASE_PATH, SUCCESS_CODE } from '../api/constants'
import type { FieldErrorDetail, SectionCode } from '../api/types'
import { MockError, getSection, saveSection, submitSection } from './data'

/**
 * MSW 请求处理器。
 *
 * 覆盖后端已声明的六个板块 × 三个接口（查详情 / 存草稿 / 提交）。
 * 路径、响应结构、错误码全部对齐 openapi.yaml 与后端 ResultCode，
 * 后端接口就绪后无需改页面，只需关闭 VITE_USE_MOCK。
 */

const SECTION_CODES: SectionCode[] = ['basic', 'academic', 'interest', 'life', 'career', 'mentor']

function traceId(): string {
  const random = () => Math.random().toString(16).slice(2, 10).padEnd(8, '0')
  return `${random()}${random()}`.slice(0, 16)
}

/** 与后端 Result.timestamp 一致：ISO-8601 带时区偏移。 */
function timestamp(): string {
  const now = new Date()
  const pad = (value: number) => String(Math.floor(Math.abs(value))).padStart(2, '0')
  const offsetMinutes = -now.getTimezoneOffset()
  const sign = offsetMinutes >= 0 ? '+' : '-'
  return (
    `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}` +
    `T${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}` +
    `${sign}${pad(offsetMinutes / 60)}:${pad(offsetMinutes % 60)}`
  )
}

function ok(data?: unknown) {
  return HttpResponse.json({
    code: SUCCESS_CODE,
    message: '操作成功',
    traceId: traceId(),
    timestamp: timestamp(),
    ...(data === undefined ? {} : { data }),
  })
}

function fail(
  code: string,
  message: string,
  status: number,
  details?: FieldErrorDetail[],
) {
  return HttpResponse.json(
    {
      code,
      message,
      traceId: traceId(),
      timestamp: timestamp(),
      ...(details ? { details } : {}),
    },
    { status },
  )
}

function parseCode(raw: string | readonly string[]): SectionCode | null {
  const value = (Array.isArray(raw) ? raw[0] : raw) as SectionCode
  return SECTION_CODES.includes(value) ? value : null
}

function parseId(raw: string | readonly string[]): number | null {
  const value = Number(Array.isArray(raw) ? raw[0] : raw)
  return Number.isInteger(value) && value > 0 ? value : null
}

/** 基础校验，用于演示 EC-42201 的字段级错误结构。 */
function validate(code: SectionCode, body: Record<string, unknown>): FieldErrorDetail[] {
  if (code !== 'basic') {
    return []
  }

  const details: FieldErrorDetail[] = []
  if (!String(body.name ?? '').trim()) {
    details.push({ field: 'name', reason: 'REQUIRED', message: '请输入姓名' })
  }
  if (!String(body.studentNo ?? '').trim()) {
    details.push({ field: 'studentNo', reason: 'REQUIRED', message: '请输入学号' })
  }

  const email = String(body.email ?? '').trim()
  if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    details.push({ field: 'email', reason: 'FORMAT', message: '邮箱格式不正确' })
  }

  return details
}

export const handlers = [
  http.get(`${API_BASE_PATH}/sections/:code/:studentId`, ({ params, request }) => {
    const code = parseCode(params.code as string)
    const studentId = parseId(params.studentId as string)
    if (!code) {
      return fail('EC-40003', '板块编码不在允许范围内', 400)
    }
    if (!studentId) {
      return fail('EC-40003', '学生 ID 必须为正整数', 400)
    }

    const url = new URL(request.url)
    const academicYear = url.searchParams.get('academicYear') ?? '2025-2026-1'
    return ok(getSection(code, studentId, academicYear))
  }),

  http.put(`${API_BASE_PATH}/sections/:code/:studentId`, async ({ params, request }) => {
    const code = parseCode(params.code as string)
    const studentId = parseId(params.studentId as string)
    if (!code) {
      return fail('EC-40003', '板块编码不在允许范围内', 400)
    }
    if (!studentId) {
      return fail('EC-40003', '学生 ID 必须为正整数', 400)
    }

    let body: Record<string, unknown>
    try {
      body = (await request.json()) as Record<string, unknown>
    } catch {
      return fail('EC-40001', '请求格式不正确，请刷新页面重试', 400)
    }

    const details = validate(code, body)
    if (details.length > 0) {
      return fail('EC-42201', '字段校验未通过', 422, details)
    }

    const academicYear = String(body.academicYear ?? '2025-2026-1')
    try {
      saveSection(code, studentId, academicYear, body)
      return ok()
    } catch (error) {
      if (error instanceof MockError) {
        return fail(error.code, error.message, error.httpStatus)
      }
      throw error
    }
  }),

  http.post(`${API_BASE_PATH}/sections/:code/:studentId/submit`, ({ params, request }) => {
    const code = parseCode(params.code as string)
    const studentId = parseId(params.studentId as string)
    if (!code) {
      return fail('EC-40003', '板块编码不在允许范围内', 400)
    }
    if (!studentId) {
      return fail('EC-40003', '学生 ID 必须为正整数', 400)
    }

    const url = new URL(request.url)
    const academicYear = url.searchParams.get('academicYear')
    if (!academicYear) {
      return fail('EC-40002', '缺少必要参数：academicYear', 400)
    }

    try {
      submitSection(code, studentId, academicYear)
      return ok()
    } catch (error) {
      if (error instanceof MockError) {
        return fail(error.code, error.message, error.httpStatus)
      }
      throw error
    }
  }),
]
