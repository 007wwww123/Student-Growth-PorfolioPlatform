import type {
  FieldMeta,
  ProfileStatus,
  ReviewTask,
  SectionCode,
  SectionVO,
  UserRole,
} from '../api/types'

/**
 * mock 数据仓库（内存态）。
 *
 * <p>结构严格对齐后端：板块自身字段放在 fields 内，外层是公共信封；
 * 字段清单按字段定义生成 fieldMeta，而不是在页面里再写一份表单。</p>
 *
 * <p>这里还模拟了权限与状态机，目的是让「学生提交 → 辅导员审核 → 退回 → 重提」
 * 在不开后端的情况下也能点通。行为以真实后端为准，冲突时改这里。</p>
 */

export class MockError extends Error {
  readonly code: string
  readonly httpStatus: number

  constructor(code: string, message: string, httpStatus: number) {
    super(message)
    this.code = code
    this.httpStatus = httpStatus
  }
}

const SECTION_NAMES: Record<SectionCode, string> = {
  basic: '基本信息',
  academic: '学业发展',
  interest: '兴趣特长',
  life: '生活心理',
  career: '职业规划',
  mentor: '导师寄语',
}

/** 字段定义（mock 版），与 arc_field_def 的关键列一致。 */
interface MockFieldDef {
  key: string
  name: string
  type: FieldMeta['fieldType']
  required: boolean
  source: 'SIS' | 'SYS' | 'STU' | 'STU_CFM' | 'COU'
  sensitive?: number
  editableRoles: UserRole[]
  options?: Array<{ value: string; label: string }>
}

const BASIC_FIELDS: MockFieldDef[] = [
  { key: 'name', name: '姓名', type: 'TEXT', required: true, source: 'SIS', editableRoles: [] },
  { key: 'studentNo', name: '学号', type: 'TEXT', required: true, source: 'SIS', editableRoles: [] },
  {
    key: 'gender',
    name: '性别',
    type: 'RADIO',
    required: true,
    source: 'SIS',
    editableRoles: [],
    options: [
      { value: '男', label: '男' },
      { value: '女', label: '女' },
    ],
  },
  {
    key: 'nation',
    name: '民族',
    type: 'SELECT',
    required: true,
    source: 'SIS',
    editableRoles: [],
    options: [
      { value: '汉族', label: '汉族' },
      { value: '回族', label: '回族' },
      { value: '满族', label: '满族' },
    ],
  },
  {
    key: 'idCard',
    name: '身份证号',
    type: 'TEXT',
    required: true,
    source: 'SIS',
    sensitive: 2,
    editableRoles: [],
  },
  { key: 'nativePlace', name: '籍贯', type: 'TEXT', required: false, source: 'SIS', editableRoles: [] },
  {
    key: 'dormNo',
    name: '宿舍号',
    type: 'TEXT',
    required: false,
    source: 'COU',
    editableRoles: ['COU'],
  },
  {
    key: 'classId',
    name: '班级',
    type: 'SELECT',
    required: true,
    source: 'SIS',
    editableRoles: [],
    options: [
      { value: 'RJ2401', label: '软件2401' },
      { value: 'RJ2402', label: '软件2402' },
      { value: 'DZ2401', label: '电子2401' },
    ],
  },
  {
    key: 'majorId',
    name: '专业',
    type: 'TEXT',
    required: true,
    source: 'SYS',
    editableRoles: [],
  },
  {
    key: 'phone',
    name: '手机',
    type: 'TEXT',
    required: false,
    source: 'STU_CFM',
    sensitive: 1,
    editableRoles: ['STU', 'COU'],
  },
  {
    key: 'email',
    name: '邮箱',
    type: 'TEXT',
    required: false,
    source: 'STU_CFM',
    editableRoles: ['STU', 'COU'],
  },
  {
    key: 'emergencyContact',
    name: '紧急联系人',
    type: 'TEXT',
    required: false,
    source: 'STU_CFM',
    editableRoles: ['STU', 'COU'],
  },
  {
    key: 'emergencyPhone',
    name: '紧急联系电话',
    type: 'TEXT',
    required: true,
    source: 'STU_CFM',
    sensitive: 1,
    editableRoles: ['STU', 'COU'],
  },
  {
    key: 'photo',
    name: '照片',
    type: 'FILE',
    required: false,
    source: 'STU_CFM',
    editableRoles: ['STU', 'COU'],
  },
  {
    key: 'econLevel',
    name: '家庭经济状况',
    type: 'SELECT',
    required: false,
    source: 'COU',
    sensitive: 1,
    editableRoles: ['COU'],
    options: [
      { value: '一般', label: '一般' },
      { value: '困难', label: '困难' },
      { value: '特殊困难', label: '特殊困难' },
    ],
  },
  {
    key: 'familyStructure',
    name: '家庭结构',
    type: 'SELECT',
    required: false,
    source: 'COU',
    editableRoles: ['COU'],
    options: [
      { value: '双亲', label: '双亲' },
      { value: '单亲', label: '单亲' },
      { value: '其他', label: '其他' },
    ],
  },
]

