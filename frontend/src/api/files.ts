import http, { request } from './http'

/** 附件上传/下载（B-6）。 */

export interface FileMeta {
  id: number
  studentId: number
  bizType?: string
  bizId?: number
  originalName: string
  contentType: string
  sizeBytes: number
  createdAt: string
}

export function uploadFile(
  file: File,
  studentId: number,
  bizType = 'OTHER',
  bizId?: number,
): Promise<FileMeta> {
  const form = new FormData()
  form.append('file', file)
  form.append('studentId', String(studentId))
  form.append('bizType', bizType)
  if (bizId != null) form.append('bizId', String(bizId))
  return request<FileMeta>({
    url: '/files',
    method: 'POST',
    data: form,
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export async function downloadFile(id: number, name = `file-${id}`): Promise<void> {
  const response = await http.get(`/files/${id}/download`, { responseType: 'blob' })
  const url = URL.createObjectURL(new Blob([response.data]))
  const link = document.createElement('a')
  link.href = url
  link.download = name
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}
