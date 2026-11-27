import { HttpResponse, http } from 'msw'
import { API_BASE_PATH, SUCCESS_CODE } from '../api/constants'
import type { FieldErrorDetail, SectionCode } from '../api/types'
import {
  MockError,
  approveSection,
  archiveStudents,
  authenticate,
  batchCreateProfiles,
  completionStatus,
  dashboardOverview,
  getSection,
  handlePending,
  listArchiveCandidates,
  listEnrollClasses,
  listTasks,
  loginUser,
  permissionMatrix,
  refreshCompletion,
  rejectSection,
  resetPermissions,
  revealValue,
  savePermissions,
  roleLabelOf,
  saveSection,
  submitSection,
  withdrawSection,
  type MockIdentity,
} from './data'

/**
 * MSW 请求处理器。
 *
 * <p>覆盖登录、板块一学生端与审核端全部接口，路径、响应结构、错误码与 openapi.yaml、
 * 后端 ResultCode 对齐；关闭 VITE_USE_MOCK 即切换到真实后端，页面不需要改。</p>
 *
 * <p>鉴权行为与后端保持一致：除 {@code /auth/login} 外都要求
 * {@code Authorization: Bearer <token>}，缺失或无效返回 {@code EC-40101}。
 * 这样「未登录拦截」在 mock 模式下也是真实生效的，而不是摆设。</p>
 */

const SECTION_CODES: SectionCode[] = ['basic', 'academic', 'interest', 'life', 'career', 'mentor']
const DEFAULT_YEAR = '2025-2026-1'
const MOCK_TTL_SECONDS = 8 * 3600

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
function guard(run: () => Response): Response {
  try {
    return run()
  } catch (error) {
    if (error instanceof MockError) {
      return fail(error.code, error.message, error.httpStatus)
    }
    throw error
  }
}

function tokenOf(request: Request): string | null {
  const header = request.headers.get('Authorization') ?? ''
  if (!header.toLowerCase().startsWith('bearer ')) {
    return null
  }
  const token = header.slice(7).trim()
  return token === '' ? null : token
}

/** 除登录外所有接口的统一入口：先验令牌，再执行业务。 */
function withAuth(request: Request, run: (who: MockIdentity) => Response): Response {
  const who = authenticate(tokenOf(request))
  if (!who) {
    return fail('EC-40101', '未登录或缺少令牌', 401)
  }
  return guard(() => run(who))
}

