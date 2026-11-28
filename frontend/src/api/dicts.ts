import { request } from './http'

/** 数据字典（F-15）。 */

export interface DictItem {
  id: number
  value: string
  label: string
  sortNo: number
  status: number
  disabled: boolean
}

export interface Dict {
  id: number
  code: string
  name: string
  fieldCode?: string
  items: DictItem[]
}

export function fetchDict(code: string): Promise<Dict> {
  return request<Dict>({ url: `/dict/${code}`, method: 'GET' })
}

export function fetchDicts(): Promise<Dict[]> {
  return request<Dict[]>({ url: '/admin/dicts', method: 'GET' })
}

export function addDictItem(code: string, value: string, label: string, sortNo: number): Promise<Dict> {
  return request<Dict>({
    url: `/admin/dicts/${code}/items`,
    method: 'POST',
    data: { value, label, sortNo },
  })
}

export function updateDictItemStatus(code: string, itemId: number, status: number): Promise<Dict> {
  return request<Dict>({
    url: `/admin/dicts/${code}/items/${itemId}/status`,
    method: 'PUT',
    params: { status },
  })
}