/** 除基本信息外的板块只给骨架字段，够演示「六板块同一套页面」即可。 */
const SIMPLE_FIELDS: Record<string, MockFieldDef[]> = {
  academic: [
    { key: 'goalShort', name: '短期目标', type: 'TEXT', required: true, source: 'STU', editableRoles: ['STU'] },
    { key: 'goalLong', name: '长期目标', type: 'TEXTAREA', required: false, source: 'STU', editableRoles: ['STU'] },
    { key: 'strongSubjects', name: '优势科目', type: 'MULTI_SELECT', required: false, source: 'STU', editableRoles: ['STU'] },
    { key: 'weakSubjects', name: '待提升科目', type: 'MULTI_SELECT', required: false, source: 'STU', editableRoles: ['STU'] },
    { key: 'abilityNote', name: '能力自评', type: 'TEXTAREA', required: false, source: 'STU', editableRoles: ['STU'] },
  ],
  interest: [
    { key: 'hobbies', name: '兴趣标签', type: 'MULTI_SELECT', required: false, source: 'STU', editableRoles: ['STU'] },
    { key: 'hobbyDesc', name: '兴趣描述', type: 'TEXTAREA', required: false, source: 'STU', editableRoles: ['STU'] },
    { key: 'skills', name: '特长技能', type: 'TEXT', required: false, source: 'STU', editableRoles: ['STU'] },
    { key: 'ideal', name: '理想状态', type: 'TEXTAREA', required: false, source: 'STU', editableRoles: ['STU'] },
  ],
  life: [
    { key: 'orgRole', name: '组织职务', type: 'TEXT', required: false, source: 'STU', editableRoles: ['STU'] },
    { key: 'orgLevel', name: '组织层级', type: 'TEXT', required: false, source: 'STU', editableRoles: ['STU'] },
    { key: 'sleepHabit', name: '作息习惯', type: 'TEXT', required: false, source: 'STU', editableRoles: ['STU'] },
    { key: 'exerciseFreq', name: '运动频率', type: 'TEXT', required: false, source: 'STU', editableRoles: ['STU'] },
    { key: 'stressScore', name: '压力指数', type: 'NUMBER', required: false, source: 'STU', editableRoles: ['STU'] },
    { key: 'mood', name: '近期心情', type: 'TEXT', required: false, source: 'STU', editableRoles: ['STU'] },
  ],
  career: [
    { key: 'intention', name: '就业意向', type: 'TEXT', required: false, source: 'STU', editableRoles: ['STU'] },
    { key: 'industry', name: '目标行业', type: 'TEXT', required: false, source: 'STU', editableRoles: ['STU'] },
    { key: 'position', name: '目标岗位', type: 'TEXT', required: false, source: 'STU', editableRoles: ['STU'] },
    { key: 'plan', name: '实施计划', type: 'TEXTAREA', required: false, source: 'STU', editableRoles: ['STU'] },
    { key: 'support', name: '需要支持', type: 'TEXTAREA', required: false, source: 'STU', editableRoles: ['STU'] },
  ],
  mentor: [
    { key: 'talkDate', name: '谈话日期', type: 'DATE', required: false, source: 'COU', editableRoles: ['COU'] },
    { key: 'topic', name: '谈话主题', type: 'TEXT', required: false, source: 'COU', editableRoles: ['COU'] },
    { key: 'advice', name: '导师建议', type: 'TEXTAREA', required: false, source: 'COU', editableRoles: ['COU'] },
    { key: 'starLevel', name: '星级评分', type: 'NUMBER', required: false, source: 'COU', editableRoles: ['COU'] },
    { key: 'feedback', name: '学生反馈', type: 'TEXTAREA', required: false, source: 'STU', editableRoles: ['STU'] },
  ],
}

