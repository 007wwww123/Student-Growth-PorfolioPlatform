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
const STU_1003 = await authHeaders('2024010201', 'Passw0rd!')
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
    detail.body.data.fields.idCard === '320102********1232',
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
  json('PUT', STU_1001, { academicYear: YEAR, fields: { classId: 'RJ2402' } }),
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

// 照片从空值首填，驳回保留空值，确认后才生效；清空待确认同样可驳回。
const photoUrl = `/sections/basic/1001/fields/basic.photo`
const photo = async () => (await call(`/sections/basic/1001?academicYear=${YEAR}`, { headers: STU_1001 })).body.data
const savePhoto = (value: unknown) => call('/sections/basic/1001',
  json('PUT', STU_1001, { academicYear: YEAR, fields: { photo: value } }))
const resolvePhoto = (action: string, headers = COU_9001) =>
  call(`${photoUrl}/${action}?academicYear=${YEAR}`, json('POST', headers))
await savePhoto([100])
const first = await photo()
check('首次填写只产生候选值', first.fields.photo == null &&
  first.fieldMeta.find((item: any) => item.key === 'photo')?.pendingValue?.[0] === 100, 'photo')
check('跨班辅导员不能确认', (await resolvePhoto('confirm', COU_9002)).status === 403, 'photo')
await resolvePhoto('reject')
check('首次驳回保留空值', (await photo()).fields.photo == null, 'photo')
await savePhoto([100])
await resolvePhoto('confirm')
await savePhoto(null)
await resolvePhoto('reject')
check('驳回清空保留原值', (await photo()).fields.photo[0] === 100, 'photo')
await savePhoto(null)
await resolvePhoto('confirm')
check('确认清空正式值', (await photo()).fields.photo == null, 'photo')

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
  revealCou.status === 200 && revealCou.body.data.value === '320102200201034565',
  `status=${revealCou.status} value=${revealCou.body.data?.value}`,
)

// 16. 审核通过后状态流转与重复审核
const approved = await call(`/review/basic/1002/approve`, json('POST', COU_9001, { academicYear: YEAR }))
const afterApprove = await call(`/sections/basic/1002?academicYear=${YEAR}`, { headers: COU_9001 })
const again = await call(`/review/basic/1002/approve`, json('POST', COU_9001, { academicYear: YEAR }))
check(
  '审核通过 + 重复审核 409',
  approved.status === 200 &&
    afterApprove.body.data.status === 'APPROVED' &&
    again.status === 409,
  `approve=${approved.status} now=${afterApprove.body.data.status} again=${again.status}`,
)

// 使用独立学年，验证 OpenAPI approveSection 的 pendingField / missingFormalValue 示例。
const reviewYear = '2024-2025-2'
const reviewQuery = '?academicYear=' + reviewYear
const fixture = '/sections/basic/1001'
const clearRequired = await call(fixture, json('PUT', STU_1001, {
  academicYear: reviewYear, fields: { emergencyPhone: null },
}))
const sent = await call(fixture + '/submit' + reviewQuery, json('POST', STU_1001))
check('审批夹具进入已提交状态', clearRequired.body.code === '0' && sent.body.code === '0', reviewYear)
async function checkBlockedApproval(name: string, httpStatus: number, code: string) {
  const result = await call('/review/basic/1001/approve', json('POST', COU_9001, { academicYear: reviewYear }))
  const detail = await call(fixture + reviewQuery, { headers: STU_1001 })
  check(name, result.status === httpStatus && result.body.code === code &&
    typeof result.body.traceId === 'string' && detail.body.data?.status === 'SUBMITTED',
    `status=${result.status} code=${result.body.code} state=${detail.body.data?.status}`)
}
await checkBlockedApproval('有候选值时禁止审核通过', 409, 'EC-40902')
const cleared = await call(fixture + '/fields/basic.emergencyPhone/confirm' + reviewQuery, json('POST', COU_9001))
check('辅导员确认清空必填字段', cleared.status === 200 && cleared.body.code === '0', reviewYear)
await checkBlockedApproval('正式必填值为空时禁止审核通过', 422, 'EC-42202')

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

