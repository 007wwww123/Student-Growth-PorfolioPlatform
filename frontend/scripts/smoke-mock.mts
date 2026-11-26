// 临时验证脚本：用 msw/node 跑同一套 handlers，校验响应结构与错误码
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
const JSON_HEADERS = { 'content-type': 'application/json' }

async function call(path: string, init?: RequestInit) {
  const response = await fetch(BASE + path, init)
  const body = (await response.json()) as Record<string, any>
  return { status: response.status, body }
}

const results: string[] = []
function check(name: string, pass: boolean, detail: string) {
  results.push(`${pass ? 'PASS' : 'FAIL'}  ${name}  ${detail}`)
}

// 1. 查详情
const detail = await call('/sections/basic/1001?academicYear=2025-2026-1')
check(
  'GET basic 详情',
  detail.status === 200 &&
    detail.body.code === '0' &&
    detail.body.data.sectionCode === 'BASIC' &&
    detail.body.data.sectionName === '基本信息' &&
    detail.body.data.status === 'DRAFT' &&
    typeof detail.body.traceId === 'string' &&
    /\+08:00$/.test(detail.body.timestamp),
  `${detail.status} code=${detail.body.code} name=${detail.body.data?.sectionName} status=${detail.body.data?.status} ts=${detail.body.timestamp}`,
)

// 2. 六个板块都能取到
for (const code of ['basic', 'academic', 'interest', 'life', 'career', 'mentor']) {
  const r = await call(`/sections/${code}/1001?academicYear=2025-2026-1`)
  check(`GET ${code}`, r.status === 200 && r.body.code === '0' && !!r.body.data, `status=${r.status} fields=${Object.keys(r.body.data?.fields ?? {}).length}`)
}

// 3. 字段校验失败 -> EC-42201 + details
const invalid = await call('/sections/basic/1001', {
  method: 'PUT',
  headers: JSON_HEADERS,
  body: JSON.stringify({ academicYear: '2025-2026-1', name: '', studentNo: '', email: 'bad' }),
})
check(
  'PUT 非法入参',
  invalid.status === 422 && invalid.body.code === 'EC-42201' && Array.isArray(invalid.body.details) && invalid.body.details.length === 3,
  `${invalid.status} code=${invalid.body.code} details=${JSON.stringify(invalid.body.details)}`,
)

// 4. 正常保存
const saved = await call('/sections/basic/1001', {
  method: 'PUT',
  headers: JSON_HEADERS,
  body: JSON.stringify({ academicYear: '2025-2026-1', name: '张三', studentNo: '2023010101', email: 'a@b.com' }),
})
check('PUT 正常保存', saved.status === 200 && saved.body.code === '0' && saved.body.data === undefined, `${saved.status} code=${saved.body.code}`)

const afterSave = await call('/sections/basic/1001?academicYear=2025-2026-1')
check('保存后字段已更新', afterSave.body.data.fields.name === '张三', `name=${afterSave.body.data.fields.name}`)

// 5. 提交缺参 -> EC-40002
const noYear = await call('/sections/basic/1001/submit', { method: 'POST' })
check('POST 提交缺 academicYear', noYear.status === 400 && noYear.body.code === 'EC-40002', `${noYear.status} code=${noYear.body.code} msg=${noYear.body.message}`)

// 6. 提交成功 -> SUBMITTED
const submitted = await call('/sections/basic/1001/submit?academicYear=2025-2026-1', { method: 'POST' })
const afterSubmit = await call('/sections/basic/1001?academicYear=2025-2026-1')
check(
  'POST 提交成功',
  submitted.status === 200 && submitted.body.code === '0' && afterSubmit.body.data.status === 'SUBMITTED',
  `${submitted.status} status=${afterSubmit.body.data.status}`,
)

// 7. 重复提交 -> EC-40902
check('提交后只读且保留已保存字段', afterSubmit.body.data.editable === false && afterSubmit.body.data.fields.name === '张三', JSON.stringify(afterSubmit.body.data.status))
const mentorSubmit = await call('/sections/mentor/1001/submit?academicYear=2025-2026-1', { method: 'POST' })
check('学生无法提交导师寄语', mentorSubmit.status === 403, String(mentorSubmit.status))
const conflict = await call('/sections/basic/1001/submit?academicYear=2025-2026-1', { method: 'POST' })
check('重复提交状态冲突', conflict.status === 409 && conflict.body.code === 'EC-40902', `${conflict.status} code=${conflict.body.code}`)

// 8. 未知板块 -> EC-40003
const unknown = await call('/sections/unknown/1001')
check('未知板块编码', unknown.status === 400 && unknown.body.code === 'EC-40003', `${unknown.status} code=${unknown.body.code}`)

console.log(results.join('\n'))
const failed = results.filter((line) => line.startsWith('FAIL')).length
console.log(`\n合计 ${results.length} 项，失败 ${failed} 项`)
server.close()
process.exit(failed === 0 ? 0 : 1)
