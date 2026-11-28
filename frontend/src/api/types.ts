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

/** 当前登录用户，对应后端 UserVO。 */
export interface SessionUser {
  userId: number
  username: string
  displayName: string
  role: UserRole
  /** 角色中文名 */
  roleLabel: string
  /** 学生账号关联的学生 ID，其他角色为空 */
  studentId?: number
  /** 管理员是否持临时只读授权 */
  tempGrant: boolean
}

/** 登录结果，对应后端 LoginVO。 */
export interface LoginResult {
  token: string
  tokenType: string
  /** 有效期秒数 */
  expiresIn: number
  user: SessionUser
}

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
 * 时间线上的一条谈话记录（板块六 MENTOR，需求 4.7）。
 *
 * <p>学生端响应体里没有 internalRemark / counselorId 这两个键
 * （服务端按角色裁剪，不是前端隐藏），作者只显示「辅导员」。</p>
 */
export interface MentorNoteVO {
  id: number
  talkDate: string
  topic: string
  topicLabel: string
  advice: string
  starLevel: number
  feedback: string | null
  feedbackAt: string | null
  /** 仅学生端有意义；feedbackAt 非空即锁定 */
  canFeedback: boolean
  authorLabel: string
  createdAt: string
  /** 仅辅导员端返回；无备注时为 null */
  internalRemark?: string | null
  /** 仅辅导员端返回 */
  counselorId?: number
}

