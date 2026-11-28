import { request } from './http'

/** 字段定义配置（F-04 / AC-007）接口与视图类型。 */

export interface FieldOptionItem {
  value: string
  label: string
}

export interface FieldDef {
  id: number
  sectionCode: string
  sectionName: string
  fieldCode: string
  fieldName: string
  fieldType: string
  fieldTypeLabel: string
  required: number
  source: string
  sourceLabel: string
  visibleRoles: string[]
  editableRoles: string[]
  validator: string
  ruleJson?: string
  optionsJson?: string
  options: FieldOptionItem[]
  sensitiveLevel: number
  sortNo: number
  status: number
  remark?: string
  hasValues: boolean
  systemOwned: boolean
}

export interface FieldMetaItem {
  code: string
  label: string
}

export interface FieldMeta {
  types: FieldMetaItem[]
  sources: FieldMetaItem[]
  validators: string[]
  roles: string[]
}

export interface FieldDefPayload {
  sectionCode?: string
  fieldCode?: string
  fieldName: string
  fieldType: string
  required: number
  source: string
  visibleRoles: string[]
  editableRoles: string[]
  validator: string
  ruleJson?: string
  optionsJson?: string
  sensitiveLevel: number
  sortNo?: number
  remark?: string
}

export function fetchFieldDefs(sectionCode: string): Promise<FieldDef[]> {
  return request<FieldDef[]>({ url: '/admin/fields', method: 'GET', params: { sectionCode } })
}

export function fetchFieldMeta(): Promise<FieldMeta> {
  return request<FieldMeta>({ url: '/admin/fields/meta', method: 'GET' })
}

export function createFieldDef(payload: FieldDefPayload): Promise<FieldDef> {
  return request<FieldDef>({ url: '/admin/fields', method: 'POST', data: payload })
}

export function updateFieldDef(id: number, payload: FieldDefPayload): Promise<FieldDef> {
  return request<FieldDef>({ url: `/admin/fields/${id}`, method: 'PUT', data: payload })
}

export function updateFieldDefStatus(id: number, status: number): Promise<void> {
  return request<void>({ url: `/admin/fields/${id}/status`, method: 'PUT', data: { status } })
}

export function updateFieldDefRequired(id: number, required: number): Promise<void> {
  return request<void>({ url: `/admin/fields/${id}/required`, method: 'PUT', data: { required } })
}