function defsOf(code: SectionCode): MockFieldDef[] {
  return code === 'basic' ? BASIC_FIELDS : (SIMPLE_FIELDS[code] ?? [])
}

/** 学生 × 班级，与 stu_student / org_class 种子数据一致。 */
const STUDENTS: Record<number, { name: string; no: string; gender: string; classId: string; idCard: string }> = {
  1001: { name: '李明', no: '2024010101', gender: '男', classId: 'RJ2401', idCard: '320102200201011234' },
  1002: { name: '王芳', no: '2024010102', gender: '女', classId: 'RJ2401', idCard: '320102200203152346' },
  1003: { name: '陈晨', no: '2024010201', gender: '男', classId: 'RJ2402', idCard: '320102200205203458' },
  1004: { name: '赵敏', no: '2024010301', gender: '女', classId: 'DZ2401', idCard: '320102200207114560' },
}

/** 班级 → 辅导员，用于校验审核数据范围。 */
const CLASS_COUNSELOR: Record<string, number> = {
  RJ2401: 9001,
  RJ2402: 9001,
  DZ2401: 9002,
}

/** mock 账号，与 db/sql/09_insert_user_seed.sql 一致（统一密码 Passw0rd!）。 */
interface MockAccount {
  username: string
  password: string
  userId: number
  role: UserRole
  displayName: string
  studentId?: number
  tempGrant: boolean
}

const ACCOUNTS: MockAccount[] = [
  { username: '2024010101', password: 'Passw0rd!', userId: 1001, role: 'STU', displayName: '李明', studentId: 1001, tempGrant: false },
  { username: '2024010102', password: 'Passw0rd!', userId: 1002, role: 'STU', displayName: '王芳', studentId: 1002, tempGrant: false },
  { username: '2024010201', password: 'Passw0rd!', userId: 1003, role: 'STU', displayName: '陈晨', studentId: 1003, tempGrant: false },
  { username: '2024010301', password: 'Passw0rd!', userId: 1004, role: 'STU', displayName: '赵敏', studentId: 1004, tempGrant: false },
  { username: 'cou9001', password: 'Passw0rd!', userId: 9001, role: 'COU', displayName: '辅导员·软件', tempGrant: false },
  { username: 'cou9002', password: 'Passw0rd!', userId: 9002, role: 'COU', displayName: '辅导员·电子', tempGrant: false },
  { username: 'admin', password: 'Passw0rd!', userId: 9003, role: 'ADMIN', displayName: '系统管理员', tempGrant: true },
  { username: 'admin0', password: 'Passw0rd!', userId: 9004, role: 'ADMIN', displayName: '管理员·无授权', tempGrant: false },
]

const ROLE_LABELS: Record<UserRole, string> = { STU: '学生', COU: '辅导员', ADMIN: '管理员' }

/** 请求身份，等价于后端 CurrentUser。 */
export interface MockIdentity {
  userId: number
  role: UserRole
  tempGrant: boolean
  displayName: string
  username: string
  studentId?: number
}

/** 登录成功后用于伪造令牌：mock.<userId>.<role>.<grant>。 */
export function loginUser(username: string, password: string): { token: string; user: MockIdentity } {
  const account = ACCOUNTS.find((item) => item.username === username)
  if (!account || account.password !== password) {
    throw new MockError('EC-40104', '账号或密码错误', 401)
  }
  const identity = toIdentity(account)
  return { token: tokenOf(identity), user: identity }
}

/** 解析令牌；格式不对或账号不存在都视为未登录。 */
export function authenticate(token: string | null): MockIdentity | null {
  if (!token) {
    return null
  }
  const parts = token.split('.')
  if (parts.length !== 4 || parts[0] !== 'mock') {
    return null
  }
  const userId = Number(parts[1])
  const account = ACCOUNTS.find((item) => item.userId === userId)
  if (!account || account.role !== parts[2]) {
    return null
  }
  return toIdentity(account)
}

