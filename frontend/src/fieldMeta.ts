import type { FieldMeta } from './api/types'
import type { SectionFieldMeta } from './sectionMeta'

/**
 * 字段元数据到渲染描述的转换。
 *
 * <p>学生板块页与审核详情页共用这一份映射：控件类型、必填、可写、只读原因
 * 全部按服务端 fieldMeta 计算，页面本身不认识具体业务字段。</p>
 */

/** 表单控件类型：比 sectionMeta 的 FieldType 多出 select（枚举）与 file（附件）。 */
export type ControlType = 'text' | 'textarea' | 'number' | 'date' | 'tags' | 'select' | 'file'

export interface RenderField {
  key: string
  label: string
  type: ControlType
  editable: boolean
  required: boolean
  maxLength?: number
  options?: Array<{ value: string; label: string }>
  /** 只读原因，展示在控件下方 */
  lockHint?: string
  /** 后端本次返回值是否已脱敏 */
  masked?: boolean
  /** 敏感级别：0 普通 / 1 脱敏 / 2 仅辅导员可查看完整值 */
  sensitive?: number
  /** 字段编码，如 basic.idCard；查看明文时要用它 */
  fieldCode?: string
}

/** 后端 FieldMetaVO.fieldType 到控件的映射。 */
const CONTROL_BY_FIELD_TYPE: Record<string, ControlType> = {
  TEXT: 'text',
  TEXTAREA: 'textarea',
  NUMBER: 'number',
  DATE: 'date',
  SELECT: 'select',
  RADIO: 'select',
  MULTI_SELECT: 'tags',
  CHECKBOX: 'tags',
  FILE: 'file',
}

/** 只读原因：按来源与敏感级别给出可读提示，不把权限码直接抛给用户。 */
export function lockHintOf(item: FieldMeta): string {
  if (item.source === 'SIS' || item.source === 'SYS') {
    return '由学校维护，不可修改'
  }
  if (item.sensitive != null && item.sensitive >= 2) {
    return '无权限查看完整内容'
  }
  return '当前状态不可修改'
}

/**
 * 生成渲染字段。
 *
 * 优先用服务端 fieldMeta（含权限位与枚举选项）；接口尚未下发时回落到板块骨架配置，
 * 保证 mock 或旧接口下页面仍能渲染。
 */
export function renderFieldsOf(
  metas?: FieldMeta[],
  fallback?: SectionFieldMeta[],
): RenderField[] {
  if (metas && metas.length > 0) {
    return metas.map((item) => ({
      key: item.key,
      label: item.fieldName,
      type: CONTROL_BY_FIELD_TYPE[item.fieldType] ?? 'text',
      editable: item.editable === true,
      required: item.required === true,
      options: item.options,
      lockHint: item.editable === true ? undefined : lockHintOf(item),
      masked: item.masked,
      sensitive: item.sensitive,
      fieldCode: item.fieldCode,
    }))
  }

  return (fallback ?? []).map((item) => ({
    key: item.key,
    label: item.label,
    type: item.type,
    editable: true,
    required: false,
    maxLength: item.maxLength,
  }))
}

/** 把接口返回的 fields 铺成表单值：数组用顿号连接，其余转字符串。 */
export function formFromFields(
  fields: Record<string, unknown> | undefined,
  renderFields: RenderField[],
): Record<string, string> {
  const form: Record<string, string> = {}
  for (const field of renderFields) {
    const raw = fields?.[field.key]
    if (Array.isArray(raw)) {
      form[field.key] = raw.join('、')
    } else {
      form[field.key] = raw == null ? '' : String(raw)
    }
  }
  return form
}

/** 值转展示文本，空值统一显示为「—」。 */
export function formatValue(value: unknown): string {
  if (value == null || value === '') {
    return '—'
  }
  if (Array.isArray(value)) {
    return value.length === 0 ? '—' : value.join('、')
  }
  return String(value)
}

/**
 * 待确认值条目（BR-F06）。
 *
 * 学生改动「需辅导员确认」的字段后，新值进入待确认区，对外仍显示旧值。
 */
export interface PendingItem {
  /** 字段短名，回调时用它反查 fieldCode */
  key: string
  label: string
  /** 当前生效值 */
  current: string
  /** 学生提交的新值，等待确认 */
  pending: string
}