// 20. 首页进度聚合（需求未编号）：范围收口、口径自洽、班级筛选
const overviewStu = await call(`/dashboard/overview?academicYear=${YEAR}`, { headers: STU_1001 })
const overviewNoGrant = await call(`/dashboard/overview?academicYear=${YEAR}`, { headers: ADMIN })
const overviewCou = await call(`/dashboard/overview?academicYear=${YEAR}`, { headers: COU_9001 })
const overviewData = overviewCou.body.data ?? {}
const sumOfClasses = (overviewData.classes ?? []).reduce(
  (acc: number, row: Record<string, any>) => acc + row.studentTotal,
  0,
)
check(
  '看板：学生与无授权管理员 403',
  overviewStu.status === 403 &&
    overviewStu.body.code === 'EC-40302' &&
    overviewNoGrant.status === 403,
  `stu=${overviewStu.status} admin=${overviewNoGrant.status}`,
)
check(
  '看板：辅导员只统计带班且口径自洽',
  overviewCou.status === 200 &&
    overviewData.scopeLabel === '我的班级' &&
    overviewData.studentTotal === 3 &&
    overviewData.studentTotal === sumOfClasses &&
    overviewData.sections.length === 6 &&
    overviewData.completionRate === 0,
  `status=${overviewCou.status} students=${overviewData.studentTotal} classSum=${sumOfClasses} rate=${overviewData.completionRate}`,
)

const overviewCross = await call(`/dashboard/overview?academicYear=${YEAR}&classCode=DZ2401`, {
  headers: COU_9001,
})
const overviewOwnClass = await call(`/dashboard/overview?academicYear=${YEAR}&classCode=RJ2401`, {
  headers: COU_9001,
})
const overviewAdmin = await call(`/dashboard/overview?academicYear=${YEAR}`, { headers: ADMIN_GRANT })
check(
  '看板：跨班筛选 403、本班筛选生效、管理员全校',
  overviewCross.status === 403 &&
    overviewOwnClass.status === 200 &&
    overviewOwnClass.body.data.scopeLabel === '软件2401' &&
    overviewAdmin.status === 200 &&
    overviewAdmin.body.data.scopeLabel === '全校' &&
    overviewAdmin.body.data.studentTotal === 4,
  `cross=${overviewCross.status} own=${overviewOwnClass.body.data?.scopeLabel} adminScope=${overviewAdmin.body.data?.scopeLabel} adminStudents=${overviewAdmin.body.data?.studentTotal}`,
)


// 21. 管理员字段权限配置：六板块覆盖、保存生效、越权拦截、管理员不改内容
const permAsStudent = await call('/admin/permissions', { headers: STU_1003 })
const permAsCou = await call('/admin/permissions', { headers: COU_9001 })
const permAsAdmin = await call('/admin/permissions', { headers: ADMIN })
const permSections = permAsAdmin.body.data?.sections ?? []
const permBasic = permSections.find((section: Record<string, any>) => section.sectionCode === 'BASIC')
const permName = permBasic?.fields?.find((field: Record<string, any>) => field.fieldCode === 'basic.name')
const permMajor = permBasic?.fields?.find((field: Record<string, any>) => field.fieldCode === 'basic.majorId')
const fieldCounts = permSections.map((section: Record<string, any>) => section.fields.length)
check(
  '权限配置：仅管理员可读矩阵',
  permAsStudent.status === 403 &&
    permAsStudent.body.code === 'EC-40303' &&
    permAsCou.status === 403 &&
    permAsAdmin.status === 200,
  `stu=${permAsStudent.status} cou=${permAsCou.status} admin=${permAsAdmin.status}`,
)
check(
  '权限配置：六板块字段齐全（16/5/4/12/5/6）',
  permSections.length === 6 &&
    fieldCounts.join(',') === '16,5,4,12,5,6' &&
    permName?.permissionMode === 'NONE' &&
    permMajor?.locked === true,
  `sections=${permSections.length} counts=${fieldCounts.join(',')} name=${permName?.permissionMode} majorLocked=${permMajor?.locked}`,
)