function toIdentity(account: MockAccount): MockIdentity {
  return {
    userId: account.userId,
    role: account.role,
    tempGrant: account.tempGrant,
    displayName: account.displayName,
    username: account.username,
    studentId: account.studentId,
  }
}

function tokenOf(identity: MockIdentity): string {
  return `mock.${identity.userId}.${identity.role}.${identity.tempGrant ? 1 : 0}`
}

/** 角色中文名，登录响应里带给前端展示。 */
export function roleLabelOf(role: UserRole): string {
  return ROLE_LABELS[role] ?? role
}

const CLASS_NAMES: Record<string, string> = {
  RJ2401: '软件2401',
  RJ2402: '软件2402',
  DZ2401: '电子2401',
}

interface MockSection extends SectionVO {
  /** 待辅导员确认的新值：字段短名 → 新值 */
  pending: Record<string, unknown>
  reviewerId?: number
}

const store = new Map<string, MockSection>()

function keyOf(code: SectionCode, studentId: number, academicYear: string): string {
  return `${code}:${studentId}:${academicYear}`
}

function basicFieldsOf(studentId: number): Record<string, unknown> {
  const student = STUDENTS[studentId] ?? {
    name: `学生${studentId}`,
    no: String(studentId),
    gender: '男',
    classId: 'RJ2401',
    idCard: '320102200201010000',
  }
  return {
    name: student.name,
    studentNo: student.no,
    gender: student.gender,
    nation: '汉族',
    idCard: student.idCard,
    nativePlace: '江苏南京',
    dormNo: '3-412',
    classId: student.classId,
    majorId: student.classId.startsWith('DZ') ? '电子信息工程' : '软件工程',
    phone: '13800001234',
    email: `${student.no}@example.edu.cn`,
    emergencyContact: '李建国',
    emergencyPhone: '13900005678',
    photo: null,
    econLevel: '一般',
    familyStructure: '双亲',
  }
}

function emptyFields(code: SectionCode, studentId: number): Record<string, unknown> {
  if (code === 'basic') {
    return basicFieldsOf(studentId)
  }
  const fields: Record<string, unknown> = {}
  for (const def of defsOf(code)) {
    fields[def.key] = def.type === 'MULTI_SELECT' ? [] : null
  }
  return fields
}

function createSection(
  code: SectionCode,
  studentId: number,
  academicYear: string,
  status: ProfileStatus = 'DRAFT',
): MockSection {
  const section: MockSection = {
    studentId,
    academicYear,
    sectionCode: code.toUpperCase(),
    sectionName: SECTION_NAMES[code],
    status,
    fields: emptyFields(code, studentId),
    editable: status === 'DRAFT',
    completeness: 0,
    submitCount: 0,
    withdrawCount: 0,
    pending: {},
  }
  section.completeness = completeOf(section)
  store.set(keyOf(code, studentId, academicYear), section)
  return section
}

function getRaw(code: SectionCode, studentId: number, academicYear: string): MockSection {
  const key = keyOf(code, studentId, academicYear)
  const cached = store.get(key)
  if (cached) {
    return cached
  }
  return createSection(code, studentId, academicYear)
}

/** 首次进入时铺一些可审核的数据，避免待审列表永远是空的。 */
function ensureSeed(): void {
  if (store.size > 0) {
    return
  }
  const year = '2025-2026-1'
  createSection('basic', 1001, year, 'DRAFT')
  const submitted = createSection('basic', 1002, year, 'SUBMITTED')
  submitted.submitCount = 1
  submitted.submittedAt = '2026-11-26T09:30:00'
  const returned = createSection('basic', 1003, year, 'RETURNED')
  returned.submitCount = 2
  returned.submittedAt = '2026-11-25T16:10:00'
  returned.reviewedAt = '2026-11-26T08:20:00'
  returned.reviewReason = '紧急联系电话与学工系统不一致，请核对后重新提交。'
  createSection('basic', 1004, year, 'DRAFT')
}

function maskIdCard(value: string): string {
  if (value.length < 10) {
    return '****'
  }
  return `${value.slice(0, 6)}********${value.slice(-4)}`
}

function maskPhone(value: string): string {
  if (value.length < 7) {
    return '****'
  }
  return `${value.slice(0, 3)}****${value.slice(-4)}`
}

