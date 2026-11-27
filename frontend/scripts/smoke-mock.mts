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

// 17. 批量建档（S-1-01）：仅辅导员、仅本班；重复执行不重复创建
const enrollAsStu = await call(`/profiles/classes?academicYear=${YEAR}`, { headers: STU_1001 })
const enrollOwn = await call(`/profiles/classes?academicYear=${YEAR}`, { headers: COU_9001 })
const enrollAdmin = await call(`/profiles/classes?academicYear=${YEAR}`, { headers: ADMIN_GRANT })
check('可建档班级（学生）403', enrollAsStu.status === 403, `status=${enrollAsStu.status}`)
check('可建档班级（管理员只读）403', enrollAdmin.status === 403, `status=${enrollAdmin.status}`)
check(
  '可建档班级（本班）',
  enrollOwn.status === 200 &&
    enrollOwn.body.data.length === 2 &&
    enrollOwn.body.data.every((item: any) => item.studentCount >= 1),
  `classes=${enrollOwn.body.data?.map((item: any) => `${item.classCode}:${item.studentCount}`).join('/')}`,
)

const enrollForeign = await call(
  `/profiles/batch-create`,
  json('POST', COU_9001, { academicYear: YEAR, classCodes: ['DZ2401'] }),
)
check(
  '跨班建档 403',
  enrollForeign.status === 403 && enrollForeign.body.code === 'EC-40302',
  `status=${enrollForeign.status} code=${enrollForeign.body.code}`,
)

// 前面的用例已为 1001 逐个访问过六个板块（mock 会惰性创建），1002 只有基本信息；
// 因此本班两人合计 12 个板块位置，本次应补齐 1002 缺的 5 块，跳过的 7 块保持不变。
const firstEnroll = await call(
  `/profiles/batch-create`,
  json('POST', COU_9001, { academicYear: YEAR, classCodes: ['RJ2401'] }),
)
check(
  '批量建档：补齐缺失板块',
  firstEnroll.status === 200 &&
    firstEnroll.body.data.totalStudents === 2 &&
    firstEnroll.body.data.createdStudents === 1 &&
    firstEnroll.body.data.createdProfiles + firstEnroll.body.data.existingProfiles === 12,
  `students=${firstEnroll.body.data?.totalStudents} created=${firstEnroll.body.data?.createdProfiles} existing=${firstEnroll.body.data?.existingProfiles}`,
)

const secondEnroll = await call(
  `/profiles/batch-create`,
  json('POST', COU_9001, { academicYear: YEAR, classCodes: ['RJ2401'] }),
)
check(
  '批量建档幂等：二次不重复创建',
  secondEnroll.status === 200 &&
    secondEnroll.body.data.createdProfiles === 0 &&
    secondEnroll.body.data.skippedStudents === 2,
  `created=${secondEnroll.body.data?.createdProfiles} skipped=${secondEnroll.body.data?.skippedStudents}`,
)

const noClass = await call(
  `/profiles/batch-create`,
  json('POST', COU_9001, { academicYear: YEAR, classCodes: [] }),
)
check('批量建档缺参数 400', noClass.status === 400, `status=${noClass.status} code=${noClass.body.code}`)
// 18. 毕业归档（S-1-11）：权限、整批拒绝（EX-12）、归档终态与重复提交
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

// 19. 完成标记（BR-S10）：六项固定、缺块「未建档」、权限与手动重算
const completion = await call(`/completion/students/1001?academicYear=${YEAR}`, { headers: STU_1001 })
const completionData = completion.body.data ?? {}
const sections: Array<Record<string, any>> = completionData.sections ?? []
check(
  '完成标记：六板块固定下发且口径自洽',
  completion.status === 200 &&
    sections.length === 6 &&
    sections[0].sectionCode === 'basic' &&
    sections.every((item) => typeof item.statusLabel === 'string' && item.statusLabel.length > 0) &&
    completionData.completed === (completionData.completedSectionCount === 6) &&
    completionData.pendingSections.length === 6 - completionData.completedSectionCount,
  `status=${completion.status} sections=${sections.length} completed=${completionData.completedSectionCount} pending=${completionData.pendingSections?.length}`,
)