const saveNameStudent = await call('/admin/permissions/basic', json('PUT', ADMIN, {
  fields: [{ fieldCode: 'basic.name', editableRoles: ['STU'] }],
}))
const detailAfterStudent = await call(`/sections/basic/1003?academicYear=${YEAR}`, { headers: STU_1003 })
const nameMetaAfterStudent = (detailAfterStudent.body.data?.fieldMeta ?? []).find(
  (field: Record<string, any>) => field.fieldCode === 'basic.name',
)
const stuSaveName = await call(`/sections/basic/1003`, json('PUT', STU_1003, {
  academicYear: YEAR,
  fields: { name: '李明' },
}))
const couSaveNameWhenStudentOnly = await call(`/sections/basic/1003`, json('PUT', COU_9001, {
  academicYear: YEAR,
  fields: { name: '李明' },
}))
check(
  '权限配置：姓名改为仅学生后，学生可写、老师被拒',
  saveNameStudent.status === 200 &&
    saveNameStudent.body.data?.changed === 1 &&
    nameMetaAfterStudent?.editable === true &&
    stuSaveName.status === 200 &&
    couSaveNameWhenStudentOnly.status === 403 &&
    couSaveNameWhenStudentOnly.body.code === 'EC-40301',
  `changed=${saveNameStudent.body.data?.changed} metaEditable=${nameMetaAfterStudent?.editable} stu=${stuSaveName.status} cou=${couSaveNameWhenStudentOnly.status}/${couSaveNameWhenStudentOnly.body.code}`,
)

const saveNameTeacher = await call('/admin/permissions/basic', json('PUT', ADMIN, {
  fields: [{ fieldCode: 'basic.name', editableRoles: ['COU'] }],
}))
const stuSaveNameWhenTeacherOnly = await call(`/sections/basic/1003`, json('PUT', STU_1003, {
  academicYear: YEAR,
  fields: { name: '李明' },
}))
const couSaveNameWhenTeacherOnly = await call(`/sections/basic/1003`, json('PUT', COU_9001, {
  academicYear: YEAR,
  fields: { name: '李明' },
}))
const detailStudentAfterTeacher = await call(`/sections/basic/1003?academicYear=${YEAR}`, { headers: STU_1003 })
const nameMetaStudentAfterTeacher = (detailStudentAfterTeacher.body.data?.fieldMeta ?? []).find(
  (field: Record<string, any>) => field.fieldCode === 'basic.name',
)
check(
  '权限配置：姓名改为仅老师后，学生被拒、老师可写',
  saveNameTeacher.status === 200 &&
    stuSaveNameWhenTeacherOnly.status === 403 &&
    stuSaveNameWhenTeacherOnly.body.code === 'EC-40301' &&
    nameMetaStudentAfterTeacher?.editable === false &&
    couSaveNameWhenTeacherOnly.status === 200,
  `stu=${stuSaveNameWhenTeacherOnly.status}/${stuSaveNameWhenTeacherOnly.body.code} meta=${nameMetaStudentAfterTeacher?.editable} cou=${couSaveNameWhenTeacherOnly.status}`,
)

