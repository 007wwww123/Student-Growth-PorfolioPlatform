import type { SectionCode, SectionVO } from '../api/types'

/**
 * mock 数据仓库（内存态）。
 *
 * 字段名严格对齐后端 SaveDTO / VO：板块自身字段放在 fields 内，
 * 外层是公共信封。修改字段时请同步 openapi.yaml 与后端 DTO。
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

/** 各板块默认值，与 DTO 字段一一对应。 */
const TEMPLATES: Record<SectionCode, Record<string, unknown>> = {
  basic: {
    name: '林清和',
    studentNo: '2023010101',
    gender: '女',
    idCard: '3301**********0028',
    phone: '13800001234',
    email: 'linqinghe@example.edu.cn',
  },
  academic: {
    goalShort: '本学期绩点进入专业前 15%',
    goalLong: '争取保研，进入数据分析方向',
    strongSubjects: ['数据结构', '概率论'],
    weakSubjects: ['大学物理'],
    abilityNote: '编程实现能力较强，理论推导需要加强。',
  },
  interest: {
    hobbies: ['摄影', '羽毛球'],
    hobbyDesc: '加入校摄影社，负责活动跟拍。',
    skills: 'Photoshop、视频剪辑',
    ideal: '希望把摄影做成长期爱好。',
  },
  life: {
    orgRole: '班级学习委员',
    orgLevel: '班级',
    sleepHabit: '23:30 - 07:00',
    exerciseFreq: '每周 3 次',
    stressScore: 4,
    mood: '整体平稳，期中阶段略紧张',
  },
  career: {
    intention: '就业',
    industry: '互联网 / 数据服务',
    position: '数据分析师',
    plan: '大三暑假完成一段数据相关实习，补齐 SQL 与可视化能力。',
    support: '希望学院提供实习内推与简历指导。',
  },
  mentor: {
    talkDate: '2026-11-10',
    topic: '学期中段学业与心态',
    advice: '保持当前节奏，重点补强物理，尽早确定实习方向。',
    starLevel: 4,
    feedback: '已按建议调整复习计划。',
  },
}

/** 导师寄语由导师维护，学生只读。 */
const READ_ONLY_SECTIONS: SectionCode[] = ['mentor']

const store = new Map<string, SectionVO>()

function keyOf(code: SectionCode, studentId: number, academicYear: string): string {
  return `${code}:${studentId}:${academicYear}`
}

/** 取板块详情，不存在时按模板生成一份 DRAFT。 */
export function getSection(
  code: SectionCode,
  studentId: number,
  academicYear: string,
): SectionVO {
  const key = keyOf(code, studentId, academicYear)
  const cached = store.get(key)
  if (cached) {
    return cached
  }

  const created: SectionVO = {
    studentId,
    academicYear,
    sectionCode: code.toUpperCase(),
    sectionName: SECTION_NAMES[code],
    status: 'DRAFT',
    fields: structuredClone(TEMPLATES[code]),
    editable: !READ_ONLY_SECTIONS.includes(code),
  }
  store.set(key, created)
  return created
}

/** 保存草稿，仅 DRAFT / RETURNED 允许写入。 */
export function saveSection(
  code: SectionCode,
  studentId: number,
  academicYear: string,
  payload: Record<string, unknown>,
): SectionVO {
  const current = getSection(code, studentId, academicYear)
  if (READ_ONLY_SECTIONS.includes(code)) {
    throw new MockError('EC-40302', '该板块由导师维护', 403)
  }
  if (current.status !== 'DRAFT' && current.status !== 'RETURNED') {
    throw new MockError('EC-40902', '当前状态不允许该操作', 409)
  }

  const fields = { ...current.fields }
  for (const [field, value] of Object.entries(payload)) {
    if (field === 'academicYear') {
      continue
    }
    fields[field] = value
  }

  const next: SectionVO = { ...current, fields }
  store.set(keyOf(code, studentId, academicYear), next)
  return next
}

/** 提交板块，DRAFT / RETURNED → SUBMITTED。 */
export function submitSection(
  code: SectionCode,
  studentId: number,
  academicYear: string,
): SectionVO {
  const current = getSection(code, studentId, academicYear)
  if (READ_ONLY_SECTIONS.includes(code)) {
    throw new MockError('EC-40302', '该板块由导师维护', 403)
  }
  if (current.status !== 'DRAFT' && current.status !== 'RETURNED') {
    throw new MockError('EC-40902', '当前状态不允许该操作', 409)
  }

  const next: SectionVO = { ...current, status: 'SUBMITTED', editable: false }
  store.set(keyOf(code, studentId, academicYear), next)
  return next
}

/** 重置内存数据，供测试或热更新使用。 */
export function resetStore(): void {
  store.clear()
}
