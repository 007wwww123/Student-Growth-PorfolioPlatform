// 用 msw/node 跑同一套 handlers，校验板块一学生端与审核端的响应结构、权限与状态机
import { setupServer } from 'msw/node'

// handlers 里用的是相对路径，Node 环境下需要 location 作为解析基准
if (!('location' in globalThis)) {
  Object.defineProperty(globalThis, 'location', {
    value: new URL('http://localhost/'),
    writable: true,
  })
}

const { handlers } = await import('../src/mocks/handlers')

const server = setupServer(...handlers)
server.listen({ onUnhandledRequest: 'error' })

const BASE = 'http://localhost/api'
const YEAR = '2025-2026-1'

/** 登录拿令牌：mock 的鉴权与后端同构，除登录外都需要 Bearer。 */
async function authHeaders(username: string, password: string): Promise<Record<string, string>> {
  const response = await call('/auth/login', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })
  if (response.body.code !== '0') {
    throw new Error(`登录失败 ${username}: ${response.body.code}`)
  }
  return { Authorization: `Bearer ${response.body.data.token}` }
}

const STU_1001 = await authHeaders('2024010101', 'Passw0rd!')
const STU_1004 = await authHeaders('2024010301', 'Passw0rd!')
const COU_9001 = await authHeaders('cou9001', 'Passw0rd!')
const COU_9002 = await authHeaders('cou9002', 'Passw0rd!')
const ADMIN = await authHeaders('admin0', 'Passw0rd!')
const ADMIN_GRANT = await authHeaders('admin', 'Passw0rd!')

async function call(path: string, init: RequestInit = {}) {
  const response = await fetch(BASE + path, init)
  const body = (await response.json()) as Record<string, any>
  return { status: response.status, body }
}

function json(method: string, headers: Record<string, string>, payload?: unknown): RequestInit {
  return {
    method,
    headers: { ...headers, 'content-type': 'application/json' },
    ...(payload === undefined ? {} : { body: JSON.stringify(payload) }),
  }
}

const results: string[] = []
function check(name: string, pass: boolean, detail: string) {
  results.push(`${pass ? 'PASS' : 'FAIL'}  ${name}  ${detail}`)
}

// 0. 鉴权：未登录、错误密码、会话信息
const anonymous = await call(`/sections/basic/1001?academicYear=${YEAR}`)
check('未登录访问受保护接口 401', anonymous.status === 401 && anonymous.body.code === 'EC-40101', `status=${anonymous.status} code=${anonymous.body.code}`)

const badLogin = await call('/auth/login', {
  method: 'POST',
  headers: { 'content-type': 'application/json' },
  body: JSON.stringify({ username: '2024010101', password: 'wrong' }),
})
check('错误密码 401', badLogin.status === 401 && badLogin.body.code === 'EC-40104', `status=${badLogin.status} code=${badLogin.body.code}`)

const me = await call('/auth/me', { headers: STU_1001 })
check(
  '会话信息',
  me.status === 200 && me.body.data.role === 'STU' && me.body.data.studentId === 1001,
  `role=${me.body.data?.role} studentId=${me.body.data?.studentId}`,
)

const spoofed = await call(`/review/tasks?academicYear=${YEAR}`, {
  headers: { 'X-User-Id': '9001', 'X-User-Role': 'COU' },
})
check('伪造身份请求头无效', spoofed.status === 401, `status=${spoofed.status} code=${spoofed.body.code}`)

// 1. 学生查看自己的草稿：敏感字段脱敏、字段清单来自 fieldMeta
const detail = await call(`/sections/basic/1001?academicYear=${YEAR}`, { headers: STU_1001 })
check(
  'GET 详情（学生）',
  detail.status === 200 &&
    detail.body.code === '0' &&
    detail.body.data.status === 'DRAFT' &&
    detail.body.data.fieldMeta.length === 16 &&
    detail.body.data.fields.idCard === '320102********1234',
  `status=${detail.body.data?.status} fields=${detail.body.data?.fieldMeta?.length} idCard=${detail.body.data?.fields?.idCard}`,
)

// 2. 六个板块都能取到
for (const code of ['basic', 'academic', 'interest', 'life', 'career', 'mentor']) {
  const r = await call(`/sections/${code}/1001?academicYear=${YEAR}`, { headers: STU_1001 })
  check(
    `GET ${code}`,
    r.status === 200 && r.body.code === '0' && Array.isArray(r.body.data.fieldMeta),
    `status=${r.status} fields=${r.body.data?.fieldMeta?.length}`,
  )
}