const saveAdminRole = await call('/admin/permissions/basic', json('PUT', ADMIN, {
  fields: [{ fieldCode: 'basic.name', editableRoles: ['ADMIN'] }],
}))
const saveLockedField = await call('/admin/permissions/basic', json('PUT', ADMIN, {
  fields: [{ fieldCode: 'basic.majorId', editableRoles: ['STU'] }],
}))
const adminContentSave = await call('/sections/basic/1003', json('PUT', ADMIN, {
  academicYear: YEAR,
  fields: { name: '管理员' },
}))
check(
  '权限配置：管理员角色/系统字段不可配置，管理员不能改内容',
  saveAdminRole.status === 400 &&
    saveLockedField.status === 400 &&
    adminContentSave.status === 403 &&
    adminContentSave.body.code === 'EC-40303',
  `adminRole=${saveAdminRole.status} locked=${saveLockedField.status} content=${adminContentSave.status}/${adminContentSave.body.code}`,
)

// 内部备注学生端不返回：可见性与可编辑性必须同口径，前端据此禁用「仅学生」选项
const permMentorInternal = (permSections.find((section: Record<string, any>) => section.sectionCode === 'MENTOR')
  ?.fields ?? []).find((field: Record<string, any>) => field.fieldCode === 'mentor.internalRemark')
const saveInvisibleRole = await call('/admin/permissions/mentor', json('PUT', ADMIN, {
  fields: [{ fieldCode: 'mentor.internalRemark', editableRoles: ['STU'] }],
}))
check(
  '权限配置：对角色不可见的字段不能配成可编辑',
  permMentorInternal?.studentVisible === false &&
    permMentorInternal?.counselorVisible === true &&
    permMentorInternal?.permissionMode === 'COU' &&
    saveInvisibleRole.status === 400 &&
    saveInvisibleRole.body.code === 'EC-40003',
  `stuVisible=${permMentorInternal?.studentVisible} couVisible=${permMentorInternal?.counselorVisible} save=${saveInvisibleRole.status}/${saveInvisibleRole.body.code}`,
)

const resetBasic = await call('/admin/permissions/basic/reset', { method: 'POST', headers: ADMIN })
const matrixAfterReset = await call('/admin/permissions', { headers: ADMIN })
const nameAfterReset = (matrixAfterReset.body.data?.sections ?? [])
  .find((section: Record<string, any>) => section.sectionCode === 'BASIC')
  ?.fields.find((field: Record<string, any>) => field.fieldCode === 'basic.name')
check(
  '权限配置：恢复默认回到初始化口径',
  resetBasic.status === 200 &&
    resetBasic.body.data?.changed >= 1 &&
    nameAfterReset?.editableRoles?.length === 0 &&
    nameAfterReset?.permissionMode === 'NONE',
  `changed=${resetBasic.body.data?.changed} roles=${JSON.stringify(nameAfterReset?.editableRoles)}`,
)

// 15. 生活心理：学生保存→提交→撤回、辅导员可见性与派生关注标记
const lifeDetail = await call(`/sections/life/1001?academicYear=${YEAR}`, { headers: STU_1001 })
const lifeKeys = Object.keys(lifeDetail.body.data?.fields ?? {})
check(
  '生活心理：学生详情含 12 个字段，且不含辅导员专属的重大事件备忘',
  lifeDetail.status === 200 &&
    lifeDetail.body.data?.sectionCode === 'LIFE' &&
    (lifeDetail.body.data?.fieldMeta ?? []).length === 11 &&
    !lifeKeys.includes('majorEvent') &&
    lifeKeys.includes('stressScore') &&
    lifeDetail.body.data?.editable === true,
  `meta=${lifeDetail.body.data?.fieldMeta?.length} keys=${lifeKeys.length}`,
)