function maskedValue(def: MockFieldDef, value: unknown): unknown {
  if (value == null || value === '') {
    return value
  }
  if (def.sensitive === 2) {
    return maskIdCard(String(value))
  }
  if (def.sensitive === 1 && def.type === 'TEXT') {
    return maskPhone(String(value))
  }
  return value
}

function completeOf(section: MockSection): number {
  const required = defsOf(section.sectionCode.toLowerCase() as SectionCode).filter((def) => def.required)
  if (required.length === 0) {
    return 100
  }
  const filled = required.filter((def) => {
    const value = section.fields[def.key]
    return value != null && String(value).trim() !== ''
  }).length
  return Number(((filled / required.length) * 100).toFixed(2))
}

function canEdit(def: MockFieldDef, role: UserRole, status: ProfileStatus): boolean {
  if (status !== 'DRAFT' && status !== 'RETURNED') {
    return false
  }
  return def.editableRoles.includes(role)
}

function fieldMetaOf(section: MockSection, role: UserRole): FieldMeta[] {
  const code = section.sectionCode.toLowerCase() as SectionCode
  return defsOf(code).map((def) => {
    const hasPending = Object.prototype.hasOwnProperty.call(section.pending, def.key)
    return {
      key: def.key,
      fieldCode: `${code}.${def.key}`,
      fieldName: def.name,
      fieldType: def.type,
      required: def.required,
      source: def.source,
      options: def.options,
      sensitive: def.sensitive ?? 0,
      editable: canEdit(def, role, section.status),
      masked: def.sensitive != null && def.sensitive >= 2,
      pendingConfirm: hasPending,
      pendingValue: hasPending ? maskedValue(def, section.pending[def.key]) : undefined,
    }
  })
}

/** 对外视图：敏感字段按角色脱敏，待确认值单独带出。 */
function viewOf(section: MockSection, role: UserRole): SectionVO {
  const code = section.sectionCode.toLowerCase() as SectionCode
  const fields: Record<string, unknown> = {}
  for (const def of defsOf(code)) {
    fields[def.key] = maskedValue(def, section.fields[def.key])
  }
  return {
    ...section,
    fields,
    fieldMeta: fieldMetaOf(section, role),
    editable: section.status === 'DRAFT' || section.status === 'RETURNED',
    completeness: completeOf(section),
  }
}

export function getSection(
  code: SectionCode,
  studentId: number,
  academicYear: string,
  role: UserRole = 'STU',
): SectionVO {
  ensureSeed()
  const section = getRaw(code, studentId, academicYear)
  const view = viewOf(section, role)
  // 退回理由只在「已退回」时下发，重提后自然消失
  if (section.status !== 'RETURNED') {
    delete view.reviewReason
    delete view.reviewedAt
  }
  return view
}

export function saveSection(
  code: SectionCode,
  studentId: number,
  academicYear: string,
  payload: Record<string, unknown>,
  role: UserRole = 'STU',
): SectionVO {
  ensureSeed()
  const section = getRaw(code, studentId, academicYear)
  if (section.status !== 'DRAFT' && section.status !== 'RETURNED') {
    throw new MockError('EC-40902', '当前状态不允许修改', 409)
  }

  const values = (payload.fields ?? {}) as Record<string, unknown>
  const defByKey = new Map(defsOf(code).map((def) => [def.key, def]))

  for (const [key, value] of Object.entries(values)) {
    const def = defByKey.get(key)
    if (!def) {
      throw new MockError('EC-42201', '字段不存在或已停用', 422)
    }
    if (!canEdit(def, role, section.status)) {
      throw new MockError('EC-40301', `无权修改字段「${def.name}」`, 403)
    }
    const current = section.fields[key]
    // 需确认字段二次修改进待确认区，首次填写直接生效
    if (def.source === 'STU_CFM' && current != null && String(current) !== '' && String(current) !== String(value)) {
      section.pending[key] = value
    } else {
      section.fields[key] = value
    }
  }

  section.completeness = completeOf(section)
  return viewOf(section, role)
}