// 3. 扁平入参不再被接受：保存必须是信封结构
const flat = await call(`/sections/basic/1001`, json('PUT', STU_1001, { academicYear: YEAR, phone: '13900009999' }))
check('PUT 扁平入参被拒', flat.status === 400 && flat.body.code === 'EC-40002', `status=${flat.status} code=${flat.body.code}`)

// 4. 学校维护字段学生写不进去 -> EC-40301
const forbidden = await call(
  `/sections/basic/1001`,
  json('PUT', STU_1001, { academicYear: YEAR, fields: { name: '张三' } }),
)
check(
  'PUT 越权字段 403',
  forbidden.status === 403 && forbidden.body.code === 'EC-40301',
  `status=${forbidden.status} code=${forbidden.body.code}`,
)

// 5. 修改需确认字段：新值进待确认区，对外仍是旧值
const saved = await call(
  `/sections/basic/1001`,
  json('PUT', STU_1001, { academicYear: YEAR, fields: { phone: '13900009999' } }),
)
const afterSave = await call(`/sections/basic/1001?academicYear=${YEAR}`, { headers: STU_1001 })
const phoneMeta = afterSave.body.data.fieldMeta.find((item: any) => item.key === 'phone')
check(
  'PUT 生效 + 待确认',
  saved.status === 200 &&
    afterSave.body.data.fields.phone === '138****1234' &&
    phoneMeta.pendingConfirm === true,
  `phone=${afterSave.body.data.fields.phone} pending=${phoneMeta.pendingConfirm} pendingValue=${phoneMeta.pendingValue}`,
)

// 6. 学生不能自己确认待确认值
const stuConfirm = await call(
  `/sections/basic/1001/fields/basic.phone/confirm?academicYear=${YEAR}`,
  json('POST', STU_1001),
)
check('学生确认待确认值 403', stuConfirm.status === 403 && stuConfirm.body.code === 'EC-40302', `status=${stuConfirm.status}`)

// 7. 辅导员确认后新值生效
const couConfirm = await call(
  `/sections/basic/1001/fields/basic.phone/confirm?academicYear=${YEAR}`,
  json('POST', COU_9001),
)
const afterConfirm = await call(`/sections/basic/1001?academicYear=${YEAR}`, { headers: STU_1001 })
const phoneAfter = afterConfirm.body.data.fieldMeta.find((item: any) => item.key === 'phone')
check(
  '辅导员确认待确认值',
  couConfirm.status === 200 &&
    couConfirm.body.data.confirmStatus === 'APPROVED' &&
    phoneAfter.pendingConfirm === false &&
    afterConfirm.body.data.fields.phone === '139****9999',
  `status=${couConfirm.status} phone=${afterConfirm.body.data.fields.phone} pending=${phoneAfter.pendingConfirm}`,
)

// 8. 提交成功
const submitted = await call(`/sections/basic/1001/submit?academicYear=${YEAR}`, { method: 'POST', headers: STU_1001 })
const afterSubmit = await call(`/sections/basic/1001?academicYear=${YEAR}`, { headers: STU_1001 })
check(
  'POST 提交成功',
  submitted.status === 200 && afterSubmit.body.data.status === 'SUBMITTED',
  `status=${submitted.status} now=${afterSubmit.body.data.status} completeness=${afterSubmit.body.data.completeness}`,
)

// 9. 待审列表：学生 403，跨班辅导员看不到，本班辅导员看得到
const listAsStu = await call(`/review/tasks?academicYear=${YEAR}`, { headers: STU_1001 })
const listOwn = await call(`/review/tasks?academicYear=${YEAR}`, { headers: COU_9001 })
const listOther = await call(`/review/tasks?academicYear=${YEAR}`, { headers: COU_9002 })
const listAdmin = await call(`/review/tasks?academicYear=${YEAR}`, { headers: ADMIN })
const listAdminGrant = await call(`/review/tasks?academicYear=${YEAR}`, { headers: ADMIN_GRANT })
check('待审列表（学生）403', listAsStu.status === 403, `status=${listAsStu.status} code=${listAsStu.body.code}`)
check(
  '待审列表（本班）',
  listOwn.status === 200 && listOwn.body.data.total >= 2,
  `total=${listOwn.body.data?.total} names=${listOwn.body.data?.records?.map((r: any) => r.studentName).join('/')}`,
)
check('待审列表（跨班）为空', listOther.status === 200 && listOther.body.data.total === 0, `total=${listOther.body.data?.total}`)
check('待审列表（管理员无授权）403', listAdmin.status === 403, `status=${listAdmin.status}`)
check('待审列表（管理员临时授权）', listAdminGrant.status === 200, `total=${listAdminGrant.body.data?.total}`)

