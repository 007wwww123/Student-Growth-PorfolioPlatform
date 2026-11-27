import { request } from './http'
import type { BatchCreateResult, EnrollClass } from './types'

/**
 * 批量建档接口（S-1-01）。
 *
 * 与后端 {@code ProfileEnrollController} 一一对应：先查本班建档进度，再按班级生成六板块空档案。
 * 学生清单由服务端按学生主数据取，前端不传学生 ID。
 */

/** 查询当前辅导员可建档的班级与建档进度。 */
export function fetchEnrollClasses(academicYear?: string): Promise<EnrollClass[]> {
  return request<EnrollClass[]>({
    url: '/profiles/classes',
    method: 'GET',
    params: academicYear ? { academicYear } : undefined,
  })
}

/**
 * 批量生成六板块空档案（DRAFT）。
 *
 * 已存在的板块一律跳过，因此可重复调用；第二次的结果是 createdProfiles = 0。
 */
export function batchCreateProfiles(payload: {
  academicYear: string
  classCodes: string[]
}): Promise<BatchCreateResult> {
  return request<BatchCreateResult>({
    url: '/profiles/batch-create',
    method: 'POST',
    data: payload,
  })
}
