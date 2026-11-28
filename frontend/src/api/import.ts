import http, { request } from './http'

/** 学生批量导入（F-09）接口与视图类型。 */

export interface ImportRow {
  rowNo: number
  studentNo?: string
  name?: string
  valid: boolean
  errors: string[]
}

export interface ImportPreview {
  fileName: string
  headers: string[]
  expectedHeaders: string[]
  headerValid: boolean
  totalRows: number
  validRows: number
  invalidRows: number
  rows: ImportRow[]
}

export interface ImportResult {
  taskId: number
  totalRows: number
  successRows: number
  failedRows: number
  status: string
  errors: ImportRow[]
}

export interface ImportTask {
  id: number
  fileName?: string
  totalRows: number
  successRows: number
  failedRows: number
  status: string
  operatorId?: number
  createdAt?: string
}

export function previewStudents(file: File): Promise<ImportPreview> {
  return upload<ImportPreview>('/admin/import/students/preview', file)
}

export function executeStudentImport(file: File): Promise<ImportResult> {
  return upload<ImportResult>('/admin/import/students/execute', file)
}

export function fetchImportTasks(): Promise<ImportTask[]> {
  return request<ImportTask[]>({ url: '/admin/import/tasks', method: 'GET' })
}

function upload<T>(url: string, file: File): Promise<T> {
  const form = new FormData()
  form.append('file', file)
  return request<T>({
    url,
    method: 'POST',
    data: form,
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/** 下载模板或回执（二进制，不能走统一 Result 解包）。 */
export async function downloadExcel(url: string, fileName: string): Promise<void> {
  const response = await http.get(url, { responseType: 'blob' })
  const blob = new Blob([response.data], {
    type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  })
  const link = document.createElement('a')
  const objectUrl = URL.createObjectURL(blob)
  link.href = objectUrl
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(objectUrl)
}
