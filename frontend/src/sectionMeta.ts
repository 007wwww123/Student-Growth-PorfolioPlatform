import type { ProfileStatus, SectionCode } from './api/types'

/**
 * 板块元数据。
 *
 * 后端最终会按 arc_field_def 动态下发字段定义（需求文档 4.1），
 * 这里先固化一份用于骨架期渲染与联调；接口就绪后改为从字段定义接口读取，
 * 页面结构不需要改。
 */

export type FieldType = 'text' | 'textarea' | 'number' | 'date' | 'tags'

export interface SectionFieldMeta {
  /** 与后端 SaveDTO 的属性名一致 */
  key: string
  label: string
  type: FieldType
  /** 可用字符数，仅用于输入框提示 */
  maxLength?: number
}

export interface SectionMeta {
  code: SectionCode
  /** 与后端 SectionCodeEnum 的中文名一致 */
  name: string
  /** 一句话说明该板块收什么内容 */
  summary: string
  fields: SectionFieldMeta[]
}

export const SECTIONS: SectionMeta[] = [
  {
    code: 'basic',
    name: '基本信息',
    summary: '学籍与联系方式，由学校维护，学生仅可修改允许编辑的字段',
    fields: [
      { key: 'name', label: '姓名', type: 'text', maxLength: 32 },
      { key: 'studentNo', label: '学号', type: 'text', maxLength: 20 },
      { key: 'gender', label: '性别', type: 'text' },
      { key: 'idCard', label: '身份证号', type: 'text', maxLength: 18 },
      { key: 'phone', label: '手机号', type: 'text', maxLength: 11 },
      { key: 'email', label: '邮箱', type: 'text', maxLength: 64 },
    ],
  },
  {
    code: 'academic',
    name: '学业发展',
    summary: '学业目标、优势与待提升科目、能力自评',
    fields: [
      { key: 'goalShort', label: '短期目标', type: 'text' },
      { key: 'goalLong', label: '长期目标', type: 'textarea' },
      { key: 'strongSubjects', label: '优势科目', type: 'tags' },
      { key: 'weakSubjects', label: '待提升科目', type: 'tags' },
      { key: 'abilityNote', label: '能力自评', type: 'textarea' },
    ],
  },
  {
    code: 'interest',
    name: '兴趣特长',
    summary: '兴趣方向、特长技能与理想状态',
    fields: [
      { key: 'hobbies', label: '兴趣标签', type: 'tags' },
      { key: 'hobbyDesc', label: '兴趣描述', type: 'textarea' },
      { key: 'skills', label: '特长技能', type: 'text' },
      { key: 'ideal', label: '理想状态', type: 'textarea' },
    ],
  },
  {
    code: 'life',
    name: '生活心理',
    summary: '组织任职、作息运动、压力与情绪观察；心理自评仅本人可见',
    fields: [
      { key: 'orgRole', label: '社团/学生工作职务', type: 'text' },
      { key: 'orgLevel', label: '参与度', type: 'text' },
      { key: 'sleepHabit', label: '作息习惯', type: 'text' },
      { key: 'exerciseFreq', label: '运动频率', type: 'text' },
      { key: 'personality', label: '个性描述', type: 'textarea' },
      { key: 'econSelf', label: '经济状况自评', type: 'text' },
      { key: 'aidSource', label: '资助来源', type: 'tags' },
      { key: 'stressScore', label: '压力自评', type: 'number' },
      { key: 'stressSource', label: '压力来源', type: 'tags' },
      { key: 'mood', label: '整体情绪', type: 'text' },
      { key: 'majorEvent', label: '重大事件备忘', type: 'textarea' },
      { key: 'careFlag', label: '关注标记', type: 'text' },
    ],
  },
  {
    code: 'career',
    name: '职业规划',
    summary: '就业意向、目标行业岗位与实施计划',
    fields: [
      { key: 'intention', label: '就业意向', type: 'text' },
      { key: 'industry', label: '目标行业', type: 'text' },
      { key: 'position', label: '目标岗位', type: 'text' },
      { key: 'plan', label: '实施计划', type: 'textarea' },
      { key: 'support', label: '需要支持', type: 'textarea' },
    ],
  },
  {
    code: 'mentor',
    name: '导师寄语',
    summary: '导师谈话记录与建议，学生只读',
    fields: [
      { key: 'talkDate', label: '谈话日期', type: 'date' },
      { key: 'topic', label: '谈话主题', type: 'text' },
      { key: 'advice', label: '导师建议', type: 'textarea' },
      { key: 'starLevel', label: '星级评分', type: 'number' },
      { key: 'feedback', label: '学生反馈', type: 'textarea' },
    ],
  },
]

export const SECTION_MAP: Record<SectionCode, SectionMeta> = SECTIONS.reduce(
  (acc, section) => {
    acc[section.code] = section
    return acc
  },
  {} as Record<SectionCode, SectionMeta>,
)

/** 状态中文名，与后端 ProfileStatusEnum 一致。 */
export const STATUS_LABELS: Record<ProfileStatus, string> = {
  DRAFT: '草稿',
  SUBMITTED: '已提交',
  RETURNED: '已退回',
  APPROVED: '已通过',
  LOCKED: '已归档',
}

/** 骨架期默认联调参数，接入登录后从会话中读取。 */
export const DEMO_STUDENT_ID = 1001
export const DEMO_ACADEMIC_YEAR = '2025-2026-1'

/**
 * 按当前日期推算学年学期，规则与后端 AcademicYearUtils 一致：
 * 9 月起为秋季学期（-1），2–8 月为春季学期（-2），1 月仍算上一学年的秋季。
 */
export function currentAcademicYear(now: Date = new Date()): string {
  const month = now.getMonth() + 1
  const year = now.getFullYear()
  if (month >= 9) {
    return `${year}-${year + 1}-1`
  }
  if (month <= 1) {
    return `${year - 1}-${year}-1`
  }
  return `${year - 1}-${year}-2`
}
