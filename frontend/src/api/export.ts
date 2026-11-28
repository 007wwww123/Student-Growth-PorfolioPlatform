import http, { request } from './http'

/** 单人档案 PDF 导出（F-12）。 */

export interface ExportTask {
  id: number
  studentId: number
  academicYear: string
  status: string
  fileName?: string
  expireAt: string
}

export function createExportTask(studentId: number, academicYear: string): Promise<ExportTask> {
  return request<ExportTask>({
    url: `/export/profiles/${studentId}`,
    method: 'POST',
    params: { academicYear },
  })
}

/** 下载 PDF（二进制，不能走统一 Result 解包）。 */
export async function downloadExport(taskId: number): Promise<void> {
  const response = await http.get(`/export/tasks/${taskId}/download`, { responseType: 'blob' })
  const blob = new Blob([response.data], { type: 'application/pdf' })
  const link = document.createElement('a')
  const url = URL.createObjectURL(blob)
  link.href = url
  link.download = `student-profile-${taskId}.pdf`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}
