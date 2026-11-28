import type {
  ArchiveCandidate,
  ArchiveResult,
  ArchiveSectionStatus,
  ArchiveStudentResult,
  BatchCreateResult,
  CompletionRefreshResult,
  CompletionSection,
  CompletionStatus,
  DashboardOverview,
  EnrollClass,
  EnrollStudentResult,
  FieldMeta,
  FieldPermission,
  MentorNoteVO,
  MentorSectionVO,
  PermissionMatrix,
  PermissionSaveResult,
  ProfileStatus,
  ReviewTask,
  SectionCode,
  SectionPermission,
  SectionProgress,
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

/** 状态中文名，供完成标记与列表复用。 */
const STATUS_LABELS: Record<ProfileStatus, string> = {
  DRAFT: '草稿',
  SUBMITTED: '已提交',
  RETURNED: '已退回',
  APPROVED: '已通过',
  LOCKED: '已归档',
}

/** 已完成口径：已通过与已归档都算完成（归档不能把标记算回未完成）。 */
const COMPLETED_STATUSES: ProfileStatus[] = ['APPROVED', 'LOCKED']

/** 字段定义（mock 版），与 arc_field_def 的关键列一致。 */
interface MockFieldDef {
  key: string
  name: string
  type: FieldMeta['fieldType']
  required: boolean
  source: 'SIS' | 'SYS' | 'STU' | 'STU_CFM' | 'COU' | 'IMP'
  sensitive?: number
  editableRoles: UserRole[]
  /** 初始化口径，管理员保存权限后仍保留，用于「已调整」与「恢复默认」 */
  defaultEditableRoles?: UserRole[]
  /** 可见角色，缺省为师生均可见；不可见的角色不允许被配置为可编辑 */
  visibleRoles?: UserRole[]
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
      { value: 'M', label: '男' },
      { value: 'F', label: '女' },
      { value: 'U', label: '未知' },
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
      { value: 'NORMAL', label: '一般' },
      { value: 'DIFFICULT', label: '困难' },
      { value: 'GOOD', label: '良好' },
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
      { value: 'ONLY_CHILD', label: '独生' },
      { value: 'NOT_ONLY', label: '非独生' },
      { value: 'OTHER', label: '其他' },
    ],
  },
]

/**
 * 除基本信息外的板块字段。
 *
 * <p>学业与兴趣（5 + 4 个）与 `db/sql/10_insert_academic_interest_field_defs.sql` 一致；
 * 生活、职业、导师（12 + 5 + 6 个）与 `db/sql/12_seed_section_field_defs.sql` 一致。
 * 两个脚本各管一段、互不重叠，mock 照抄同一口径，避免「页面有、后端没有」的字段。</p>
 */
const SIMPLE_FIELDS: Record<string, MockFieldDef[]> = {
  academic: [
    { key: 'goalShort', name: '短期学业目标', type: 'TEXTAREA', required: true, source: 'STU', editableRoles: ['STU'] },
    { key: 'goalLong', name: '长期发展目标', type: 'TEXTAREA', required: false, source: 'STU', editableRoles: ['STU'] },
    { key: 'strongSubjects', name: '优势科目', type: 'MULTI_SELECT', required: false, source: 'STU', editableRoles: ['STU'] },
    { key: 'weakSubjects', name: '待提升科目', type: 'MULTI_SELECT', required: false, source: 'STU', editableRoles: ['STU'] },
    { key: 'abilityNote', name: '学科能力认定', type: 'TEXTAREA', required: false, source: 'COU', editableRoles: ['COU'] },
  ],
  interest: [
    { key: 'hobbies', name: '兴趣爱好', type: 'MULTI_SELECT', required: true, source: 'STU', editableRoles: ['STU'] },
    { key: 'hobbyDesc', name: '兴趣描述', type: 'TEXTAREA', required: false, source: 'STU', editableRoles: ['STU'] },
    { key: 'skills', name: '技能特长', type: 'TEXTAREA', required: false, source: 'STU', editableRoles: ['STU'] },
    { key: 'ideal', name: '理想描述', type: 'TEXTAREA', required: false, source: 'STU', editableRoles: ['STU'] },
  ],
  life: [
    { key: 'orgRole', name: '社团/学生工作职务', type: 'TEXT', required: false, source: 'STU', editableRoles: ['STU'] },
    {
      key: 'orgLevel',
      name: '参与度',
      type: 'SELECT',
      required: false,
      source: 'STU',
      editableRoles: ['STU'],
      // 枚举一律存英文码、展示中文标签（需求 5.4）：库里是 HIGH/MID/LOW，
      // mock 若用「高/中/低」当值，前端存进去的值后端一个都认不出来
      options: [
        { value: 'HIGH', label: '高' },
        { value: 'MID', label: '中' },
        { value: 'LOW', label: '低' },
      ],
    },
    {
      key: 'sleepHabit',
      name: '作息习惯',
      type: 'SELECT',
      required: false,
      source: 'STU',
      editableRoles: ['STU'],
      options: [
        { value: 'EARLY', label: '早睡早起' },
        { value: 'NORMAL', label: '规律' },
        { value: 'LATE', label: '晚睡' },
      ],
    },
    {
      key: 'exerciseFreq',
      name: '运动频率',
      type: 'SELECT',
      required: false,
      source: 'STU',
      editableRoles: ['STU'],
      options: [
        { value: 'NONE', label: '不运动' },
        { value: 'LOW', label: '每周 1–2 次' },
        { value: 'MID', label: '每周 3–4 次' },
        { value: 'HIGH', label: '每周 5 次以上' },
      ],
    },
    { key: 'personality', name: '个性描述', type: 'TEXTAREA', required: false, source: 'STU', editableRoles: ['STU'] },
    {
      key: 'econSelf',
      name: '经济状况自评',
      type: 'SELECT',
      required: false,
      source: 'STU',
      editableRoles: ['STU'],
      options: [
        { value: 'GOOD', label: '良好' },
        { value: 'NORMAL', label: '一般' },
        { value: 'TIGHT', label: '紧张' },
      ],
    },
    { key: 'aidSource', name: '资助来源', type: 'MULTI_SELECT', required: false, source: 'COU', editableRoles: ['COU'] },
    {
      key: 'stressScore',
      name: '压力自评',
      type: 'NUMBER',
      required: true,
      source: 'STU',
      editableRoles: ['STU'],
      // 原始分值只对学生本人可见（需求 4.5）：辅导员视图里连字段都不下发，只看关注标记
      visibleRoles: ['STU'],
    },
    { key: 'stressSource', name: '压力来源', type: 'MULTI_SELECT', required: false, source: 'STU', editableRoles: ['STU'] },
    {
      key: 'mood',
      name: '整体情绪',
      type: 'SELECT',
      required: true,
      source: 'STU',
      editableRoles: ['STU'],
      options: [
        { value: 'GOOD', label: '良好' },
        { value: 'FAIR', label: '一般' },
        { value: 'LOW', label: '低落' },
      ],
    },
    // 辅导员工作记录：学生端不返回（需求 4.5 要求接口层剔除，不靠前端隐藏）
    {
      key: 'majorEvent',
      name: '重大事件备忘',
      type: 'TEXTAREA',
      required: false,
      source: 'COU',
      editableRoles: ['COU'],
      visibleRoles: ['COU'],
    },
    // 派生字段：由压力自评与情绪算出，固定不可编辑，值不落库
    { key: 'careFlag', name: '关注标记', type: 'CHECKBOX', required: false, source: 'SYS', editableRoles: [] },
  ],
  career: [
    {
      key: 'intention',
      name: '发展意向',
      type: 'SELECT',
      required: true,
      source: 'STU',
      editableRoles: ['STU'],
      // 枚举存英文码、展示中文标签（需求 5.4）：原 mock 把「考研」当值，真实后端按 ENUM 校验会直接拒绝
      options: [
        { value: 'EMPLOY', label: '就业' },
        { value: 'POSTGRAD', label: '考研' },
        { value: 'OVERSEAS', label: '留学' },
        { value: 'STARTUP', label: '创业' },
        { value: 'UNDECIDED', label: '未定' },
      ],
    },
    { key: 'industry', name: '心仪行业', type: 'TEXT', required: false, source: 'STU', editableRoles: ['STU'] },
    { key: 'position', name: '心仪岗位', type: 'TEXT', required: false, source: 'STU', editableRoles: ['STU'] },
    { key: 'plan', name: '已做准备', type: 'TEXTAREA', required: false, source: 'STU', editableRoles: ['STU'] },
    { key: 'support', name: '所需支持', type: 'TEXTAREA', required: false, source: 'STU', editableRoles: ['STU'] },
  ],
  mentor: [
    { key: 'talkDate', name: '谈话日期', type: 'DATE', required: true, source: 'COU', editableRoles: ['COU'] },
    {
      key: 'topic',
      name: '谈话主题',
      type: 'SELECT',
      required: true,
      source: 'COU',
      editableRoles: ['COU'],
      options: [
        // 与 12_seed_section_field_defs.sql 一致：枚举一律存英文码、展示中文标签。
        // 原 mock 用中文当值，真实后端的 ENUM 校验器一个都认不出来。
        { value: 'ACADEMIC', label: '学业规划' },
        { value: 'CAREER', label: '就业指导' },
        { value: 'PSYCH', label: '心理疏导' },
        { value: 'DAILY', label: '日常交流' },
      ],
    },
    { key: 'advice', name: '建议与寄语', type: 'TEXTAREA', required: true, source: 'COU', editableRoles: ['COU'] },
    {
      key: 'starLevel',
      name: '重要标记',
      type: 'RADIO',
      required: false,
      source: 'COU',
      editableRoles: ['COU'],
      options: [
        { value: '0', label: '无' },
        { value: '1', label: '★' },
        { value: '2', label: '★★' },
      ],
    },
    { key: 'feedback', name: '学生反馈与承诺', type: 'TEXTAREA', required: false, source: 'STU', editableRoles: ['STU'] },
    // 内部备注学生端不返回，因此只能配置「老师可编辑」
    {
      key: 'internalRemark',
      name: '内部备注',
      type: 'TEXTAREA',
      required: false,
      source: 'COU',
      editableRoles: ['COU'],
      visibleRoles: ['COU'],
    },
  ],
}