// 10. 跨班审核被拒
const crossReject = await call(
  `/review/basic/1001/reject`,
  json('POST', COU_9002, { academicYear: YEAR, reason: '这不是我班的学生，理由长度足够十一个字' }),
)
check('跨班审核 403', crossReject.status === 403, `status=${crossReject.status} code=${crossReject.body.code}`)

// 11. 退回理由过短 -> EC-42203
const shortReason = await call(
  `/review/basic/1001/reject`,
  json('POST', COU_9001, { academicYear: YEAR, reason: '太短' }),
)
check(
  '退回理由过短 422',
  shortReason.status === 422 && shortReason.body.code === 'EC-42203',
  `status=${shortReason.status} code=${shortReason.body.code}`,
)

// 12. 正常退回，理由回传学生端
const rejected = await call(
  `/review/basic/1001/reject`,
  json('POST', COU_9001, { academicYear: YEAR, reason: '紧急联系电话有误，请核对后重新提交。' }),
)
const afterReject = await call(`/sections/basic/1001?academicYear=${YEAR}`, { headers: STU_1001 })
check(
  '退回成功 + 理由回传',
  rejected.status === 200 &&
    afterReject.body.data.status === 'RETURNED' &&
    afterReject.body.data.reviewReason === '紧急联系电话有误，请核对后重新提交。',
  `status=${afterReject.body.data.status} reason=${afterReject.body.data.reviewReason}`,
)

// 13. 重新提交后理由不再下发
await call(`/sections/basic/1001/submit?academicYear=${YEAR}`, { method: 'POST', headers: STU_1001 })
const afterResubmit = await call(`/sections/basic/1001?academicYear=${YEAR}`, { headers: STU_1001 })
check(
  '重提后清空退回理由',
  afterResubmit.body.data.status === 'SUBMITTED' && afterResubmit.body.data.reviewReason === undefined,
  `status=${afterResubmit.body.data.status} reason=${afterResubmit.body.data.reviewReason}`,
)

// 14. 撤回一次后再次撤回 -> EC-40903
const withdraw1 = await call(`/sections/basic/1001/withdraw?academicYear=${YEAR}`, { method: 'POST', headers: STU_1001 })
const afterWithdraw = await call(`/sections/basic/1001?academicYear=${YEAR}`, { headers: STU_1001 })
await call(`/sections/basic/1001/submit?academicYear=${YEAR}`, { method: 'POST', headers: STU_1001 })
const withdraw2 = await call(`/sections/basic/1001/withdraw?academicYear=${YEAR}`, { method: 'POST', headers: STU_1001 })
check(
  '撤回一次成功、二次 409',
  withdraw1.status === 200 &&
    afterWithdraw.body.data.status === 'DRAFT' &&
    afterWithdraw.body.data.withdrawCount === 1 &&
    withdraw2.status === 409 &&
    withdraw2.body.code === 'EC-40903',
  `first=${withdraw1.status} count=${afterWithdraw.body.data.withdrawCount} second=${withdraw2.status}/${withdraw2.body.code}`,
)

// 15. 敏感字段明文：学生 403，辅导员可取
const revealStu = await call(`/sections/basic/1004/fields/basic.idCard/reveal?academicYear=${YEAR}`, {
  headers: STU_1004,
})
const revealCou = await call(`/sections/basic/1004/fields/basic.idCard/reveal?academicYear=${YEAR}`, {
  headers: COU_9002,
})
check('学生查看明文 403', revealStu.status === 403, `status=${revealStu.status}`)
check(
  '辅导员查看明文',
  revealCou.status === 200 && revealCou.body.data.value === '320102200207114560',
  `status=${revealCou.status} value=${revealCou.body.data?.value}`,
)

// 16. 审核通过后状态流转与重复审核
const approved = await call(`/review/basic/1002/approve`, json('POST', COU_9001, { academicYear: YEAR }))
const afterApprove = await call(`/sections/basic/1002?academicYear=${YEAR}`, { headers: STU_1001 })
const again = await call(`/review/basic/1002/approve`, json('POST', COU_9001, { academicYear: YEAR }))
check(
  '审核通过 + 重复审核 409',
  approved.status === 200 &&
    afterApprove.body.data.status === 'APPROVED' &&
    again.status === 409,
  `approve=${approved.status} now=${afterApprove.body.data.status} again=${again.status}`,
)