export function submitSection(
  code: SectionCode,
  studentId: number,
  academicYear: string,
  role: UserRole = 'STU',
): SectionVO {
  ensureSeed()
  const section = getRaw(code, studentId, academicYear)
  if (section.status !== 'DRAFT' && section.status !== 'RETURNED') {
    throw new MockError('EC-40902', '当前状态不允许提交', 409)
  }
  const missing = defsOf(code)
    .filter((def) => def.required)
    .filter((def) => {
      const value = section.fields[def.key]
      return value == null || String(value).trim() === ''
    })
  if (missing.length > 0) {
    throw new MockError('EC-42202', `还有 ${missing.length} 项必填未完成`, 422)
  }
  section.status = 'SUBMITTED'
  section.submitCount = (section.submitCount ?? 0) + 1
  section.submittedAt = new Date().toISOString().slice(0, 19)
  section.completeness = completeOf(section)
  return viewOf(section, role)
}

export function withdrawSection(
  code: SectionCode,
  studentId: number,
  academicYear: string,
  role: UserRole = 'STU',
): SectionVO {
  ensureSeed()
  const section = getRaw(code, studentId, academicYear)
  if (section.status !== 'SUBMITTED') {
    throw new MockError('EC-40902', '当前状态不允许撤回', 409)
  }
  if ((section.withdrawCount ?? 0) >= 1) {
    throw new MockError('EC-40903', '撤回次数已用完', 409)
  }
  if (role !== 'STU') {
    throw new MockError('EC-40302', '只有学生本人可以撤回', 403)
  }
  section.status = 'DRAFT'
  section.withdrawCount = (section.withdrawCount ?? 0) + 1
  return viewOf(section, role)
}

/** 待确认值的确认 / 驳回，仅辅导员。 */
export function handlePending(
  code: SectionCode,
  studentId: number,
  academicYear: string,
  fieldKey: string,
  action: 'confirm' | 'reject',
  role: UserRole,
): { fieldCode: string; fieldName?: string; value?: unknown; confirmStatus: string } {
  ensureSeed()
  if (role !== 'COU' && role !== 'ADMIN') {
    throw new MockError('EC-40302', '仅该班现任辅导员可以处理待确认值', 403)
  }
  const section = getRaw(code, studentId, academicYear)
  if (!Object.prototype.hasOwnProperty.call(section.pending, fieldKey)) {
    throw new MockError('EC-40902', '该字段没有待确认的值', 409)
  }
  const def = defsOf(code).find((item) => item.key === fieldKey)
  const next = section.pending[fieldKey]
  if (action === 'confirm') {
    section.fields[fieldKey] = next
  }
  delete section.pending[fieldKey]
  section.completeness = completeOf(section)
  return {
    fieldCode: `${code}.${fieldKey}`,
    fieldName: def?.name,
    value: maskedValue(def ?? BASIC_FIELDS[0]!, section.fields[fieldKey]),
    confirmStatus: action === 'confirm' ? 'APPROVED' : 'REJECTED',
  }
}

/** 查看敏感字段明文，仅辅导员或持授权管理员。 */
export function revealValue(
  code: SectionCode,
  studentId: number,
  academicYear: string,
  fieldKey: string,
  role: UserRole,
): { fieldCode: string; fieldName?: string; value: string } {
  ensureSeed()
  if (role !== 'COU' && role !== 'ADMIN') {
    throw new MockError('EC-40302', '无权限查看完整内容', 403)
  }
  const section = getRaw(code, studentId, academicYear)
  const def = defsOf(code).find((item) => item.key === fieldKey)
  if (!def || !def.sensitive) {
    throw new MockError('EC-40003', '该字段不是敏感字段', 400)
  }
  return {
    fieldCode: `${code}.${fieldKey}`,
    fieldName: def.name,
    value: String(section.fields[fieldKey] ?? ''),
  }
}

export interface TaskQuery {
  sectionCode?: string
  status?: string
  classId?: string
  academicYear?: string
  pageNum?: number
  pageSize?: number
}

function classOfStudent(studentId: number, year: string): string {
  const basic = store.get(keyOf('basic', studentId, year))
  const fromField = basic?.fields.classId
  return String(fromField ?? STUDENTS[studentId]?.classId ?? 'RJ2401')
}

