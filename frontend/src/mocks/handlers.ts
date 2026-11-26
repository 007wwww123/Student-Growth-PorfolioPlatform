import { HttpResponse, http } from 'msw'
import { API_BASE_PATH, SUCCESS_CODE } from '../api/constants'
import type { FieldErrorDetail, SectionCode, UserRole } from '../api/types'
import {
  MockError,
  approveSection,
  getSection,
  handlePending,
  listTasks,
  rejectSection,
  revealValue,
  saveSection,
  submitSection,
  withdrawSection,
} from './data'

/**
 * MSW 请求处理器。
 *
 * <p>覆盖板块一的学生端与审核端全部接口，路径、响应结构、错误码与 openapi.yaml、
 * 后端 ResultCode 对齐；关闭 VITE_USE_MOCK 即切换到真实后端，页面不需要改。</p>
 *
 * <p>身份从请求头读取（与后端骨架期一致），因此切换顶部「演示身份」后，
 * 待审范围、字段可写性、审核权限都会真实变化。</p>
 */

const SECTION_CODES: SectionCode[] = ['basic', 'academic', 'interest', 'life', 'career', 'mentor']
const DEFAULT_YEAR = '2025-2026-1'

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

function fail(code: string, message: string, status: number, details?: FieldErrorDetail[]) {
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

/** 统一把 MockError 翻成 Result 包装的错误响应。 */
function handle(run: () => Response) {
  try {
    return run()
  } catch (error) {
    if (error instanceof MockError) {
      return fail(error.code, error.message, error.httpStatus)
    }
    throw error
  }
}

function roleOf(request: Request): UserRole {
  const role = (request.headers.get('X-User-Role') ?? 'STU').toUpperCase()
  return role === 'COU' || role === 'ADMIN' ? (role as UserRole) : 'STU'
}

function userIdOf(request: Request): number {
  const value = Number(request.headers.get('X-User-Id') ?? '0')
  return Number.isInteger(value) && value > 0 ? value : 0
}

function tempGrantOf(request: Request): boolean {
  return (request.headers.get('X-Temp-Grant') ?? '').toLowerCase() === 'true'
}

function parseCode(raw: string | readonly string[]): SectionCode | null {
  const value = (Array.isArray(raw) ? raw[0] : raw).toLowerCase() as SectionCode
  return SECTION_CODES.includes(value) ? value : null
}

function parseId(raw: string | readonly string[]): number | null {
  const value = Number(Array.isArray(raw) ? raw[0] : raw)
  return Number.isInteger(value) && value > 0 ? value : null
}

/** 字段编码形如 basic.idCard，取最后一段作为字段短名。 */
function fieldKeyOf(raw: string | readonly string[]): string {
  const value = String(Array.isArray(raw) ? raw[0] : raw)
  return value.includes('.') ? value.slice(value.lastIndexOf('.') + 1) : value
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
    const academicYear = url.searchParams.get('academicYear') ?? DEFAULT_YEAR
    return ok(getSection(code, studentId, academicYear, roleOf(request)))
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

    // 保存入参是信封结构：{ academicYear, fields: {...} }，不是扁平表单
    const fields = body.fields
    if (fields == null || typeof fields !== 'object') {
      return fail('EC-40002', '缺少必要参数：fields', 400)
    }
    const academicYear = String(body.academicYear ?? DEFAULT_YEAR)

    return handle(() => {
      saveSection(
        code,
        studentId,
        academicYear,
        { fields },
        roleOf(request),
      )
      return ok()
    })
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
    const academicYear = url.searchParams.get('academicYear') ?? DEFAULT_YEAR
    return handle(() => {
      submitSection(code, studentId, academicYear, roleOf(request))
      return ok()
    })
  }),

  http.post(`${API_BASE_PATH}/sections/:code/:studentId/withdraw`, ({ params, request }) => {
    const code = parseCode(params.code as string)
    const studentId = parseId(params.studentId as string)
    if (!code) {
      return fail('EC-40003', '板块编码不在允许范围内', 400)
    }
    if (!studentId) {
      return fail('EC-40003', '学生 ID 必须为正整数', 400)
    }

    const url = new URL(request.url)
    const academicYear = url.searchParams.get('academicYear') ?? DEFAULT_YEAR
    return handle(() => {
      withdrawSection(code, studentId, academicYear, roleOf(request))
      return ok()
    })
  }),

  http.post(
    `${API_BASE_PATH}/sections/:code/:studentId/fields/:fieldCode/confirm`,
    ({ params, request }) => {
      const code = parseCode(params.code as string)
      const studentId = parseId(params.studentId as string)
      if (!code || !studentId) {
        return fail('EC-40003', '参数不合法', 400)
      }
      const url = new URL(request.url)
      const academicYear = url.searchParams.get('academicYear') ?? DEFAULT_YEAR
      return handle(() =>
        ok(
          handlePending(
            code,
            studentId,
            academicYear,
            fieldKeyOf(params.fieldCode as string),
            'confirm',
            roleOf(request),
          ),
        ),
      )
    },
  ),

  http.post(
    `${API_BASE_PATH}/sections/:code/:studentId/fields/:fieldCode/reject`,
    ({ params, request }) => {
      const code = parseCode(params.code as string)
      const studentId = parseId(params.studentId as string)
      if (!code || !studentId) {
        return fail('EC-40003', '参数不合法', 400)
      }
      const url = new URL(request.url)
      const academicYear = url.searchParams.get('academicYear') ?? DEFAULT_YEAR
      return handle(() => {
        handlePending(
          code,
          studentId,
          academicYear,
          fieldKeyOf(params.fieldCode as string),
          'reject',
          roleOf(request),
        )
        return ok()
      })
    },
  ),

  http.get(
    `${API_BASE_PATH}/sections/:code/:studentId/fields/:fieldCode/reveal`,
    ({ params, request }) => {
      const code = parseCode(params.code as string)
      const studentId = parseId(params.studentId as string)
      if (!code || !studentId) {
        return fail('EC-40003', '参数不合法', 400)
      }
      const url = new URL(request.url)
      const academicYear = url.searchParams.get('academicYear') ?? DEFAULT_YEAR
      return handle(() =>
        ok(
          revealValue(
            code,
            studentId,
            academicYear,
            fieldKeyOf(params.fieldCode as string),
            roleOf(request),
          ),
        ),
      )
    },
  ),

  http.get(`${API_BASE_PATH}/review/tasks`, ({ request }) => {
    const url = new URL(request.url)
    const query = {
      sectionCode: url.searchParams.get('sectionCode') ?? undefined,
      status: url.searchParams.get('status') ?? undefined,
      classId: url.searchParams.get('classId') ?? undefined,
      academicYear: url.searchParams.get('academicYear') ?? undefined,
      pageNum: Number(url.searchParams.get('pageNum') ?? '1'),
      pageSize: Number(url.searchParams.get('pageSize') ?? '20'),
    }
    return handle(() => ok(listTasks(query, roleOf(request), userIdOf(request), tempGrantOf(request))))
  }),

  http.post(`${API_BASE_PATH}/review/:code/:studentId/approve`, async ({ params, request }) => {
    const code = parseCode(params.code as string)
    const studentId = parseId(params.studentId as string)
    if (!code || !studentId) {
      return fail('EC-40003', '参数不合法', 400)
    }
    const body = await request.json().catch(() => ({}))
    const academicYear = String((body as Record<string, unknown>).academicYear ?? DEFAULT_YEAR)
    return handle(() => {
      approveSection(code, studentId, academicYear, roleOf(request), userIdOf(request), tempGrantOf(request))
      return ok()
    })
  }),

  http.post(`${API_BASE_PATH}/review/:code/:studentId/reject`, async ({ params, request }) => {
    const code = parseCode(params.code as string)
    const studentId = parseId(params.studentId as string)
    if (!code || !studentId) {
      return fail('EC-40003', '参数不合法', 400)
    }
    const body = await request.json().catch(() => ({}))
    const payload = body as Record<string, unknown>
    return handle(() => {
      rejectSection(
        code,
        studentId,
        String(payload.academicYear ?? DEFAULT_YEAR),
        String(payload.reason ?? ''),
        roleOf(request),
        userIdOf(request),
        tempGrantOf(request),
      )
      return ok()
    })
  }),
]
