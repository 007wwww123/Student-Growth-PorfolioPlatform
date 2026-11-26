import axios, { AxiosError, type AxiosRequestConfig } from 'axios'
import { accessToken, identity, currentStudentId, currentAcademicYear } from './context'
import { SUCCESS_CODE } from './constants'
import type { FieldErrorDetail, Result } from './types'

export { SUCCESS_CODE }

/**
 * 业务异常。
 *
 * 后端把「传输层结果」交给 HTTP 状态码，把「具体原因」交给业务码，
 * 所以 4xx 也可能带完整的 Result 包装，这里统一收敛成一个异常类型，
 * 页面只需要读 code / message / details，不用判断是网络错误还是业务错误。
 */
export class ApiError extends Error {
  /** 业务码，如 EC-42201；网络层失败时为 NETWORK_ERROR */
  readonly code: string
  /** HTTP 状态码，网络层失败时为空 */
  readonly httpStatus?: number
  /** 字段级错误明细 */
  readonly details?: FieldErrorDetail[]
  /** 链路追踪号，便于报障时定位日志 */
  readonly traceId?: string

  constructor(params: {
    code: string
    message: string
    httpStatus?: number
    details?: FieldErrorDetail[]
    traceId?: string
  }) {
    super(params.message)
    this.name = 'ApiError'
    this.code = params.code
    this.httpStatus = params.httpStatus
    this.details = params.details
    this.traceId = params.traceId
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError
}

const instance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api',
  timeout: 15_000,
  headers: { 'Content-Type': 'application/json' },
})

instance.interceptors.request.use(config => {
  if (accessToken.value) config.headers.Authorization = `Bearer ${accessToken.value}`
  return config
})

/**
 * 发起请求并按 Result 约定解包。
 *
 * 成功（code === '0'）时直接返回 data，失败时抛出 ApiError，
 * 调用方用 try/catch 或 isApiError 判断即可，不必关心 HTTP 状态码。
 */
export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  try {
    const response = await instance.request<Result<T>>(config)
    const body = response.data

    if (body == null || typeof body !== 'object' || !('code' in body)) {
      throw new ApiError({
        code: 'EC-INVALID-BODY',
        message: '服务端返回格式不符合约定',
        httpStatus: response.status,
      })
    }

    if (body.code !== SUCCESS_CODE) {
      throw new ApiError({
        code: body.code,
        message: body.message || '请求失败',
        httpStatus: response.status,
        details: body.details,
        traceId: body.traceId,
      })
    }

    return body.data as T
  } catch (error) {
    const failure = normalizeError(error)
    if (failure.httpStatus === 401 && error instanceof AxiosError &&
        error.config?.headers.Authorization === `Bearer ${accessToken.value}`) {
      accessToken.value = ''
      identity.value = null
      currentStudentId.value = 0
      currentAcademicYear.value = ''
    }
    throw failure
  }
}

function normalizeError(error: unknown): ApiError {
  if (error instanceof ApiError) {
    return error
  }

  if (error instanceof AxiosError) {
    // 后端返回了 Result 包装的错误响应
    const body = error.response?.data as Result<unknown> | undefined
    if (body && typeof body === 'object' && typeof body.code === 'string') {
      return new ApiError({
        code: body.code,
        message: body.message || '请求失败',
        httpStatus: error.response?.status,
        details: body.details,
        traceId: body.traceId,
      })
    }

    if (error.code === 'ECONNABORTED') {
      return new ApiError({ code: 'NETWORK_TIMEOUT', message: '请求超时，请稍后重试' })
    }

    return new ApiError({
      code: 'NETWORK_ERROR',
      message: '网络异常，请检查后端服务是否已启动',
      httpStatus: error.response?.status,
    })
  }

  return new ApiError({ code: 'UNKNOWN_ERROR', message: '发生未知错误' })
}

export default instance