/** 待审列表：按辅导员负责的班级过滤，管理员需持临时授权。 */
export function listTasks(
  query: TaskQuery,
  role: UserRole,
  userId: number,
  tempGrant: boolean,
): { records: ReviewTask[]; total: number; pageNum: number; pageSize: number } {
  ensureSeed()
  if (role === 'ADMIN' && !tempGrant) {
    throw new MockError('EC-40302', '管理员默认无业务数据权限', 403)
  }
  if (role !== 'COU' && role !== 'ADMIN') {
    throw new MockError('EC-40302', '无权查看待审列表', 403)
  }

  const status = query.status ?? 'SUBMITTED'
  const pageNum = query.pageNum ?? 1
  const pageSize = query.pageSize ?? 20

  const rows: ReviewTask[] = []
  for (const section of store.values()) {
    if (section.status !== status) {
      continue
    }
    if (query.sectionCode && section.sectionCode !== query.sectionCode.toUpperCase()) {
      continue
    }
    if (query.academicYear && section.academicYear !== query.academicYear) {
      continue
    }
    const classId = classOfStudent(section.studentId, section.academicYear)
    if (role === 'COU' && CLASS_COUNSELOR[classId] !== userId) {
      continue
    }
    if (query.classId && classId !== query.classId) {
      continue
    }
    const basic = store.get(keyOf('basic', section.studentId, section.academicYear))
    rows.push({
      profileId: undefined,
      studentId: section.studentId,
      studentName: String(basic?.fields.name ?? STUDENTS[section.studentId]?.name ?? ''),
      classId,
      className: CLASS_NAMES[classId],
      sectionCode: section.sectionCode,
      sectionName: section.sectionName,
      academicYear: section.academicYear,
      status: section.status,
      statusLabel: {
        DRAFT: '草稿',
        SUBMITTED: '已提交',
        RETURNED: '已退回',
        APPROVED: '已通过',
        LOCKED: '已归档',
      }[section.status],
      completeness: completeOf(section),
      submitCount: section.submitCount,
      submittedAt: section.submittedAt,
      reviewedAt: section.reviewedAt,
    })
  }

  rows.sort((a, b) => String(b.submittedAt ?? '').localeCompare(String(a.submittedAt ?? '')))
  const start = (pageNum - 1) * pageSize
  return {
    records: rows.slice(start, start + pageSize),
    total: rows.length,
    pageNum,
    pageSize,
  }
}

function assertCounselorOf(
  studentId: number,
  year: string,
  role: UserRole,
  userId: number,
  tempGrant: boolean,
): void {
  if (role === 'ADMIN' && tempGrant) {
    return
  }
  if (role !== 'COU') {
    throw new MockError('EC-40302', '仅辅导员可以审核', 403)
  }
  const classId = classOfStudent(studentId, year)
  if (CLASS_COUNSELOR[classId] !== userId) {
    throw new MockError('EC-40302', '只能审核本班学生的档案', 403)
  }
}

export function approveSection(
  code: SectionCode,
  studentId: number,
  academicYear: string,
  role: UserRole,
  userId: number,
  tempGrant: boolean,
): void {
  ensureSeed()
  assertCounselorOf(studentId, academicYear, role, userId, tempGrant)
  const section = getRaw(code, studentId, academicYear)
  if (section.status !== 'SUBMITTED') {
    throw new MockError('EC-40902', '该档案当前不是待审核状态', 409)
  }
  section.status = 'APPROVED'
  section.reviewerId = userId
  section.reviewedAt = new Date().toISOString().slice(0, 19)
  delete section.reviewReason
}

export function rejectSection(
  code: SectionCode,
  studentId: number,
  academicYear: string,
  reason: string,
  role: UserRole,
  userId: number,
  tempGrant: boolean,
): void {
  ensureSeed()
  assertCounselorOf(studentId, academicYear, role, userId, tempGrant)
  if (reason.trim().length < 10) {
    throw new MockError('EC-42203', '退回理由不少于 10 个字', 422)
  }
  const section = getRaw(code, studentId, academicYear)
  if (section.status !== 'SUBMITTED') {
    throw new MockError('EC-40902', '该档案当前不是待审核状态', 409)
  }
  section.status = 'RETURNED'
  section.reviewerId = userId
  section.reviewedAt = new Date().toISOString().slice(0, 19)
  section.reviewReason = reason.trim()
}

/** 重置内存数据，供测试或热更新使用。 */
export function resetStore(): void {
  store.clear()
}