const lifeSave = await call('/sections/life/1001', json('PUT', STU_1001, {
  academicYear: YEAR,
  fields: {
    orgRole: '班级学习委员',
    orgLevel: 'MID',
    sleepHabit: 'LATE',
    exerciseFreq: 'LOW',
    personality: '偏内向，熟悉后活跃',
    econSelf: 'TIGHT',
    stressScore: 4,
    stressSource: ['学业', '就业'],
    mood: 'LOW',
  },
}))
const lifeAfterSave = await call(`/sections/life/1001?academicYear=${YEAR}`, { headers: STU_1001 })
check(
  '生活心理：保存草稿后完成度 100%，关注标记按压力与情绪派生为需关注',
  lifeSave.status === 200 &&
    lifeAfterSave.body.data?.completeness === 100 &&
    Number(lifeAfterSave.body.data?.fields?.careFlag) === 1 &&
    lifeAfterSave.body.data?.fields?.orgLevel === 'MID',
  `complete=${lifeAfterSave.body.data?.completeness} careFlag=${lifeAfterSave.body.data?.fields?.careFlag}`,
)

const lifeBadScore = await call('/sections/life/1001', json('PUT', STU_1001, {
  academicYear: YEAR,
  fields: { stressScore: 6 },
}))
const lifeTooManySources = await call('/sections/life/1001', json('PUT', STU_1001, {
  academicYear: YEAR,
  fields: { stressSource: ['学业', '就业', '人际', '家庭'] },
}))
check(
  '生活心理：压力自评越界与压力来源超限都被拦下',
  lifeBadScore.status === 422 &&
    lifeBadScore.body.code === 'EC-42201' &&
    lifeTooManySources.status === 422 &&
    lifeTooManySources.body.code === 'EC-42201',
  `score=${lifeBadScore.body.code} source=${lifeTooManySources.body.code}`,
)

const lifeCouOnlyField = await call('/sections/life/1001', json('PUT', STU_1001, {
  academicYear: YEAR,
  fields: { aidSource: ['国家助学金'] },
}))
check(
  '生活心理：学生改辅导员维护的资助来源被拒',
  lifeCouOnlyField.status === 403 && lifeCouOnlyField.body.code === 'EC-40301',
  `status=${lifeCouOnlyField.status} code=${lifeCouOnlyField.body.code}`,
)

const lifeCou = await call(`/sections/life/1001?academicYear=${YEAR}`, { headers: COU_9001 })
const lifeCouKeys = Object.keys(lifeCou.body.data?.fields ?? {})
const lifeCouMeta = (lifeCou.body.data?.fieldMeta ?? []).map((field: Record<string, any>) => field.key)
check(
  '生活心理：辅导员看不到压力自评原始值，但能看到关注标记与压力来源',
  !lifeCouKeys.includes('stressScore') &&
    !lifeCouMeta.includes('stressScore') &&
    Number(lifeCou.body.data?.fields?.careFlag) === 1 &&
    lifeCouKeys.includes('stressSource') &&
    lifeCouMeta.includes('majorEvent'),
  `keys=${lifeCouKeys.join(',')}`,
)

const lifeSubmit = await call(`/sections/life/1001/submit?academicYear=${YEAR}`, { method: 'POST', headers: STU_1001 })
const lifeAfterSubmit = await call(`/sections/life/1001?academicYear=${YEAR}`, { headers: STU_1001 })
const lifeWithdraw = await call(`/sections/life/1001/withdraw?academicYear=${YEAR}`, {
  method: 'POST',
  headers: STU_1001,
})
const lifeAfterWithdraw = await call(`/sections/life/1001?academicYear=${YEAR}`, { headers: STU_1001 })
check(
  '生活心理：提交后锁定、撤回一次回到草稿',
  lifeSubmit.status === 200 &&
    lifeAfterSubmit.body.data?.status === 'SUBMITTED' &&
    lifeAfterSubmit.body.data?.editable === false &&
    lifeWithdraw.status === 200 &&
    lifeAfterWithdraw.body.data?.status === 'DRAFT' &&
    lifeAfterWithdraw.body.data?.withdrawCount === 1,
  `submit=${lifeAfterSubmit.body.data?.status} withdraw=${lifeAfterWithdraw.body.data?.status}`,
)