function userPayload(who: MockIdentity) {
  return {
    userId: who.userId,
    username: who.username,
    displayName: who.displayName,
    role: who.role,
    roleLabel: roleLabelOf(who.role),
    studentId: who.studentId,
    tempGrant: who.tempGrant,
  }
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
  http.post(`${API_BASE_PATH}/auth/login`, async ({ request }) => {
    let body: Record<string, unknown>
    try {
      body = (await request.json()) as Record<string, unknown>
    } catch {
      return fail('EC-40001', '请求格式不正确，请刷新页面重试', 400)
    }
    const username = String(body.username ?? '').trim()
    const password = String(body.password ?? '')
    if (!username || !password) {
      return fail('EC-40002', '请输入登录名和密码', 400)
    }
    return guard(() => {
      const result = loginUser(username, password)
      return ok({
        token: result.token,
        tokenType: 'Bearer',
        expiresIn: MOCK_TTL_SECONDS,
        user: userPayload(result.user),
      })
    })
  }),

  http.get(`${API_BASE_PATH}/auth/me`, ({ request }) =>
    withAuth(request, (who) => ok(userPayload(who))),
  ),

  http.post(`${API_BASE_PATH}/auth/logout`, ({ request }) => withAuth(request, () => ok())),

  // ------------------------------------------------------------------
  // 管理员 · 字段权限配置（只读写字段定义，不接触学生档案）
  // ------------------------------------------------------------------
  http.get(`${API_BASE_PATH}/admin/permissions`, ({ request }) =>
    withAuth(request, (who) => ok(permissionMatrix(who.role))),
  ),

  http.put(`${API_BASE_PATH}/admin/permissions/:sectionCode`, async ({ params, request }) => {
    const code = parseCode(params.sectionCode as string)
    if (!code) {
      return fail('EC-40003', '板块编码不在允许范围内', 400)
    }
    let body: Record<string, unknown>
    try {
      body = (await request.json()) as Record<string, unknown>
    } catch {
      return fail('EC-40001', '请求格式不正确，请刷新页面重试', 400)
    }
    const fields = body.fields
    if (!Array.isArray(fields) || fields.length === 0) {
      return fail('EC-40002', '至少需要提交一个字段', 400)
    }
    return withAuth(request, (who) =>
      ok(
        savePermissions(
          code,
          fields as Array<{ fieldCode: string; editableRoles: string[] }>,
          who.role,
        ),
      ),
    )
  }),

  http.post(`${API_BASE_PATH}/admin/permissions/:sectionCode/reset`, ({ params, request }) => {
    const code = parseCode(params.sectionCode as string)
    if (!code) {
      return fail('EC-40003', '板块编码不在允许范围内', 400)
    }
    return withAuth(request, (who) => ok(resetPermissions(code, who.role)))
  }),

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
    return withAuth(request, (who) => ok(getSection(code, studentId, academicYear, who.role)))
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

    return withAuth(request, (who) => {
      saveSection(code, studentId, academicYear, { fields }, who.role)
      return ok()
    })
  }),

  http.post(`${API_BASE_PATH}/sections/:code/:studentId/submit`, ({ params, request }) => {    const code = parseCode(params.code as string)
    const studentId = parseId(params.studentId as string)
    if (!code) {
      return fail('EC-40003', '板块编码不在允许范围内', 400)
    }
    if (!studentId) {
      return fail('EC-40003', '学生 ID 必须为正整数', 400)
    }

    const url = new URL(request.url)
    const academicYear = url.searchParams.get('academicYear') ?? DEFAULT_YEAR
    return withAuth(request, (who) => {
      submitSection(code, studentId, academicYear, who.role)
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
    return withAuth(request, (who) => {
      withdrawSection(code, studentId, academicYear, who.role)
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
      return withAuth(request, (who) =>
        ok(
          handlePending(
            code,
            studentId,
            academicYear,
            fieldKeyOf(params.fieldCode as string),
            'confirm',
            who.role,
            who.userId,
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
      return withAuth(request, (who) => {
        handlePending(
          code,
          studentId,
          academicYear,
          fieldKeyOf(params.fieldCode as string),
          'reject',
          who.role,
          who.userId,
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
      return withAuth(request, (who) =>
        ok(
          revealValue(
            code,
            studentId,
            academicYear,
            fieldKeyOf(params.fieldCode as string),
            who.role,
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
    return withAuth(request, (who) =>
      ok(listTasks(query, who.role, who.userId, who.tempGrant)),
    )
  }),

  http.post(`${API_BASE_PATH}/review/:code/:studentId/approve`, async ({ params, request }) => {
    const code = parseCode(params.code as string)
    const studentId = parseId(params.studentId as string)
    if (!code || !studentId) {
      return fail('EC-40003', '参数不合法', 400)
    }
    const body = (await request.json().catch(() => ({}))) as Record<string, unknown>
    const academicYear = String(body.academicYear ?? DEFAULT_YEAR)
    return withAuth(request, (who) => {
      approveSection(code, studentId, academicYear, who.role, who.userId, who.tempGrant)
      return ok()
    })
  }),

  http.post(`${API_BASE_PATH}/review/:code/:studentId/reject`, async ({ params, request }) => {
    const code = parseCode(params.code as string)
    const studentId = parseId(params.studentId as string)
    if (!code || !studentId) {
      return fail('EC-40003', '参数不合法', 400)
    }
    const body = (await request.json().catch(() => ({}))) as Record<string, unknown>
    return withAuth(request, (who) => {
      rejectSection(
        code,
        studentId,
        String(body.academicYear ?? DEFAULT_YEAR),
        String(body.reason ?? ''),
        who.role,
        who.userId,
        who.tempGrant,
      )
      return ok()
    })
  }),

  http.get(`${API_BASE_PATH}/profiles/classes`, ({ request }) => {
    const url = new URL(request.url)
    const academicYear = url.searchParams.get('academicYear') ?? DEFAULT_YEAR
    return withAuth(request, (who) =>
      ok(listEnrollClasses(academicYear, who.role, who.userId)),
    )
  }),

  http.post(`${API_BASE_PATH}/profiles/batch-create`, async ({ request }) => {
    let body: Record<string, unknown>
    try {
      body = (await request.json()) as Record<string, unknown>
    } catch {
      return fail('EC-40001', '请求格式不正确，请刷新页面重试', 400)
    }
    const academicYear = String(body.academicYear ?? DEFAULT_YEAR)
    const classCodes = Array.isArray(body.classCodes)
      ? body.classCodes.map((item) => String(item))
      : []
    if (classCodes.length === 0) {
      return fail('EC-40002', '请至少选择一个班级', 400)
    }
    return withAuth(request, (who) =>
      ok(batchCreateProfiles(academicYear, classCodes, who.role, who.userId)),
    )
  }),

  http.get(`${API_BASE_PATH}/archive/candidates`, ({ request }) => {
    const url = new URL(request.url)
    const academicYear = url.searchParams.get('academicYear') ?? DEFAULT_YEAR
    const classCode = url.searchParams.get('classCode') ?? undefined
    return withAuth(request, (who) =>
      ok(listArchiveCandidates(academicYear, who.role, who.userId, who.tempGrant, classCode)),
    )
  }),

  http.post(`${API_BASE_PATH}/archive/batch`, async ({ request }) => {
    let body: Record<string, unknown>
    try {
      body = (await request.json()) as Record<string, unknown>
    } catch {
      return fail('EC-40001', '请求格式不正确，请刷新页面重试', 400)
    }
    const academicYear = String(body.academicYear ?? DEFAULT_YEAR)
    const studentIds = Array.isArray(body.studentIds)
      ? body.studentIds.map((item) => Number(item))
      : []
    if (studentIds.length === 0) {
      return fail('EC-40002', '请至少选择一名学生', 400)
    }
    return withAuth(request, (who) =>
      ok(archiveStudents(academicYear, studentIds, who.role, who.userId)),
    )
  }),

  http.get(`${API_BASE_PATH}/completion/students/:studentId`, ({ params, request }) => {
    const studentId = parseId(params.studentId as string)
    if (!studentId) {
      return fail('EC-40003', '参数不合法', 400)
    }
    const url = new URL(request.url)
    const academicYear = url.searchParams.get('academicYear') ?? DEFAULT_YEAR
    return withAuth(request, (who) =>
      ok(
        completionStatus(
          studentId,
          academicYear,
          who.role,
          who.userId,
          who.tempGrant,
        ),
      ),
    )
  }),

  http.post(`${API_BASE_PATH}/completion/refresh`, ({ request }) => {
    const url = new URL(request.url)
    const rawStudentId = url.searchParams.get('studentId')
    const studentId = rawStudentId ? parseId(rawStudentId) : undefined
    if (rawStudentId && !studentId) {
      return fail('EC-40003', '参数不合法', 400)
    }
    const academicYear = url.searchParams.get('academicYear') ?? DEFAULT_YEAR
    return withAuth(request, (who) =>
      ok(
        refreshCompletion(
          academicYear,
          studentId ?? undefined,
          who.role,
          who.userId,
          who.tempGrant,
        ),
      ),
    )
  }),

  http.get(`${API_BASE_PATH}/dashboard/overview`, ({ request }) => {
    const url = new URL(request.url)
    const academicYear = url.searchParams.get('academicYear') ?? DEFAULT_YEAR
    const classCode = url.searchParams.get('classCode') ?? undefined
    return withAuth(request, (who) =>
      ok(
        dashboardOverview(
          academicYear,
          classCode,
          who.role,
          who.userId,
          who.tempGrant,
        ),
      ),
    )
  }),
]
