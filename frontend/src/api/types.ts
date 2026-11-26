/**
 * 与后端 com.college.growth.common.result 一一对应的类型定义。
 *
 * 约定来源：需求文档 6.1「错误响应体固定结构」，字段名与 Result / PageResult /
 * FieldErrorDetail 完全一致，改动此处必须同步 openapi.yaml 与后端代码。
 */

/** 字段级错误明细，仅字段校验失败（EC-42201）时出现。 */
export interface FieldErrorDetail {
  field?: string
  reason?: string
  message?: string
}

/** 统一响应体，所有接口一律返回该包装。 */
export interface Result<T> {
  /** 业务码：成功固定为 '0'，失败为 EC-xxxxx */
  code: string
  /** 提示文案，可直接展示给用户 */
  message: string
  /** 链路追踪号，反馈问题时报给后端 */
  traceId?: string
  /** 服务端时间，ISO-8601 带时区，如 2026-11-26T15:20:00+08:00 */
  timestamp?: string
  /** 业务数据，请求成功时存在 */
  data?: T
  /** 字段级错误明细，仅 EC-42201 时存在 */
  details?: FieldErrorDetail[]
}

/** 分页载体，列表接口统一使用。 */
export interface PageResult<T> {
  records: T[]
  total: number
  /** 页码，从 1 开始 */
  pageNum: number
  pageSize: number
}

/** 六大板块编码，与后端 SectionCodeEnum 一致（前端用小写，path 段直接用）。 */
export type SectionCode = 'basic' | 'academic' | 'interest' | 'life' | 'career' | 'mentor'

/** 档案板块状态，与后端 ProfileStatusEnum 一致。 */
export type ProfileStatus = 'DRAFT' | 'SUBMITTED' | 'RETURNED' | 'APPROVED' | 'LOCKED'

/**
 * 板块详情。
 *
 * 注意字段结构：外层是公共信封（学生、学年、板块、状态），
 * 板块自身字段放在 fields 里，由后端按 arc_field_def 动态下发，
 * 因此前端表单应按 fields 渲染而不是硬编码表单字段。
 */
export interface SectionVO {
  studentId: number
  academicYear: string
  sectionCode: string
  sectionName: string
  status: ProfileStatus
  fields: Record<string, unknown>
  /** 当前用户是否可编辑该板块 */
  editable: boolean
}
