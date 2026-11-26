/**
 * 接口约定常量。
 *
 * 单独成文件是为了让 mock（运行在 Service Worker 中）也能复用，
 * 避免 import http.ts 时把 axios 与 import.meta.env 带进 mock 上下文。
 */

/** 成功业务码，与后端 ResultCode.SUCCESS 一致。 */
export const SUCCESS_CODE = '0'

/** 接口前缀，与后端 server.servlet.context-path 一致。 */
export const API_BASE_PATH = '/api'
