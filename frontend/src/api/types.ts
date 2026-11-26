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

/** 平台三类角色，与后端 UserRoleEnum 一致。 */
export type UserRole = 'STU' | 'COU' | 'ADMIN'

/**
 * 字段渲染与权限位，对应后端 FieldMetaVO。
 *
 * 字段清单、控件类型、必填、是否可写全部由服务端按 arc_field_def 计算后下发，
 * 前端不另写一份字段表，也不要自己推断「哪些字段只读」。
 */
export interface FieldMeta {
  /** 字段短名，与 SectionVO.fields 的键一致 */
  key: string
  /** 字段编码，如 basic.studentNo */
  fieldCode: string
  /** 界面显示名 */
  fieldName: string
  /** TEXT / TEXTAREA / NUMBER / DATE / SELECT / MULTI_SELECT / RADIO / CHECKBOX / FILE */
  fieldType: string
  /** 提交时是否必填 */
  required?: boolean
  /** 来源：SIS / COU / STU / STU_CFM / SYS / IMP */
  source?: string
  /** 枚举选项，非枚举字段为空 */
  options?: Array<{ value: string; label: string }>
  /** 敏感级别：0 普通 / 1 脱敏 / 2 仅辅导员可查看完整值 */
  sensitive?: number
  /** 当前角色在当前状态下是否可写 */
  editable?: boolean
  /** 本次返回的值是否已脱敏 */
  masked?: boolean
  /** 是否存在待辅导员确认的值 */
  pendingConfirm?: boolean
  /** 待确认值（如有），已按角色脱敏 */
  pendingValue?: unknown
}

/**
 * 板块详情。
 *
 * 注意字段结构：外层是公共信封（学生、学年、板块、状态），
 * 板块自身字段放在 fields 里，由后端按 arc_field_def 动态下发，
 * 因此前端表单应按 fieldMeta 渲染而不是硬编码表单字段。
 */
export interface SectionVO {
  studentId: number
  academicYear: string
  sectionCode: string
  sectionName: string
  status: ProfileStatus
  fields: Record<string, unknown>
  /** 字段渲染与权限位，按 sort_no 排序，仅含当前角色可见的字段 */
  fieldMeta?: FieldMeta[]
  /** 当前用户是否可编辑该板块 */
  editable: boolean
  /** 完成度百分比，由服务端按必填字段现算 */
  completeness?: number
  /** 提交次数 */
  submitCount?: number
  /** 撤回次数 */
  withdrawCount?: number
  /** 最近一次提交时间 */
  submittedAt?: string
  /** 最近一次退回理由（BR-S04：理由对学生可见），仅在 RETURNED 状态返回 */
  reviewReason?: string
  /** 最近一次退回时间，与 reviewReason 同时返回 */
  reviewedAt?: string
}

/**
 * 待审列表行，对应后端 ReviewTaskVO。
 *
 * 只带列表需要的字段，打开详情仍走各板块详情接口，
 * 避免审核模块复制一套字段渲染与权限逻辑。
 */
export interface ReviewTask {
  profileId?: number
  studentId: number
  /** 学生姓名，取自基本信息板块 basic.name */
  studentName?: string
  classId?: string
  className?: string
  /** 板块编码，大写，如 BASIC */
  sectionCode: string
  sectionName?: string
  academicYear: string
  status: ProfileStatus
  statusLabel?: string
  completeness?: number
  submitCount?: number
  submittedAt?: string
  reviewerId?: number
  reviewedAt?: string
}

/** 待确认值处理结果，对应后端 PendingFieldVO。 */
export interface PendingField {
  fieldCode: string
  fieldName?: string
  /** 处理后的对外值，敏感字段为脱敏值 */
  value?: unknown
  /** APPROVED / REJECTED */
  confirmStatus?: string
}

/** 敏感字段明文，对应后端 SensitiveFieldVO。每次调用都会写审计日志。 */
export interface SensitiveField {
  fieldCode: string
  fieldName?: string
  value: string
}

/** 审核结论入参，对应后端 ReviewDecisionDTO。 */
export interface ReviewDecision {
  /** 学年，为空取当前学年；注意审核接口从请求体读学年，不是 query */
  academicYear?: string
  /** 审核意见，通过时可空，最长 255 */
  comment?: string
  /** 退回理由，退回时必填且不少于 10 字 */
  reason?: string
}