// 17. 毕业归档（S-1-11）：权限、整批拒绝（EX-12）、归档终态与重复提交
const arcAsStu = await call(`/archive/candidates?academicYear=${YEAR}`, { headers: STU_1001 })
const arcAdminNoGrant = await call(`/archive/candidates?academicYear=${YEAR}`, { headers: ADMIN })
const arcOwn = await call(`/archive/candidates?academicYear=${YEAR}`, { headers: COU_9001 })
const arcOther = await call(`/archive/candidates?academicYear=${YEAR}`, { headers: COU_9002 })
check('归档名单（学生）403', arcAsStu.status === 403, `status=${arcAsStu.status}`)
check('归档名单（管理员无授权）403', arcAdminNoGrant.status === 403, `status=${arcAdminNoGrant.status}`)
check(
  '归档名单（本班）',
  arcOwn.status === 200 && arcOwn.body.data.length === 3 && arcOwn.body.data.every((item: any) => !item.archivable),
  `rows=${arcOwn.body.data?.length} archivable=${arcOwn.body.data?.filter((i: any) => i.archivable).length}`,
)
check(
  '归档名单（六板块全通过可归档）',
  arcOther.status === 200 && arcOther.body.data.length >= 1 && arcOther.body.data[0].archivable === true,
  `rows=${arcOther.body.data?.length} first=${arcOther.body.data?.[0]?.studentName}/${arcOther.body.data?.[0]?.approvedCount}`,
)

const archiveRejected = await call(
  `/archive/batch`,
  json('POST', COU_9001, { academicYear: YEAR, studentIds: [1001] }),
)
check(
  '整批拒绝 + 未完成清单（EX-12）',
  archiveRejected.status === 200 &&
    archiveRejected.body.data.rejected === true &&
    archiveRejected.body.data.archivedProfiles === 0 &&
    archiveRejected.body.data.students[0].missingSections.length === 6,
  `rejected=${archiveRejected.body.data?.rejected} missing=${archiveRejected.body.data?.students?.[0]?.missingSections?.length}`,
)

const crossArchive = await call(
  `/archive/batch`,
  json('POST', COU_9001, { academicYear: YEAR, studentIds: [1004] }),
)
check('跨班归档 403', crossArchive.status === 403, `status=${crossArchive.status} code=${crossArchive.body.code}`)

const archived = await call(
  `/archive/batch`,
  json('POST', COU_9002, { academicYear: YEAR, studentIds: [1004] }),
)
check(
  '归档成功：六板块转 LOCKED',
  archived.status === 200 &&
    archived.body.data.archivedStudents === 1 &&
    archived.body.data.archivedProfiles === 6 &&
    archived.body.data.rejected === false,
  `students=${archived.body.data?.archivedStudents} profiles=${archived.body.data?.archivedProfiles}`,
)

const afterArchive = await call(`/archive/candidates?academicYear=${YEAR}`, { headers: COU_9002 })
check(
  '归档后标记为已归档',
  afterArchive.body.data[0].archived === true && afterArchive.body.data[0].lockedCount === 6,
  `archived=${afterArchive.body.data?.[0]?.archived} locked=${afterArchive.body.data?.[0]?.lockedCount}`,
)

const archiveAgain = await call(
  `/archive/batch`,
  json('POST', COU_9002, { academicYear: YEAR, studentIds: [1004] }),
)
check(
  '重复归档跳过',
  archiveAgain.status === 200 &&
    archiveAgain.body.data.skippedStudents === 1 &&
    archiveAgain.body.data.archivedStudents === 0,
  `skipped=${archiveAgain.body.data?.skippedStudents} archived=${archiveAgain.body.data?.archivedStudents}`,
)

const emptyArchive = await call(
  `/archive/batch`,
  json('POST', COU_9001, { academicYear: YEAR, studentIds: [] }),
)
check('归档空名单 400', emptyArchive.status === 400, `status=${emptyArchive.status}`)

console.log(results.join('\n'))
const failed = results.filter((line) => line.startsWith('FAIL')).length
console.log(`\n合计 ${results.length} 项，失败 ${failed} 项`)
server.close()
process.exit(failed === 0 ? 0 : 1)