const lifeSubmitAgain = await call(`/sections/life/1001/submit?academicYear=${YEAR}`, {
  method: 'POST',
  headers: STU_1001,
})
const lifeWithdrawTwice = await call(`/sections/life/1001/withdraw?academicYear=${YEAR}`, {
  method: 'POST',
  headers: STU_1001,
})
check(
  '生活心理：再次提交后第二次撤回返回 EC-40903',
  lifeSubmitAgain.status === 200 &&
    lifeWithdrawTwice.status === 409 &&
    lifeWithdrawTwice.body.code === 'EC-40903',
  `code=${lifeWithdrawTwice.body.code}`,
)

// 16. 职业规划：学生保存→提交→撤回、必填拦截、辅导员只读
const careerDetail = await call(`/sections/career/1001?academicYear=${YEAR}`, { headers: STU_1001 })
const careerIntention = (careerDetail.body.data?.fieldMeta ?? [])
  .find((field: Record<string, any>) => field.key === 'intention')
check(
  '职业规划：学生详情 5 个字段，发展意向为必填且选项为英文码',
  careerDetail.status === 200 &&
    careerDetail.body.data?.sectionCode === 'CAREER' &&
    (careerDetail.body.data?.fieldMeta ?? []).length === 5 &&
    careerIntention?.required === true &&
    careerIntention?.editable === true &&
    (careerIntention?.options ?? []).map((option: Record<string, any>) => option.value).join(',') ===
      'EMPLOY,POSTGRAD,OVERSEAS,STARTUP,UNDECIDED',
  `meta=${careerDetail.body.data?.fieldMeta?.length} required=${careerIntention?.required}`,
)

const careerPartialSave = await call(`/sections/career/1001`, json('PUT', STU_1001, {
  academicYear: YEAR,
  fields: { industry: '工业软件' },
}))
const careerSubmitMissing = await call(`/sections/career/1001/submit?academicYear=${YEAR}`, {
  method: 'POST',
  headers: STU_1001,
})
check(
  '职业规划：只填非必填项时提交被拒（EC-42202）',
  careerPartialSave.status === 200 &&
    careerSubmitMissing.status === 422 &&
    careerSubmitMissing.body.code === 'EC-42202',
  `code=${careerSubmitMissing.body.code}`,
)

const careerSave = await call('/sections/career/1001', json('PUT', STU_1001, {
  academicYear: YEAR,
  fields: {
    intention: 'POSTGRAD',
    industry: '工业软件',
    position: '后端开发工程师',
    plan: '已完成两个 Spring Boot 项目，刷题 200 道',
    support: '希望获得实习内推与简历修改建议',
  },
}))
const careerAfterSave = await call(`/sections/career/1001?academicYear=${YEAR}`, { headers: STU_1001 })
const careerCou = await call(`/sections/career/1001?academicYear=${YEAR}`, { headers: COU_9001 })
check(
  '职业规划：保存后完成度 100%，意向存英文码，辅导员可读但不可写',
  careerSave.status === 200 &&
    careerAfterSave.body.data?.completeness === 100 &&
    careerAfterSave.body.data?.fields?.intention === 'POSTGRAD' &&
    careerCou.body.data?.fields?.intention === 'POSTGRAD' &&
    (careerCou.body.data?.fieldMeta ?? []).every((field: Record<string, any>) => field.editable === false),
  `complete=${careerAfterSave.body.data?.completeness} couEditable=${(careerCou.body.data?.fieldMeta ?? []).some((f: Record<string, any>) => f.editable)}`,
)

