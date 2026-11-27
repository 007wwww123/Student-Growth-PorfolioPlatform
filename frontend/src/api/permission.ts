import { request } from './http'
import type { PermissionMatrix, PermissionSaveResult } from './types'

/**
 * 字段编辑权限配置接口（管理员模式）。
 *
 * <p>路径与板块内容接口分离：`/admin/permissions` 只读写字段定义，不接触学生档案，
 * 因此管理员配置权限时看不到也不需要看任何业务数据。</p>
 */

/** 读取六大板块的权限矩阵。 */
export function fetchPermissionMatrix(): Promise<PermissionMatrix> {
  return request<PermissionMatrix>({
    url: '/admin/permissions',
    method: 'GET',
  })
}

/** 保存某板块的字段权限，保存即生效。 */
export function saveSectionPermissions(
  sectionCode: string,
  fields: Array<{ fieldCode: string; editableRoles: Array<'STU' | 'COU'> }>,
): Promise<PermissionSaveResult> {
  return request<PermissionSaveResult>({
    url: `/admin/permissions/${sectionCode.toLowerCase()}`,
    method: 'PUT',
    data: { fields },
  })
}

/** 恢复某板块的默认权限。 */
export function resetSectionPermissions(sectionCode: string): Promise<PermissionSaveResult> {
  return request<PermissionSaveResult>({
    url: `/admin/permissions/${sectionCode.toLowerCase()}/reset`,
    method: 'POST',
  })
}