/** 导师寄语详情：统一信封 + 子表时间线。fields 恒为空（本板块无主表字段值）。 */
export interface MentorSectionVO extends SectionVO {
  notes: MentorNoteVO[]
  canAppend: boolean
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

/**
 * 可建档班级行，对应后端 ClassEnrollVO。
 *
 * 「已建档」的口径是六个板块实例都存在，不要求已填写或已通过。
 */
export interface EnrollClass {
  classCode: string
  className: string
  majorName?: string
  gradeYear?: number
  /** 本班在读学生数 */
  studentCount: number
  /** 六板块齐全的学生数 */
  enrolledStudentCount: number
  /** 仍有板块缺失的学生数 */
  missingStudentCount: number
  /** 是否已全部建档 */
  enrolled: boolean
}

/** 单个学生的建档结果，对应后端 StudentEnrollItemVO。 */
export interface EnrollStudentResult {
  studentId: number
  studentNo?: string
  studentName?: string
  classCode?: string
  className?: string
  /** 本次新建的板块编码（大写） */
  createdSections: string[]
  /** 本次跳过的板块数量 */
  skippedSectionCount: number
  /** CREATED / SKIPPED */
  status: 'CREATED' | 'SKIPPED'
}

/** 批量建档结果，对应后端 BatchCreateResultVO。 */
export interface BatchCreateResult {
  academicYear: string
  classCodes: string[]
  totalStudents: number
  createdStudents: number
  skippedStudents: number
  createdProfiles: number
  existingProfiles: number
  students: EnrollStudentResult[]
}

/** 板块状态行，对应后端 ArchiveSectionStatusVO。 */
export interface ArchiveSectionStatus {
  /** 板块编码，大写 */
  sectionCode: string
  sectionName: string
  /** 当前状态；未建档时为空 */
  status?: ProfileStatus
  /** 状态中文名；未建档时为「未建档」 */
  statusLabel: string
}

/**
 * 归档候选人，对应后端 ArchiveCandidateVO。
 *
 * 口径：六板块齐全且每块 APPROVED / LOCKED 才可归档；已归档也计入「完成」。
 */
export interface ArchiveCandidate {
  studentId: number
  studentNo?: string
  studentName?: string
  classCode?: string
  className?: string
  sectionCount: number
  approvedCount: number
  lockedCount: number
  incompleteSections: ArchiveSectionStatus[]
  archivable: boolean
  archived: boolean
}

/** 单个学生的归档结果，对应后端 ArchiveStudentResultVO。 */
export interface ArchiveStudentResult {
  studentId: number
  studentNo?: string
  studentName?: string
  className?: string
  /** ARCHIVED 已归档 / SKIPPED 本就已归档 / INCOMPLETE 未完成 */
  status: 'ARCHIVED' | 'SKIPPED' | 'INCOMPLETE'
  /** 未完成的板块名称，仅 INCOMPLETE 时有值 */
  missingSections?: string[]
}

/** 批量归档结果，对应后端 ArchiveResultVO。rejected=true 表示整批未执行（EX-12）。 */
export interface ArchiveResult {
  academicYear: string
  requestedStudents: number
  archivedStudents: number
  archivedProfiles: number
  skippedStudents: number
  rejected: boolean
  students: ArchiveStudentResult[]
}

/**
 * 单板块完成情况，对应后端 CompletionSectionVO。
 *
 * sectionCode 为小写（与 SectionCode 一致），未建档板块的 status 为空、statusLabel 为「未建档」。
 */
export interface CompletionSection {
  sectionCode: SectionCode
  sectionName?: string
  status?: ProfileStatus
  statusLabel?: string
  /** 本学期是否算完成（已通过 / 已归档） */
  completed: boolean
}

/**
 * 完成标记，对应后端 CompletionStatusVO（BR-S10）。
 *
 * 标记是服务端派生值，前端不自行统计「几块通过了几块」，否则两边口径会漂。
 */
export interface CompletionStatus {
  studentId: number
  studentNo?: string
  studentName?: string
  classCode?: string
  className?: string
  academicYear: string
  /** 六板块是否全部完成 */
  completed: boolean
  /** 已建档板块数 0–6 */
  createdSectionCount: number
  /** 已完成板块数 0–6 */
  completedSectionCount: number
  /** 未完成板块中文名，按板块顺序 */
  pendingSections: string[]
  /** 逐板块状态，固定六项 */
  sections: CompletionSection[]
}

/** 完成标记重算结果，对应后端 CompletionRefreshVO。 */
export interface CompletionRefreshResult {
  academicYear: string
  /** MANUAL 手动 / SCHEDULED 定时 / STATUS_CHANGE 状态流转触发 */
  trigger?: string
  checkedStudents: number
  completedStudents: number
  /** 标记发生翻转的学生数，重复重算应为 0 */
  changedStudents: number
  refreshedAt?: string
}

/** 单板块进度分布，对应后端 SectionProgressVO。 */
export interface SectionProgress {
  sectionCode: SectionCode
  sectionName?: string
  /** 该板块已建档学生数 */
  studentCount: number
  draft: number
  submitted: number
  returned: number
  approved: number
  locked: number
  /** 完成率百分比，一位小数 */
  completionRate: number
}

/** 班级完成率，对应后端 ClassProgressVO。 */
export interface ClassProgress {
  classCode: string
  className?: string
  studentTotal: number
  completedStudents: number
  pendingReview: number
  /** 完成率百分比，一位小数 */
  completionRate: number
}

/**
 * 首页进度聚合，对应后端 DashboardOverviewVO。
 *
 * 口径为「本学年已建档」：未建档学生没有档案记录，不计入分母。
 * 数字全部由服务端算好，前端不要再用 sections 里的分项重新推导总数。
 */
export interface DashboardOverview {
  academicYear: string
  /** 数据范围文案：全校 / 我的班级 / 指定班级名 */
  scopeLabel?: string
  studentTotal: number
  completedStudents: number
  /** 完成率百分比，一位小数 */
  completionRate: number
  /** 待审核板块数 */
  pendingReview: number
  returnedProfiles: number
  draftProfiles: number
  /** 六板块状态分布，固定六项 */
  sections: SectionProgress[]
  classes: ClassProgress[]
}

/** 字段权限的四选一模式，与后端 PermissionConstants 一致。 */
export type PermissionMode = 'NONE' | 'STU' | 'COU' | 'BOTH'

/**
 * 单个字段的编辑权限，对应后端 FieldPermissionVO。
 *
 * <p>`editableRoles` 是唯一事实，`permissionMode` 只是它的四选一投影；
 * 前端提交时仍按角色数组提交，避免出现两套权限表达。</p>
 */
export interface FieldPermission {
  fieldCode: string
  fieldName: string
  fieldType: string
  /** 值来源：SIS / COU / STU / STU_CFM / SYS / IMP */
  source?: string
  required?: boolean
  sensitive?: number
  editableRoles: Array<'STU' | 'COU'>
  defaultEditableRoles: Array<'STU' | 'COU'>
  permissionMode: PermissionMode
  /** 当前配置是否偏离初始化口径 */
  customized: boolean
  /** 学生是否可见该字段；不可见时不允许配置为「学生可编辑」 */
  studentVisible?: boolean
  /** 老师是否可见该字段；不可见时不允许配置为「老师可编辑」 */
  counselorVisible?: boolean
  /** 系统推导 / 系统生成 / 学籍唯一标识，固定只读，不可配置 */
  locked: boolean
  lockedReason?: string
}

/** 单个板块的字段权限清单及统计，对应后端 SectionPermissionVO。 */
export interface SectionPermission {
  sectionCode: string
  sectionName: string
  fields: FieldPermission[]
  studentEditable: number
  counselorEditable: number
  readonly: number
  customized: number
}

/** 六大板块权限矩阵，对应后端 PermissionMatrixVO。 */
export interface PermissionMatrix {
  sections: SectionPermission[]
}

/** 保存权限后的返回，对应后端 PermissionSaveResultVO。 */
export interface PermissionSaveResult {
  changed: number
  section: SectionPermission
}