const careerSubmit = await call(`/sections/career/1001/submit?academicYear=${YEAR}`, {
  method: 'POST',
  headers: STU_1001,
})
const careerAfterSubmit = await call(`/sections/career/1001?academicYear=${YEAR}`, { headers: STU_1001 })
const careerWithdraw = await call(`/sections/career/1001/withdraw?academicYear=${YEAR}`, {
  method: 'POST',
  headers: STU_1001,
})
const careerAfterWithdraw = await call(`/sections/career/1001?academicYear=${YEAR}`, { headers: STU_1001 })
check(
  '职业规划：提交后锁定、撤回一次回到草稿',
  careerSubmit.status === 200 &&
    careerAfterSubmit.body.data?.status === 'SUBMITTED' &&
    careerAfterSubmit.body.data?.editable === false &&
    careerWithdraw.status === 200 &&
    careerAfterWithdraw.body.data?.status === 'DRAFT' &&
    careerAfterWithdraw.body.data?.withdrawCount === 1,
  `submit=${careerAfterSubmit.body.data?.status} withdraw=${careerAfterWithdraw.body.data?.status}`,
)

// ---- 板块六 导师寄语：子表时间线（需求 4.7 / AC-040） ----
const mentorStu = await call(`/sections/mentor/1001?academicYear=${YEAR}`, { headers: STU_1001 })
const ms = mentorStu.body.data ?? {}
check(
  '导师寄语：学生详情为统一信封 + 2 条倒序时间线，fields 恒为空',
  mentorStu.status === 200 &&
    ms.notes?.length === 2 &&
    ms.notes[0].talkDate === '2026-05-20' &&
    ms.notes[1].talkDate === '2026-04-18' &&
    Object.keys(ms.fields ?? {}).length === 0 &&
    ms.completeness === 100 &&
    ms.canAppend === false,
  `notes=${ms.notes?.length} comp=${ms.completeness} fields=${Object.keys(ms.fields ?? {}).length}`,
)
check(
  '导师寄语：学生端不下发内部备注/辅导员 ID，只显示「辅导员」',
  (ms.notes ?? []).every(
    (n: Record<string, any>) =>
      !('internalRemark' in n) && !('counselorId' in n) &&
      n.authorLabel === '辅导员' && n.canFeedback === true,
  ) &&
    !(ms.fieldMeta ?? []).some((m: Record<string, any>) => m.key === 'internalRemark'),
  `meta=${(ms.fieldMeta ?? []).map((m: Record<string, any>) => m.key).join(',')}`,
)

const mentorCou = await call(`/sections/mentor/1001?academicYear=${YEAR}`, { headers: COU_9001 })
const mc = mentorCou.body.data ?? {}
const topicValues = (mc.fieldMeta ?? [])
  .find((m: Record<string, any>) => m.key === 'topic')
  ?.options?.map((o: Record<string, any>) => o.value)
  .join(',')
check(
  '导师寄语：辅导员可见内部备注且可追加，主题枚举为英文码',
  mc.canAppend === true &&
    mc.notes?.[0]?.internalRemark === '已推荐至合作企业实习' &&
    (mc.fieldMeta ?? []).length === 6 &&
    topicValues === 'ACADEMIC,CAREER,PSYCH,DAILY',
  `canAppend=${mc.canAppend} meta=${(mc.fieldMeta ?? []).length} topics=${topicValues}`,
)

const NOTE3 = {
  academicYear: YEAR,
  talkDate: '2026-06-01',
  topic: 'DAILY',
  advice: '近期作息较规律，继续保持运动习惯，有问题随时找辅导员沟通',
  starLevel: 0,
  internalRemark: '家庭经济困难，需留意勤工助学名额',
}
const append3 = await call('/sections/mentor/1001/notes', json('POST', COU_9001, NOTE3))
const afterAppend = await call(`/sections/mentor/1001?academicYear=${YEAR}`, { headers: STU_1001 })
check(
  '导师寄语：辅导员追加第 3 条，学生端见 3 条且仍不见内部备注（AC-040）',
  append3.status === 200 &&
    append3.body.code === '0' &&
    append3.body.data?.internalRemark === '家庭经济困难，需留意勤工助学名额' &&
    afterAppend.body.data?.notes?.length === 3 &&
    afterAppend.body.data.notes.every((n: Record<string, any>) => !('internalRemark' in n)),
  `code=${append3.body.code} notes=${afterAppend.body.data?.notes?.length}`,
)