// 初始化口径快照：管理员改过权限后，仍能标识「已调整」并支持「恢复默认」
for (const def of [...BASIC_FIELDS, ...Object.values(SIMPLE_FIELDS).flat()]) {
  def.defaultEditableRoles = [...def.editableRoles]
}

function defsOf(code: SectionCode): MockFieldDef[] {
  return code === 'basic' ? BASIC_FIELDS : (SIMPLE_FIELDS[code] ?? [])
}

/** 学生 × 班级，与 stu_student / org_class 种子数据一致。 */
const STUDENTS: Record<number, { name: string; no: string; gender: string; classId: string; idCard: string; nation: string; nativePlace: string }> = {
  1001: { name: '李明', no: '2024010101', gender: 'M', classId: 'RJ2401', idCard: '320102200201011232', nation: '汉族', nativePlace: '江苏 南京' },
  1002: { name: '王芳', no: '2024010102', gender: 'F', classId: 'RJ2401', idCard: '320102200201015671', nation: '回族', nativePlace: '江苏 南京' },
  1003: { name: '陈晨', no: '2024010201', gender: 'M', classId: 'RJ2402', idCard: '320102200201022345', nation: '汉族', nativePlace: '江苏 苏州' },
  1004: { name: '赵敏', no: '2024010301', gender: 'F', classId: 'DZ2401', idCard: '320102200201034565', nation: '满族', nativePlace: '江苏 无锡' },
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

/** 班级附加信息，与 org_class 的 major_name / grade_year 对齐（批量建档页展示用）。 */
const CLASS_INFO: Record<string, { major: string; grade: number }> = {
  RJ2401: { major: '软件工程', grade: 2024 },
  RJ2402: { major: '软件工程', grade: 2024 },
  DZ2401: { major: '电子信息工程', grade: 2024 },
}

interface MockSection extends SectionVO {
  /** 待辅导员确认的新值：字段短名 → 新值 */
  pending: Record<string, unknown>
  reviewerId?: number
}

const store = new Map<string, MockSection>()

/** 六板块编码，顺序与 SectionCodeEnum 一致。 */
const ALL_SECTIONS = Object.keys(SECTION_NAMES) as SectionCode[]

/** 学生某学年已真实创建的板块实例（惰性占位的不算）。 */
function existingSections(studentId: number, year: string): SectionCode[] {
  return ALL_SECTIONS.filter((code) => store.has(keyOf(code, studentId, year)))
}

function keyOf(code: SectionCode, studentId: number, academicYear: string): string {
  return `${code}:${studentId}:${academicYear}`
}

function basicFieldsOf(studentId: number): Record<string, unknown> {
  const student = STUDENTS[studentId] ?? {
    name: `学生${studentId}`,
    no: String(studentId),
    gender: 'M',
    classId: 'RJ2401',
    idCard: '320102200201010000', nation: '汉族', nativePlace: '江苏 南京',
  }
  return {
    name: student.name,
    studentNo: student.no,
    gender: student.gender,
    nation: student.nation,
    idCard: student.idCard,
    nativePlace: student.nativePlace,
    dormNo: '3-412',
    classId: student.classId,
    majorId: student.classId.startsWith('DZ') ? '电子信息工程' : '软件工程',
    phone: '13800001234',
    email: `${student.no}@example.edu.cn`,
    emergencyContact: '李建国',
    emergencyPhone: '13900005678',
    photo: null,
    econLevel: 'NORMAL',
    familyStructure: 'ONLY_CHILD',
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
  // 赵敏（1004，电子2401）六板块全部通过，用于演示毕业归档的正向路径
  for (const code of ALL_SECTIONS) {
    createSection(code, 1004, year, 'APPROVED')
  }
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
  const required = defsOf(section.sectionCode.toLowerCase() as SectionCode).filter((def) => def.required && def.source !== 'SYS')
  if (required.length === 0) {
    return 100
  }
  const filled = required.filter((def) => {
    const value = section.fields[def.key] ?? section.pending[def.key]
    return value != null && String(value).trim() !== ''
  }).length
  return Number(((filled / required.length) * 100).toFixed(2))
}

function canEdit(def: MockFieldDef, role: UserRole, status: ProfileStatus): boolean {
  if (role === 'ADMIN') {
    // 管理员只配置权限，不参与内容编辑
    return false
  }
  if (status !== 'DRAFT' && status !== 'RETURNED') {
    return false
  }
  // 可见性与可编辑性同口径：看不见的字段不允许写
  if (!(def.visibleRoles ?? ['STU', 'COU']).includes(role)) {
    return false
  }
  return def.editableRoles.includes(role)
}

// ---------------------------------------------------------------------------
// 字段编辑权限配置（管理员模式）
// ---------------------------------------------------------------------------

function lockedReasonOf(def: MockFieldDef): string | null {
  if (def.key === 'majorId') {
    return '该字段由系统按班级自动推导，固定只读'
  }
  if (def.key === 'studentNo') {
    return '学号是学籍唯一标识，创建后不可修改'
  }
  if (def.source === 'SYS') {
    return '该字段由系统生成，固定只读'
  }
  return null
}

function permissionModeOf(roles: UserRole[]): 'NONE' | 'STU' | 'COU' | 'BOTH' {
  const student = roles.includes('STU')
  const counselor = roles.includes('COU')
  if (student && counselor) return 'BOTH'
  if (student) return 'STU'
  if (counselor) return 'COU'
  return 'NONE'
}

function sameRoles(left: UserRole[], right: UserRole[]): boolean {
  return left.length === right.length && left.every((role) => right.includes(role))
}

/** 角色是否可见该字段：看不见却可写会出现「能写但看不到」，必须前后端同口径拦住。 */
function roleVisibleOf(def: MockFieldDef, role: UserRole): boolean {
  return (def.visibleRoles ?? ['STU', 'COU']).includes(role)
}

function fieldPermissionOf(def: MockFieldDef): FieldPermission {
  const roles = [...def.editableRoles]
  const defaults = [...(def.defaultEditableRoles ?? def.editableRoles)]
  const lockedReason = lockedReasonOf(def)
  return {
    fieldCode: def.key,
    fieldName: def.name,
    fieldType: def.type,
    source: def.source,
    required: def.required,
    sensitive: def.sensitive ?? 0,
    editableRoles: roles.filter((role): role is 'STU' | 'COU' => role !== 'ADMIN'),
    defaultEditableRoles: defaults.filter((role): role is 'STU' | 'COU' => role !== 'ADMIN'),
    permissionMode: permissionModeOf(roles),
    customized: !sameRoles(roles, defaults),
    studentVisible: roleVisibleOf(def, 'STU'),
    counselorVisible: roleVisibleOf(def, 'COU'),
    locked: lockedReason != null,
    lockedReason: lockedReason ?? undefined,
  }
}

function sectionPermissionOf(code: SectionCode): SectionPermission {
  const fields = defsOf(code).map((def) => ({
    ...fieldPermissionOf(def),
    fieldCode: `${code}.${def.key}`,
  }))
  return {
    sectionCode: code.toUpperCase(),
    sectionName: SECTION_NAMES[code],
    fields,
    studentEditable: fields.filter((field) => field.editableRoles.includes('STU')).length,
    counselorEditable: fields.filter((field) => field.editableRoles.includes('COU')).length,
    readonly: fields.filter((field) => field.editableRoles.length === 0).length,
    customized: fields.filter((field) => field.customized).length,
  }
}

function requireAdmin(role: UserRole): void {
  if (role !== 'ADMIN') {
    throw new MockError('EC-40303', '仅管理员可以配置字段编辑权限', 403)
  }
}

export function permissionMatrix(role: UserRole): PermissionMatrix {
  requireAdmin(role)
  return { sections: ALL_SECTIONS.map((code) => sectionPermissionOf(code)) }
}

export function savePermissions(
  code: SectionCode,
  items: Array<{ fieldCode: string; editableRoles: string[] }>,
  role: UserRole,
): PermissionSaveResult {
  requireAdmin(role)
  const defs = defsOf(code)
  let changed = 0
  for (const item of items) {
    const def = defs.find((candidate) => `${code}.${candidate.key}` === item.fieldCode)
    if (!def) {
      throw new MockError('EC-40003', `字段「${item.fieldCode}」不属于板块「${SECTION_NAMES[code]}」`, 400)
    }
    const roles: UserRole[] = []
    for (const raw of item.editableRoles ?? []) {
      const value = String(raw).trim().toUpperCase()
      if (value === 'ADMIN') {
        throw new MockError('EC-40003', '管理员只负责配置权限，不能作为内容编辑角色', 400)
      }
      if (value !== 'STU' && value !== 'COU') {
        throw new MockError('EC-40003', '可编辑角色只能是 STU（学生）或 COU（老师）', 400)
      }
      if (!roleVisibleOf(def, value)) {
        const label = value === 'STU' ? '学生' : '老师'
        throw new MockError('EC-40003', `字段「${def.name}」对${label}不可见，不能配置为可编辑`, 400)
      }
      if (!roles.includes(value)) {
        roles.push(value)
      }
    }
    const lockedReason = lockedReasonOf(def)
    if (lockedReason != null && roles.length > 0) {
      throw new MockError('EC-40003', `字段「${def.name}」不可配置：${lockedReason}`, 400)
    }
    if (!sameRoles(roles, def.editableRoles)) {
      def.editableRoles = roles
      changed++
    }
  }
  return { changed, section: sectionPermissionOf(code) }
}

export function resetPermissions(code: SectionCode, role: UserRole): PermissionSaveResult {
  requireAdmin(role)
  let changed = 0
  for (const def of defsOf(code)) {
    const defaults = [...(def.defaultEditableRoles ?? def.editableRoles)]
    if (!sameRoles(defaults, def.editableRoles)) {
      def.editableRoles = defaults
      changed++
    }
  }
  return { changed, section: sectionPermissionOf(code) }
}

/**
 * 当前角色能否看到该字段。
 *
 * <p>与后端 {@code arc_field_def.visible_roles} 同口径：看不到的字段连值与 fieldMeta 都不下发，
 * 否则「前端隐藏」只是把输入框藏起来，响应体里仍然能读到。管理员例外——持授权时按全量返回，
 * 由页面只读展示。</p>
 */
function canSee(def: MockFieldDef, role: UserRole): boolean {
  return role === 'ADMIN' || roleVisibleOf(def, role)
}

function fieldMetaOf(section: MockSection, role: UserRole): FieldMeta[] {
  const code = section.sectionCode.toLowerCase() as SectionCode
  return defsOf(code)
    .filter((def) => canSee(def, role))
    .map((def) => {
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

/**
 * 派生的关注标记（需求 4.5：由压力自评与情绪算出，不落库、不可手工编辑）。
 *
 * <p>取值一律来自正式值而不是脱敏后的视图，理由与后端一致：辅导员看不到压力自评，
 * 若从视图推导，关注标记会永远是 0——那恰好是辅导员唯一能看的结论。</p>
 */
function derivedCareFlag(section: MockSection): number {
  const score = Number(section.fields.stressScore)
  return section.fields.mood === 'LOW' && Number.isFinite(score) && score >= 4 ? 1 : 0
}

/** 对外视图：敏感字段按角色脱敏，不可见字段不下发，派生字段读取时补上。 */
function viewOf(section: MockSection, role: UserRole): SectionVO {
  const code = section.sectionCode.toLowerCase() as SectionCode
  const fields: Record<string, unknown> = {}
  for (const def of defsOf(code)) {
    if (!canSee(def, role)) {
      continue
    }
    fields[def.key] = maskedValue(def, section.fields[def.key])
  }
  if (code === 'life' && 'careFlag' in fields) {
    fields.careFlag = derivedCareFlag(section)
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
  if (role === 'ADMIN') {
    throw new MockError('EC-40303', '管理员只负责配置字段权限，不能修改档案内容', 403)
  }
  ensureSeed()
  const section = getRaw(code, studentId, academicYear)
  if (section.status !== 'DRAFT' && section.status !== 'RETURNED') {
    throw new MockError('EC-40902', '当前状态不允许修改', 409)
  }

  const values = (payload.fields ?? {}) as Record<string, unknown>
  const defByKey = new Map(defsOf(code).map((def) => [def.key, def]))

  for (const key of Object.keys(values)) {
    const def = defByKey.get(key)
    if (!def) {
      throw new MockError('EC-42201', '字段不存在或已停用', 422)
    }
    if (!canEdit(def, role, section.status)) {
      throw new MockError('EC-40301', `无权修改字段「${def.name}」`, 403)
    }
  }

  // 先按合并后的快照校验板块规则，再落值：mock 没有事务，先写后校验会留下半份脏数据
  validateSectionRules(code, { ...section.fields, ...values })

  for (const [key, value] of Object.entries(values)) {
    const def = defByKey.get(key)!
    if (def.source === 'STU_CFM' && role === 'STU') {
      if (JSON.stringify(section.fields[key]) !== JSON.stringify(value)) section.pending[key] = value
    } else {
      section.fields[key] = value
      delete section.pending[key]
    }
  }

  section.completeness = completeOf(section)
  return viewOf(section, role)
}

/**
 * 板块级跨字段规则，与后端 `*SectionUtils.validateFields` 同口径。
 *
 * <p>mock 只覆盖能拦住「页面能存、后端 422」的那几条；字段类型、枚举、长度仍由
 * fieldMeta 的控件约束负责。</p>
 */
function validateSectionRules(code: SectionCode, fields: Record<string, unknown>): void {
  if (code !== 'life') {
    return
  }
  const score = fields.stressScore
  if (score != null && String(score).trim() !== '') {
    const numeric = Number(score)
    if (!Number.isInteger(numeric) || numeric < 1 || numeric > 5) {
      throw new MockError('EC-42201', '压力自评须为 1–5 的整数', 422)
    }
  }
  const source = fields.stressSource
  if (Array.isArray(source) && source.length > 3) {
    throw new MockError('EC-42201', '压力来源最多选择 3 项', 422)
  }
}

// ---------------------------------------------------------------------------
// 板块六 导师寄语：谈话记录子表（需求 4.7）
// ---------------------------------------------------------------------------

/**
 * 与后端 arc_mentor_note 同构。mock 也用独立数组而不是塞进 section.fields：
 * 需求明确「没有主表字段值，全部内容在子表，一行一次谈话」，
 * 用固定字段表达不了 N 次谈话，也会让完成度按主表必填字段恒算 0%。
 */
interface MentorNoteRow {
  id: number
  studentId: number
  academicYear: string
  talkDate: string
  topic: string
  advice: string
  starLevel: number
  feedback: string | null
  feedbackAt: string | null
  internalRemark: string | null
  counselorId: number
  createdAt: string
}

let mentorNoteSeq = 5000

/** 预置两条谈话，对应验收用例 AC-040「学生已有 2 条谈话，辅导员追加第 3 条」。 */
const MENTOR_NOTES: MentorNoteRow[] = [
  {
    id: 5001, studentId: 1001, academicYear: '2025-2026-1', talkDate: '2026-04-18',
    topic: 'ACADEMIC', advice: '本学期重点补齐算法基础，建议参加校赛练手并每周复盘一次',
    starLevel: 1, feedback: null, feedbackAt: null, internalRemark: null,
    counselorId: 9001, createdAt: '2026-04-18T10:00:00',
  },
  {
    id: 5002, studentId: 1001, academicYear: '2025-2026-1', talkDate: '2026-05-20',
    topic: 'CAREER', advice: '建议暑期投递实习，简历突出项目经历与量化成果，尽早准备秋招',
    starLevel: 2, feedback: null, feedbackAt: null,
    internalRemark: '已推荐至合作企业实习', counselorId: 9001, createdAt: '2026-05-20T14:30:00',
  },
]

const MENTOR_TOPIC_CODES = new Set(['ACADEMIC', 'CAREER', 'PSYCH', 'DAILY'])
const MENTOR_ADVICE_MIN = 20
const MENTOR_ADVICE_MAX = 1000
const MENTOR_TEXT_MAX = 500
/** 学生主数据只有入学年，下界按 1 月 1 日取，与后端 MentorSectionUtils 同口径。 */
const MENTOR_ENROLL_FROM = '2024-01-01'

function nowText(): string {
  return new Date().toISOString().slice(0, 19)
}

function mentorRowsOf(studentId: number, academicYear: string): MentorNoteRow[] {
  return MENTOR_NOTES.filter((row) => row.studentId === studentId && row.academicYear === academicYear)
    .slice()
    .sort((a, b) => b.talkDate.localeCompare(a.talkDate) || b.createdAt.localeCompare(a.createdAt) || b.id - a.id)
}

function mentorNoteComplete(row: MentorNoteRow): boolean {
  return Boolean(row.talkDate && row.topic && row.advice.trim())
}

/** 完成度按「至少一条完整谈话」算，与主表字段无关（后端 MentorCompletenessProvider 同口径）。 */
function mentorCompletenessOf(studentId: number, academicYear: string): number {
  return mentorRowsOf(studentId, academicYear).some(mentorNoteComplete) ? 100 : 0
}

function mentorTopicLabel(topic: string): string {
  const def = SIMPLE_FIELDS.mentor.find((item) => item.key === 'topic')
  return def?.options?.find((option) => option.value === topic)?.label ?? topic
}

/**
 * 按角色裁剪视图：学生端连 internalRemark / counselorId 两个键都不下发。
 * 与后端 MentorSectionUtils.toView 同口径，避免「前端隐藏」被当成权限控制。
 */
function mentorNoteView(row: MentorNoteRow, role: UserRole): MentorNoteVO {
  const view: MentorNoteVO = {
    id: row.id,
    talkDate: row.talkDate,
    topic: row.topic,
    topicLabel: mentorTopicLabel(row.topic),
    advice: row.advice,
    starLevel: row.starLevel,
    feedback: row.feedback,
    feedbackAt: row.feedbackAt,
    canFeedback: role === 'STU' && row.feedbackAt === null,
    authorLabel: '辅导员',
    createdAt: row.createdAt,
  }
  if (role === 'COU' || role === 'ADMIN') {
    view.internalRemark = row.internalRemark
    view.counselorId = row.counselorId
  }
  return view
}

/** 导师寄语详情：统一信封 + 子表时间线；fields 恒为空。 */
export function getMentorSection(
  studentId: number,
  academicYear: string,
  role: UserRole = 'STU',
  userId = 0,
): MentorSectionVO {
  ensureSeed()
  const section = getRaw('mentor', studentId, academicYear)
  const view = viewOf(section, role) as MentorSectionVO
  const defs = defsOf('mentor')
  view.fields = {}
  view.fieldMeta = (view.fieldMeta ?? []).filter((meta) => {
    const def = defs.find((item) => item.key === meta.key)
    return (def?.visibleRoles ?? ['STU', 'COU']).includes(role)
  })
  view.completeness = mentorCompletenessOf(studentId, academicYear)
  view.notes = mentorRowsOf(studentId, academicYear).map((row) => mentorNoteView(row, role))
  view.canAppend = role === 'COU'
    && section.status !== 'LOCKED'
    && CLASS_COUNSELOR[classOfStudent(studentId, academicYear)] === userId
  if (section.status !== 'RETURNED') {
    delete view.reviewReason
    delete view.reviewedAt
  }
  return view
}

/** 追加一条谈话记录（仅本班辅导员；只追加，不提供修改/删除）。 */
export function appendMentorNote(
  studentId: number,
  payload: Record<string, unknown>,
  role: UserRole,
  userId: number,
): MentorNoteVO {
  if (role !== 'COU') {
    throw new MockError('EC-40303', '仅该班辅导员可追加谈话记录', 403)
  }
  ensureSeed()
  const academicYear = String(payload.academicYear ?? '2025-2026-1')
  if (CLASS_COUNSELOR[classOfStudent(studentId, academicYear)] !== userId) {
    throw new MockError('EC-40302', '无权访问该学生的档案', 403)
  }
  const section = getRaw('mentor', studentId, academicYear)
  if (section.status === 'LOCKED') {
    throw new MockError('EC-40902', '板块已归档，仅可追加更正记录', 409)
  }

  const talkDate = String(payload.talkDate ?? '')
  const topic = String(payload.topic ?? '')
  const advice = String(payload.advice ?? '')
  const starLevel = Number(payload.starLevel ?? 0)
  const internalRemark = String(payload.internalRemark ?? '')

  // 与后端同一套错误码：长度/枚举/日期 → EC-42201，必填缺失 → EC-42202。
  if (!talkDate || !topic || !advice) {
    throw new MockError('EC-42202', '必填内容未填写：谈话日期 / 主题 / 建议与寄语', 422)
  }
  if (talkDate > new Date().toISOString().slice(0, 10) || talkDate < MENTOR_ENROLL_FROM) {
    throw new MockError('EC-42201', '谈话日期不晚于今天且不早于入学日期', 422)
  }
  if (!MENTOR_TOPIC_CODES.has(topic)) {
    throw new MockError('EC-42201', '取值不在可选范围内：谈话主题', 422)
  }
  if (advice.trim().length < MENTOR_ADVICE_MIN || advice.trim().length > MENTOR_ADVICE_MAX) {
    throw new MockError('EC-42201', '建议与寄语为 20–1000 字', 422)
  }
  if (!Number.isInteger(starLevel) || starLevel < 0 || starLevel > 2) {
    throw new MockError('EC-42201', '重要标记取值 0–2', 422)
  }
  if (internalRemark.length > MENTOR_TEXT_MAX) {
    throw new MockError('EC-42201', '内部备注不能超过 500 字', 422)
  }

  const row: MentorNoteRow = {
    id: ++mentorNoteSeq,
    studentId,
    academicYear,
    talkDate,
    topic,
    advice: advice.trim(),
    starLevel,
    feedback: null,
    feedbackAt: null,
    internalRemark: internalRemark.trim() || null,
    counselorId: userId,
    createdAt: nowText(),
  }
  MENTOR_NOTES.push(row)
  section.completeness = mentorCompletenessOf(studentId, academicYear)
  return mentorNoteView(row, role)
}

/** 学生反馈：仅本人、每条一次，feedbackAt 非空即锁定。 */
export function submitMentorFeedback(
  studentId: number,
  noteId: number,
  payload: Record<string, unknown>,
  role: UserRole,
): MentorNoteVO {
  if (role !== 'STU') {
    throw new MockError('EC-40302', '仅学生本人可提交反馈', 403)
  }
  ensureSeed()
  const row = MENTOR_NOTES.find((item) => item.id === noteId && item.studentId === studentId)
  if (!row) {
    throw new MockError('EC-40302', '无权对该谈话记录提交反馈', 403)
  }
  if (row.feedbackAt !== null) {
    throw new MockError('EC-40902', '该条谈话已反馈，不可重复提交', 409)
  }
  const feedback = String(payload.feedback ?? '').trim()
  if (!feedback) {
    throw new MockError('EC-40002', '反馈内容不能为空', 400)
  }
  if (feedback.length > MENTOR_TEXT_MAX) {
    throw new MockError('EC-42201', '学生反馈不能超过 500 字', 422)
  }
  row.feedback = feedback
  row.feedbackAt = nowText()
  return mentorNoteView(row, role)
}

export function submitSection(
  code: SectionCode,
  studentId: number,
  academicYear: string,
  role: UserRole = 'STU',
): SectionVO {
  if (role === 'ADMIN') {
    throw new MockError('EC-40303', '管理员只负责配置字段权限，不能修改档案内容', 403)
  }
  ensureSeed()
  const section = getRaw(code, studentId, academicYear)
  if (section.status !== 'DRAFT' && section.status !== 'RETURNED') {
    throw new MockError('EC-40902', '当前状态不允许提交', 409)
  }
  // MENTOR 的必填内容在子表（需求 4.7），不能用主表 fields 判空，
  // 否则「辅导员追加了谈话、学生却永远提交不了」。
  if (code === 'mentor' && mentorCompletenessOf(studentId, academicYear) === 0) {
    throw new MockError('EC-42202', '请至少追加一条完整谈话记录后再提交', 422)
  }
  const missing = (code === 'mentor' ? [] : defsOf(code))
    .filter((def) => def.required && def.source !== 'SYS')
    .filter((def) => {
      const value = section.fields[def.key] ?? section.pending[def.key]
      return value == null || String(value).trim() === ''
    })
  if (missing.length > 0) {
    throw new MockError('EC-42202', `还有 ${missing.length} 项必填未完成`, 422)
  }
  section.status = 'SUBMITTED'
  section.submitCount = (section.submitCount ?? 0) + 1
  section.submittedAt = new Date().toISOString().slice(0, 19)
  section.completeness = code === 'mentor'
    ? mentorCompletenessOf(studentId, academicYear)
    : completeOf(section)
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
  userId: number,
): { fieldCode: string; fieldName?: string; value?: unknown; confirmStatus: string } {
  ensureSeed()
  if (role !== 'COU' || CLASS_COUNSELOR[classOfStudent(studentId, academicYear)] !== userId) {
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
      statusLabel: STATUS_LABELS[section.status],
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
  // MENTOR 没有主表字段值，审核就绪改按子表判定（后端 MentorCompletenessProvider 同口径）。
  if (code === 'mentor' && mentorCompletenessOf(studentId, academicYear) === 0) {
    throw new MockError('EC-42202', '请退回补填：导师寄语（至少一条完整谈话记录）', 422)
  }
  for (const def of code === 'mentor' ? [] : defsOf(code)) {
    if (Object.prototype.hasOwnProperty.call(section.pending, def.key))
      throw new MockError('EC-40902', '请先确认或驳回：' + def.name, 409)
    if (def.required && def.source !== 'SYS' && !String(section.fields[def.key] ?? '').trim())
      throw new MockError('EC-42202', '请退回补填：' + def.name, 422)
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

/**
 * 完成标记读取权限：学生限本人，辅导员限本班，管理员需持临时授权。
 *
 * <p>与后端 CompletionServiceImpl 一致：这里只读店铺里的记录，不会因为查询而给缺块学生补建档案，
 * 否则「打开一次审核详情就多出六条草稿」，与真实后端行为相反。</p>
 */
function assertCanReadCompletion(
  studentId: number,
  year: string,
  role: UserRole,
  userId: number,
  tempGrant: boolean,
): void {
  if (role === 'STU') {
    if (studentId !== userId) {
      throw new MockError('EC-40302', '只能查看本人档案的完成标记', 403)
    }
    return
  }
  if (role === 'COU') {
    if (CLASS_COUNSELOR[classOfStudent(studentId, year)] !== userId) {
      throw new MockError('EC-40302', '该学生不在你负责的班级内', 403)
    }
    return
  }
  if (!tempGrant) {
    throw new MockError('EC-40302', '无权查看完成标记', 403)
  }
}

/** 完成标记：固定六项，缺块显示「未建档」并计入未完成清单（BR-S10）。 */
export function completionStatus(
  studentId: number,
  academicYear: string,
  role: UserRole,
  userId: number,
  tempGrant: boolean,
): CompletionStatus {
  ensureSeed()
  assertCanReadCompletion(studentId, academicYear, role, userId, tempGrant)

  const sections: CompletionSection[] = []
  const pendingSections: string[] = []
  let createdSectionCount = 0
  let completedSectionCount = 0
  for (const code of ALL_SECTIONS) {
    const raw = store.get(keyOf(code, studentId, academicYear))
    const completed = raw ? COMPLETED_STATUSES.includes(raw.status) : false
    if (raw) {
      createdSectionCount += 1
    }
    if (completed) {
      completedSectionCount += 1
    } else {
      pendingSections.push(SECTION_NAMES[code])
    }
    sections.push({
      sectionCode: code,
      sectionName: SECTION_NAMES[code],
      status: raw?.status,
      statusLabel: raw ? STATUS_LABELS[raw.status] : '未建档',
      completed,
    })
  }

  const basic = store.get(keyOf('basic', studentId, academicYear))
  const classCode = classOfStudent(studentId, academicYear)
  return {
    studentId,
    studentNo: String(basic?.fields.studentNo ?? STUDENTS[studentId]?.no ?? ''),
    studentName: String(basic?.fields.name ?? STUDENTS[studentId]?.name ?? ''),
    classCode,
    className: CLASS_NAMES[classCode],
    academicYear,
    completed: completedSectionCount >= ALL_SECTIONS.length,
    createdSectionCount,
    completedSectionCount,
    pendingSections,
    sections,
  }
}

/**
 * 手动重算完成标记（运维补偿）。
 *
 * <p>mock 不保存派生标记，因此这里只回算并返回统计；{@code changedStudents} 恒为 0，
 * 真实后端会在标记翻转时写库并返回 1。</p>
 */
export function refreshCompletion(
  academicYear: string,
  studentId: number | undefined,
  role: UserRole,
  userId: number,
  tempGrant: boolean,
): CompletionRefreshResult {
  ensureSeed()
  if (role !== 'COU' && !(role === 'ADMIN' && tempGrant)) {
    throw new MockError('EC-40302', '仅管理员或辅导员可以重算完成标记', 403)
  }

  const refreshedAt = new Date().toISOString().slice(0, 19)
  if (studentId) {
    const status = completionStatus(studentId, academicYear, role, userId, tempGrant)
    return {
      academicYear,
      trigger: 'MANUAL',
      checkedStudents: status.createdSectionCount > 0 ? 1 : 0,
      completedStudents: status.completed ? 1 : 0,
      changedStudents: 0,
      refreshedAt,
    }
  }

  if (!(role === 'ADMIN' && tempGrant)) {
    throw new MockError('EC-40302', '整学年重算仅管理员（持临时授权）可执行', 403)
  }
  const studentIds = new Set<number>()
  for (const section of store.values()) {
    if (section.academicYear === academicYear) {
      studentIds.add(section.studentId)
    }
  }
  let completedStudents = 0
  for (const id of studentIds) {
    const status = completionStatus(id, academicYear, role, userId, tempGrant)
    if (status.completed) {
      completedStudents += 1
    }
  }
  return {
    academicYear,
    trigger: 'MANUAL',
    checkedStudents: studentIds.size,
    completedStudents,
    changedStudents: 0,
    refreshedAt,
  }
}


/**
 * 首页进度聚合（需求未编号，按「首页进度聚合」实现）。
 *
 * <p>口径与后端一致：只统计本学年「已建档」学生，完成标记按「六板块全部 APPROVED / LOCKED」现算。
 * 权限先判范围再算数——学生与无授权管理员直接 403，不返回空壳数据。</p>
 */
export function dashboardOverview(
  academicYear: string,
  classCode: string | undefined,
  role: UserRole,
  userId: number,
  tempGrant: boolean,
): DashboardOverview {
  ensureSeed()
  if (role === 'STU') {
    throw new MockError('EC-40302', '学生请在各板块页面查看本人进度', 403)
  }
  if (role === 'ADMIN' && !tempGrant) {
    throw new MockError('EC-40302', '管理员默认无业务数据权限', 403)
  }
  if (classCode && role === 'COU' && CLASS_COUNSELOR[classCode] !== userId) {
    throw new MockError('EC-40302', '只能查看本班数据', 403)
  }

  const sections: SectionProgress[] = ALL_SECTIONS.map((code) => ({
    sectionCode: code,
    sectionName: SECTION_NAMES[code],
    studentCount: 0,
    draft: 0,
    submitted: 0,
    returned: 0,
    approved: 0,
    locked: 0,
    completionRate: 0,
  }))
  const sectionOf = new Map<SectionCode, SectionProgress>(sections.map((item) => [item.sectionCode, item]))

  interface ClassBucket {
    students: Set<number>
    completed: Set<number>
    pendingReview: number
    returnedProfiles: number
    draftProfiles: number
  }
  const buckets = new Map<string, ClassBucket>()
  const sectionsByStudent = new Map<number, MockSection[]>()

  for (const section of store.values()) {
    if (section.academicYear !== academicYear) {
      continue
    }
    const classId = classOfStudent(section.studentId, academicYear)
    if (role === 'COU' && CLASS_COUNSELOR[classId] !== userId) {
      continue
    }
    if (classCode && classId !== classCode) {
      continue
    }

    const progress = sectionOf.get(section.sectionCode.toLowerCase() as SectionCode)
    if (progress) {
      progress.studentCount += 1
      if (section.status === 'DRAFT') progress.draft += 1
      if (section.status === 'SUBMITTED') progress.submitted += 1
      if (section.status === 'RETURNED') progress.returned += 1
      if (section.status === 'APPROVED') progress.approved += 1
      if (section.status === 'LOCKED') progress.locked += 1
    }

    const bucket = buckets.get(classId) ?? {
      students: new Set<number>(),
      completed: new Set<number>(),
      pendingReview: 0,
      returnedProfiles: 0,
      draftProfiles: 0,
    }
    bucket.students.add(section.studentId)
    if (section.status === 'SUBMITTED') bucket.pendingReview += 1
    if (section.status === 'RETURNED') bucket.returnedProfiles += 1
    if (section.status === 'DRAFT') bucket.draftProfiles += 1
    buckets.set(classId, bucket)

    const list = sectionsByStudent.get(section.studentId) ?? []
    list.push(section)
    sectionsByStudent.set(section.studentId, list)
  }

  // 六板块全部完成的学生
  for (const [studentId, list] of sectionsByStudent) {
    const done = new Set(list.filter((item) => COMPLETED_STATUSES.includes(item.status)).map((item) => item.sectionCode))
    if (done.size < ALL_SECTIONS.length) {
      continue
    }
    const classId = classOfStudent(studentId, academicYear)
    buckets.get(classId)?.completed.add(studentId)
  }

  for (const progress of sections) {
    progress.completionRate = percent(progress.approved + progress.locked, progress.studentCount)
  }

  const classes = [...buckets.entries()]
    .map(([classId, bucket]) => ({
      classCode: classId,
      className: CLASS_NAMES[classId],
      studentTotal: bucket.students.size,
      completedStudents: bucket.completed.size,
      pendingReview: bucket.pendingReview,
      completionRate: percent(bucket.completed.size, bucket.students.size),
    }))
    .sort((a, b) => String(a.className ?? a.classCode).localeCompare(String(b.className ?? b.classCode)))

  let studentTotal = 0
  let completedStudents = 0
  let pendingReview = 0
  let returnedProfiles = 0
  let draftProfiles = 0
  for (const bucket of buckets.values()) {
    studentTotal += bucket.students.size
    completedStudents += bucket.completed.size
    pendingReview += bucket.pendingReview
    returnedProfiles += bucket.returnedProfiles
    draftProfiles += bucket.draftProfiles
  }

  return {
    academicYear,
    scopeLabel: classCode ? CLASS_NAMES[classCode] : role === 'COU' ? '我的班级' : '全校',
    studentTotal,
    completedStudents,
    completionRate: percent(completedStudents, studentTotal),
    pendingReview,
    returnedProfiles,
    draftProfiles,
    sections,
    classes,
  }
}

/** 百分比一位小数，分母为 0 时返回 0，与后端 rate() 一致。 */
function percent(part: number, total: number): number {
  if (total <= 0) {
    return 0
  }
  return Math.round((part * 1000) / total) / 10
}

/** 重置内存数据，供测试或热更新使用。 */
export function resetStore(): void {
  store.clear()
}

/**
 * 可建档班级（S-1-01）：仅返回当前辅导员带班的班级，与后端数据范围一致。
 *
 * <p>「已建档」按六板块实例是否齐全判断，不要求已填写；这样重复点「生成」时
 * 页面能明确显示没有可做的事。</p>
 */
export function listEnrollClasses(
  academicYear: string,
  role: UserRole,
  userId: number,
): EnrollClass[] {
  ensureSeed()
  if (role !== 'COU') {
    throw new MockError('EC-40302', '仅辅导员可以为本班学生建档', 403)
  }
  const codes = Object.keys(CLASS_NAMES).filter((code) => CLASS_COUNSELOR[code] === userId)
  const allStudents = Object.entries(STUDENTS).map(([id, student]) => ({ id: Number(id), ...student }))
  return codes.map((code) => {
    const students = allStudents.filter((item) => item.classId === code)
    const enrolledCount = students.filter(
      (item) => existingSections(item.id, academicYear).length >= ALL_SECTIONS.length,
    ).length
    return {
      classCode: code,
      className: CLASS_NAMES[code] ?? code,
      majorName: CLASS_INFO[code]?.major,
      gradeYear: CLASS_INFO[code]?.grade,
      studentCount: students.length,
      enrolledStudentCount: enrolledCount,
      missingStudentCount: students.length - enrolledCount,
      enrolled: students.length > 0 && enrolledCount === students.length,
    }
  })
}

/**
 * 批量生成六板块空档案（S-1-01 / EX-01）。
 *
 * <p>与后端一致：逐板块判断是否已存在，缺哪块补哪块；已存在的绝不覆盖。
 * 学生清单只从所选班级的在读学生里取，不接受外部传入的学生 ID。</p>
 */
export function batchCreateProfiles(
  academicYear: string,
  classCodes: string[],
  role: UserRole,
  userId: number,
): BatchCreateResult {
  ensureSeed()
  if (role !== 'COU') {
    throw new MockError('EC-40302', '仅辅导员可以为本班学生建档', 403)
  }
  if (!Array.isArray(classCodes) || classCodes.length === 0) {
    throw new MockError('EC-40002', '请至少选择一个班级', 400)
  }
  const unknown = classCodes.filter((code) => !CLASS_NAMES[code])
  if (unknown.length > 0) {
    throw new MockError('EC-40002', `班级不存在或已停用：${unknown.join('、')}`, 400)
  }
  const foreign = classCodes.filter((code) => CLASS_COUNSELOR[code] !== userId)
  if (foreign.length > 0) {
    throw new MockError('EC-40302', '只能为本班学生建档', 403)
  }

  const students = Object.entries(STUDENTS)
    .map(([id, student]) => ({ id: Number(id), ...student }))
    .filter((student) => classCodes.includes(student.classId))
    .sort((left, right) => left.no.localeCompare(right.no))
  if (students.length === 0) {
    throw new MockError('EC-40002', '所选班级下没有在读学生', 400)
  }

  const items: EnrollStudentResult[] = []
  let createdProfiles = 0
  let existingProfiles = 0
  for (const student of students) {
    const owned = existingSections(student.id, academicYear)
    const missing = ALL_SECTIONS.filter((code) => !owned.includes(code))
    for (const code of missing) {
      createSection(code, student.id, academicYear, 'DRAFT')
      createdProfiles++
    }
    existingProfiles += owned.length
    items.push({
      studentId: student.id,
      studentNo: student.no,
      studentName: student.name,
      classCode: student.classId,
      className: CLASS_NAMES[student.classId],
      createdSections: missing.map((code) => code.toUpperCase()),
      skippedSectionCount: owned.length,
      status: missing.length === 0 ? 'SKIPPED' : 'CREATED',
    })
  }

  const createdStudents = items.filter((item) => item.status === 'CREATED').length
  return {
    academicYear,
    classCodes: [...classCodes],
    totalStudents: students.length,
    createdStudents,
    skippedStudents: students.length - createdStudents,
    createdProfiles,
    existingProfiles,
    students: items,
  }
}

/** 归档候选人（S-1-11）：六板块齐全且全部通过才可归档；已归档计入完成。 */
export function listArchiveCandidates(
  academicYear: string,
  role: UserRole,
  userId: number,
  tempGrant: boolean,
  classCode?: string,
): ArchiveCandidate[] {
  ensureSeed()
  if (role === 'STU') {
    throw new MockError('EC-40302', '无权查看归档名单', 403)
  }
  if (role === 'ADMIN' && !tempGrant) {
    throw new MockError('EC-40302', '管理员默认无业务数据权限', 403)
  }
  const scope =
    role === 'COU'
      ? Object.keys(CLASS_NAMES).filter((code) => CLASS_COUNSELOR[code] === userId)
      : Object.keys(CLASS_NAMES)
  if (classCode && !scope.includes(classCode)) {
    throw new MockError('EC-40302', '无权访问该班级的归档名单', 403)
  }
  const targetClasses = classCode ? [classCode] : scope
  const students = Object.entries(STUDENTS)
    .map(([id, student]) => ({ id: Number(id), ...student }))
    .filter((student) => targetClasses.includes(student.classId))
    .sort((left, right) => left.no.localeCompare(right.no))

  return students.map((student) => {
    const owned = existingSections(student.id, academicYear)
    const incomplete: ArchiveSectionStatus[] = []
    let approvedCount = 0
    let lockedCount = 0
    for (const code of ALL_SECTIONS) {
      const section = store.get(keyOf(code, student.id, academicYear))
      if (!section) {
        incomplete.push({
          sectionCode: code.toUpperCase(),
          sectionName: SECTION_NAMES[code],
          statusLabel: '未建档',
        })
        continue
      }
      if (section.status === 'APPROVED') {
        approvedCount++
      } else if (section.status === 'LOCKED') {
        lockedCount++
      } else {
        incomplete.push({
          sectionCode: code.toUpperCase(),
          sectionName: SECTION_NAMES[code],
          status: section.status,
          statusLabel: STATUS_LABELS[section.status],
        })
      }
    }
    return {
      studentId: student.id,
      studentNo: student.no,
      studentName: student.name,
      classCode: student.classId,
      className: CLASS_NAMES[student.classId],
      sectionCount: owned.length,
      approvedCount,
      lockedCount,
      incompleteSections: incomplete,
      archivable: incomplete.length === 0 && owned.length === ALL_SECTIONS.length,
      archived: lockedCount === ALL_SECTIONS.length,
    }
  })
}

/**
 * 批量归档（S-1-11 / EX-12）。
 *
 * <p>先全量校验再统一写库：名单里只要有一个人没完成，整批都不动，
 * 返回未完成清单；成功时把六板块置为 LOCKED。</p>
 */
export function archiveStudents(
  academicYear: string,
  studentIds: number[],
  role: UserRole,
  userId: number,
): ArchiveResult {
  ensureSeed()
  if (role !== 'COU') {
    throw new MockError('EC-40302', '仅辅导员可以执行毕业归档', 403)
  }
  if (!Array.isArray(studentIds) || studentIds.length === 0) {
    throw new MockError('EC-40002', '请至少选择一名学生', 400)
  }
  const uniqueIds = [...new Set(studentIds.map(Number))]
  const allStudents = Object.entries(STUDENTS).map(([id, student]) => ({
    id: Number(id),
    ...student,
  }))
  const students = uniqueIds
    .map((id) => allStudents.find((student) => student.id === id))
    .filter((student): student is (typeof allStudents)[number] => student != null)
  if (students.length !== uniqueIds.length) {
    throw new MockError('EC-40002', '部分学生不存在，请刷新名单后重试', 400)
  }
  for (const student of students) {
    if (CLASS_COUNSELOR[student.classId] !== userId) {
      throw new MockError('EC-40302', '只能归档本班学生', 403)
    }
  }

  const incomplete: ArchiveStudentResult[] = []
  const archivable: number[] = []
  let skippedStudents = 0
  for (const student of students) {
    const missing = ALL_SECTIONS.filter((code) => {
      const section = store.get(keyOf(code, student.id, academicYear))
      return !section || (section.status !== 'APPROVED' && section.status !== 'LOCKED')
    }).map((code) => SECTION_NAMES[code])
    if (missing.length > 0) {
      incomplete.push({
        studentId: student.id,
        studentNo: student.no,
        studentName: student.name,
        className: CLASS_NAMES[student.classId],
        status: 'INCOMPLETE',
        missingSections: missing,
      })
      continue
    }
    const allLocked = ALL_SECTIONS.every(
      (code) => store.get(keyOf(code, student.id, academicYear))?.status === 'LOCKED',
    )
    if (allLocked) {
      skippedStudents++
    } else {
      archivable.push(student.id)
    }
  }

  if (incomplete.length > 0) {
    return {
      academicYear,
      requestedStudents: students.length,
      archivedStudents: 0,
      archivedProfiles: 0,
      skippedStudents,
      rejected: true,
      students: incomplete,
    }
  }

  for (const studentId of archivable) {
    for (const code of ALL_SECTIONS) {
      const section = store.get(keyOf(code, studentId, academicYear))
      if (section) {
        section.status = 'LOCKED'
      }
    }
  }

  return {
    academicYear,
    requestedStudents: students.length,
    archivedStudents: archivable.length,
    archivedProfiles: archivable.length * ALL_SECTIONS.length,
    skippedStudents,
    rejected: false,
    students: archivable.map((studentId) => {
      const student = STUDENTS[studentId]!
      return {
        studentId,
        studentNo: student.no,
        studentName: student.name,
        className: CLASS_NAMES[student.classId],
        status: 'ARCHIVED' as const,
        missingSections: [],
      }
    }),
  }
}