// 改用未建过档的学年：该学年 1004 没有任何板块实例，六块都应显示「未建档」。
// （本学年的种子已由归档演示改成六块全通过，不能再拿它验「缺块」口径。）
const UNBUILT_YEAR = '2024-2025-1'
const partial = await call(`/completion/students/1004?academicYear=${UNBUILT_YEAR}`, {
  headers: COU_9002,
})
const partialSections: Array<Record<string, any>> = partial.body.data?.sections ?? []
const missing = partialSections.filter((item) => item.statusLabel === '未建档')
check(
  '完成标记：缺块显示未建档',
  partial.status === 200 &&
    missing.length === 6 &&
    partial.body.data?.createdSectionCount === 0 &&
    missing.every((item) => item.status === undefined && item.completed === false),
  `status=${partial.status} missing=${missing.length} created=${partial.body.data?.createdSectionCount}`,
)

const completionOther = await call(`/completion/students/1004?academicYear=${YEAR}`, { headers: STU_1001 })
check(
  '完成标记：学生看他人 403',
  completionOther.status === 403 && completionOther.body.code === 'EC-40302',
  `status=${completionOther.status} code=${completionOther.body.code}`,
)

const completionCou = await call(`/completion/students/1001?academicYear=${YEAR}`, { headers: COU_9001 })
const completionCrossClass = await call(`/completion/students/1001?academicYear=${YEAR}`, { headers: COU_9002 })
const completionNoGrant = await call(`/completion/students/1001?academicYear=${YEAR}`, { headers: ADMIN })
const completionGrant = await call(`/completion/students/1001?academicYear=${YEAR}`, { headers: ADMIN_GRANT })
check(
  '完成标记：本班辅导员可读、跨班与管理员无授权 403',
  completionCou.status === 200 &&
    completionCrossClass.status === 403 &&
    completionNoGrant.status === 403 &&
    completionGrant.status === 200,
  `cou=${completionCou.status} cross=${completionCrossClass.status} admin=${completionNoGrant.status} grant=${completionGrant.status}`,
)

const refreshByStudent = await call(`/completion/refresh?studentId=1001&academicYear=${YEAR}`, {
  method: 'POST',
  headers: STU_1001,
})
const refreshByCou = await call(`/completion/refresh?studentId=1001&academicYear=${YEAR}`, {
  method: 'POST',
  headers: COU_9001,
})
const refreshYearByCou = await call(`/completion/refresh?academicYear=${YEAR}`, {
  method: 'POST',
  headers: COU_9001,
})
const refreshYearByAdmin = await call(`/completion/refresh?academicYear=${YEAR}`, {
  method: 'POST',
  headers: ADMIN_GRANT,
})
check(
  '完成标记：手动重算权限',
  refreshByStudent.status === 403 &&
    refreshByCou.status === 200 &&
    refreshByCou.body.data.checkedStudents === 1 &&
    refreshYearByCou.status === 403 &&
    refreshYearByAdmin.status === 200 &&
    refreshYearByAdmin.body.data.checkedStudents === 4,
  `stu=${refreshByStudent.status} cou=${refreshByCou.status}/${refreshByCou.body.data?.checkedStudents} couYear=${refreshYearByCou.status} adminYear=${refreshYearByAdmin.status}/${refreshYearByAdmin.body.data?.checkedStudents}`,
)

console.log(results.join('\n'))
const failed = results.filter((line) => line.startsWith('FAIL')).length
console.log(`\n合计 ${results.length} 项，失败 ${failed} 项`)
server.close()
process.exit(failed === 0 ? 0 : 1)