const badShort = await call('/sections/mentor/1001/notes', json('POST', COU_9001, { ...NOTE3, advice: '太短' }))
const badTopic = await call('/sections/mentor/1001/notes', json('POST', COU_9001, { ...NOTE3, topic: '学业规划' }))
const badFuture = await call('/sections/mentor/1001/notes', json('POST', COU_9001, { ...NOTE3, talkDate: '2099-01-01' }))
const badMissing = await call('/sections/mentor/1001/notes', json('POST', COU_9001, { ...NOTE3, advice: '' }))
check(
  '导师寄语：建议过短/中文主题/未来日期 → EC-42201，缺必填 → EC-42202',
  badShort.body.code === 'EC-42201' && badTopic.body.code === 'EC-42201' &&
    badFuture.body.code === 'EC-42201' && badMissing.body.code === 'EC-42202',
  `${badShort.body.code},${badTopic.body.code},${badFuture.body.code},${badMissing.body.code}`,
)

const stuAppend = await call('/sections/mentor/1001/notes', json('POST', STU_1001, NOTE3))
const otherCouAppend = await call('/sections/mentor/1001/notes', json('POST', COU_9002, NOTE3))
check(
  '导师寄语：学生与非本班辅导员追加被拒',
  stuAppend.body.code === 'EC-40303' && otherCouAppend.body.code === 'EC-40302',
  `${stuAppend.body.code},${otherCouAppend.body.code}`,
)

const FIRST_NOTE = ms.notes?.[1]?.id
const feedback = `/sections/mentor/1001/notes/${FIRST_NOTE}/feedback`
const fb1 = await call(feedback, json('POST', STU_1001, { academicYear: YEAR, feedback: '已报名校赛，每周刷题 20 道' }))
const fb2 = await call(feedback, json('POST', STU_1001, { academicYear: YEAR, feedback: '再改一次' }))
const fbLong = await call(
  `/sections/mentor/1001/notes/${mc.notes?.[0]?.id}/feedback`,
  json('POST', STU_1001, { academicYear: YEAR, feedback: 'x'.repeat(501) }),
)
check(
  '导师寄语：反馈一次即锁定，二次 EC-40902，超长 EC-42201',
  fb1.status === 200 && fb1.body.data?.canFeedback === false &&
    fb2.body.code === 'EC-40902' && fbLong.body.code === 'EC-42201',
  `${fb1.body.code},${fb2.body.code},${fbLong.body.code}`,
)

const putEmpty = await call('/sections/mentor/1001', json('PUT', STU_1001, { academicYear: YEAR, fields: {} }))
const putNonEmpty = await call('/sections/mentor/1001', json('PUT', STU_1001, {
  academicYear: YEAR, fields: { topic: 'ACADEMIC' },
}))
check(
  '导师寄语：PUT 空 fields 幂等，非空 EC-40003 指路追加接口',
  putEmpty.status === 200 && putEmpty.body.code === '0' &&
    putNonEmpty.body.code === 'EC-40003',
  `${putEmpty.body.code},${putNonEmpty.body.code}`,
)

const mSubmit = await call(`/sections/mentor/1001/submit?academicYear=${YEAR}`, { method: 'POST', headers: STU_1001 })
const mWithdraw = await call(`/sections/mentor/1001/withdraw?academicYear=${YEAR}`, { method: 'POST', headers: STU_1001 })
check(
  '导师寄语：提交/撤回走公共状态机（不被主表必填卡住）',
  mSubmit.body.code === '0' && mWithdraw.body.code === '0',
  `${mSubmit.body.code},${mWithdraw.body.code}`,
)

console.log(results.join('\n'))
const failed = results.filter((line) => line.startsWith('FAIL')).length
console.log(`\n合计 ${results.length} 项，失败 ${failed} 项`)
server.close()
process.exit(failed === 0 ? 0 : 1)
