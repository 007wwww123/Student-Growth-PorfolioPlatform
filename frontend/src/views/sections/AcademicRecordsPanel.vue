<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import {
  createCertificate,
  createEvent,
  fetchAcademicRecords,
  importCertificates,
  importRanks,
  verifyCertificate,
  verifyEvent,
  type AcademicRecords,
} from '@/api/records'
import { downloadExcel, type ImportResult } from '@/api/import'
import { isApiError, type ApiError } from '@/api/http'
import { getUser } from '@/auth/session'
import { DEMO_STUDENT_ID } from '@/sectionMeta'

/**
 * 学业成果：成绩轨迹（只读）、证书、竞赛。
 *
 * <p>学生本人可新增证书/竞赛并等待认定；辅导员只能认定（通过/退回），
 * 认定通过的条目才计入成果数量。成绩轨迹由教务导入，页面只读。</p>
 */
const user = getUser()
const studentId = user?.studentId ?? DEMO_STUDENT_ID
const isStudent = user?.role === 'STU'
const isCounselor = user?.role === 'COU'

const data = ref<AcademicRecords | null>(null)
const tab = ref<'ranks' | 'certificates' | 'events'>('ranks')
const loading = ref(false)
const error = ref<ApiError | null>(null)
const message = ref('')
const showCertForm = ref(false)
const showEventForm = ref(false)
const verify = ref<{ type: 'cert' | 'event'; id: number; remark: string } | null>(null)
const rankFile = ref<File | null>(null)
const certFile = ref<File | null>(null)
const importing = ref(false)
const importResult = ref<ImportResult | null>(null)

const certForm = reactive({ certType: '外语', certName: '', score: '', obtainDate: '' })
const eventForm = reactive({
  eventType: 'COMPETITION',
  title: '',
  roleName: '',
  level: 'SCHOOL',
  startDate: '',
  endDate: '',
  description: '',
})

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    data.value = await fetchAcademicRecords(studentId)
  } catch (e) {
    error.value = isApiError(e) ? e : null
  } finally {
    loading.value = false
  }
}

function flash(text: string): void {
  message.value = text
  window.setTimeout(() => {
    if (message.value === text) message.value = ''
  }, 3500)
}

async function submitCert(): Promise<void> {
  if (!certForm.certName.trim() || !certForm.obtainDate) return
  try {
    await createCertificate(studentId, {
      certType: certForm.certType,
      certName: certForm.certName.trim(),
      score: certForm.score.trim() || undefined,
      obtainDate: certForm.obtainDate,
    })
    certForm.certName = ''
    certForm.score = ''
    showCertForm.value = false
    flash('证书已提交，等待辅导员认定')
    await load()
    tab.value = 'certificates'
  } catch (e) {
    error.value = isApiError(e) ? e : null
  }
}

async function submitEvent(): Promise<void> {
  if (!eventForm.title.trim() || !eventForm.startDate) return
  try {
    await createEvent(studentId, {
      eventType: eventForm.eventType,
      title: eventForm.title.trim(),
      roleName: eventForm.roleName.trim() || undefined,
      level: eventForm.level,
      startDate: eventForm.startDate,
      endDate: eventForm.endDate || undefined,
      description: eventForm.description.trim() || undefined,
    })
    eventForm.title = ''
    eventForm.roleName = ''
    eventForm.description = ''
    showEventForm.value = false
    flash('竞赛/科研已提交，等待辅导员认定')
    await load()
    tab.value = 'events'
  } catch (e) {
    error.value = isApiError(e) ? e : null
  }
}

async function pass(type: 'cert' | 'event', id: number): Promise<void> {
  try {
    if (type === 'cert') await verifyCertificate(studentId, id, 'PASSED')
    else await verifyEvent(studentId, id, 'PASSED')
    flash('已认定通过')
    await load()
  } catch (e) {
    error.value = isApiError(e) ? e : null
  }
}

function openReject(type: 'cert' | 'event', id: number): void {
  verify.value = { type, id, remark: '' }
}

async function submitReject(): Promise<void> {
  if (!verify.value || !verify.value.remark.trim()) return
  try {
    if (verify.value.type === 'cert') {
      await verifyCertificate(studentId, verify.value.id, 'REJECTED', verify.value.remark.trim())
    } else {
      await verifyEvent(studentId, verify.value.id, 'REJECTED', verify.value.remark.trim())
    }
    verify.value = null
    flash('已退回')
    await load()
  } catch (e) {
    error.value = isApiError(e) ? e : null
  }
}

function onRankFile(event: Event): void {
  const input = event.target as HTMLInputElement
  rankFile.value = input.files && input.files.length > 0 ? input.files[0] : null
}

function onCertFile(event: Event): void {
  const input = event.target as HTMLInputElement
  certFile.value = input.files && input.files.length > 0 ? input.files[0] : null
}

async function doImportRank(): Promise<void> {
  if (!rankFile.value) return
  importing.value = true
  error.value = null
  try {
    importResult.value = await importRanks(rankFile.value)
    flash(`成绩导入：成功 ${importResult.value.successRows} 行，失败 ${importResult.value.failedRows} 行`)
    await load()
    tab.value = 'ranks'
  } catch (e) {
    error.value = isApiError(e) ? e : null
  } finally {
    importing.value = false
  }
}

async function doImportCert(): Promise<void> {
  if (!certFile.value) return
  importing.value = true
  error.value = null
  try {
    importResult.value = await importCertificates(certFile.value)
    flash(`证书导入：成功 ${importResult.value.successRows} 行，失败 ${importResult.value.failedRows} 行`)
    await load()
    tab.value = 'certificates'
  } catch (e) {
    error.value = isApiError(e) ? e : null
  } finally {
    importing.value = false
  }
}

function statusText(status: string): string {
  return status === 'PASSED' ? '已通过' : status === 'REJECTED' ? '已退回' : '待认定'
}

function eventTypeText(type: string): string {
  return type === 'COMPETITION' ? '竞赛' : type === 'PROJECT' ? '项目' : '科研'
}

function levelText(level: string): string {
  const map: Record<string, string> = {
    SCHOOL: '校级',
    PROVINCE: '省级',
    NATIONAL: '国家级',
    INTERNATIONAL: '国际级',
  }
  return map[level] ?? level
}

onMounted(load)
</script>

<template>
  <section class="records">
    <header class="records__head">
      <div>
        <h2>学业成果</h2>
        <p class="muted">成绩轨迹由教务导入，只读；证书与竞赛需辅导员认定，通过后才计入成果。</p>
      </div>
      <span v-if="data" class="tag">证书通过 {{ data.passedCertificates }} · 竞赛通过 {{ data.passedEvents }}</span>
    </header>

    <p v-if="error" class="banner banner--error">{{ error.message }}（{{ error.code }}）</p>
    <p v-if="message" class="banner banner--ok">{{ message }}</p>

    <div v-if="isCounselor" class="import">
      <h3>导入成绩 / 证书</h3>
      <div class="import__row">
        <button class="btn btn--small btn--ghost" @click="downloadExcel('/academic/records/import/ranks/template', 'academic-rank-template.xlsx')">
          下载成绩模板
        </button>
        <input type="file" accept=".xlsx" @change="onRankFile" />
        <button class="btn btn--small" :disabled="!rankFile || importing" @click="doImportRank">导入成绩</button>
      </div>
      <div class="import__row">
        <button class="btn btn--small btn--ghost" @click="downloadExcel('/academic/records/import/certificates/template', 'academic-cert-template.xlsx')">
          下载证书模板
        </button>
        <input type="file" accept=".xlsx" @change="onCertFile" />
        <button class="btn btn--small" :disabled="!certFile || importing" @click="doImportCert">导入证书</button>
      </div>
      <p v-if="importResult" class="muted">
        导入任务 #{{ importResult.taskId }}：成功 {{ importResult.successRows }} 行，失败
        {{ importResult.failedRows }} 行。
        <button
          class="btn btn--small btn--ghost"
          @click="downloadExcel(`/academic/records/import/tasks/${importResult.taskId}/receipt`, `academic-import-receipt-${importResult.taskId}.xlsx`)"
        >
          下载回执
        </button>
      </p>
    </div>

    <nav class="tabs">
      <button class="tab" :class="{ 'tab--active': tab === 'ranks' }" @click="tab = 'ranks'">成绩轨迹</button>
      <button class="tab" :class="{ 'tab--active': tab === 'certificates' }" @click="tab = 'certificates'">证书技能</button>
      <button class="tab" :class="{ 'tab--active': tab === 'events' }" @click="tab = 'events'">竞赛科研</button>
    </nav>

    <p v-if="loading" class="muted">加载中…</p>

    <div v-if="tab === 'ranks' && data">
      <table>
        <thead><tr><th>学期</th><th>班级排名</th><th>专业排名</th><th>GPA</th><th>来源</th></tr></thead>
        <tbody>
          <tr v-for="row in data.ranks" :key="row.id">
            <td>{{ row.academicYear }}</td>
            <td>{{ row.classRank ?? '—' }}</td>
            <td>{{ row.majorRank ?? '—' }}</td>
            <td>{{ row.gpa ?? '—' }}</td>
            <td>{{ row.source === 'IMPORT' ? '教务导入' : '手工' }}</td>
          </tr>
          <tr v-if="data.ranks.length === 0"><td colspan="5" class="empty">暂无成绩记录</td></tr>
        </tbody>
      </table>
    </div>

    <div v-if="tab === 'certificates' && data">
      <button v-if="isStudent" class="btn btn--small" @click="showCertForm = !showCertForm">新增证书</button>
      <div v-if="showCertForm" class="form">
        <select v-model="certForm.certType">
          <option>外语</option><option>计算机</option><option>职业资格</option><option>技能</option><option>其他</option>
        </select>
        <input v-model="certForm.certName" placeholder="证书名称，如 大学英语四级（CET-4）" />
        <input v-model="certForm.score" placeholder="成绩/等级，可空" />
        <input v-model="certForm.obtainDate" type="date" />
        <button class="btn btn--small" @click="submitCert">提交</button>
      </div>
      <table>
        <thead><tr><th>类型</th><th>名称</th><th>成绩</th><th>取得日期</th><th>认定</th><th>意见</th><th v-if="isCounselor">操作</th></tr></thead>
        <tbody>
          <tr v-for="row in data.certificates" :key="row.id">
            <td>{{ row.certType }}</td>
            <td>{{ row.certName }}</td>
            <td>{{ row.score || '—' }}</td>
            <td>{{ row.obtainDate }}</td>
            <td><span class="state" :class="'state--' + row.verifyStatus">{{ statusText(row.verifyStatus) }}</span></td>
            <td>{{ row.verifyRemark || '—' }}</td>
            <td v-if="isCounselor" class="ops">
              <button v-if="row.verifyStatus !== 'PASSED'" class="btn btn--small" @click="pass('cert', row.id)">通过</button>
              <button v-if="row.verifyStatus !== 'REJECTED'" class="btn btn--small btn--ghost" @click="openReject('cert', row.id)">退回</button>
            </td>
          </tr>
          <tr v-if="data.certificates.length === 0"><td :colspan="isCounselor ? 7 : 6" class="empty">暂无证书</td></tr>
        </tbody>
      </table>
    </div>

    <div v-if="tab === 'events' && data">
      <button v-if="isStudent" class="btn btn--small" @click="showEventForm = !showEventForm">新增竞赛/科研</button>
      <div v-if="showEventForm" class="form">
        <select v-model="eventForm.eventType"><option value="COMPETITION">竞赛</option><option value="PROJECT">项目</option><option value="RESEARCH">科研</option></select>
        <input v-model="eventForm.title" placeholder="名称，4-255 字" />
        <input v-model="eventForm.roleName" placeholder="本人角色，可空" />
        <select v-model="eventForm.level"><option value="SCHOOL">校级</option><option value="PROVINCE">省级</option><option value="NATIONAL">国家级</option><option value="INTERNATIONAL">国际级</option></select>
        <input v-model="eventForm.startDate" type="date" />
        <input v-model="eventForm.endDate" type="date" />
        <input v-model="eventForm.description" placeholder="描述，可空" />
        <button class="btn btn--small" @click="submitEvent">提交</button>
      </div>
      <table>
        <thead><tr><th>类型</th><th>名称</th><th>级别</th><th>时间</th><th>认定</th><th v-if="isCounselor">操作</th></tr></thead>
        <tbody>
          <tr v-for="row in data.events" :key="row.id">
            <td>{{ eventTypeText(row.eventType) }}</td>
            <td>{{ row.title }}</td>
            <td>{{ levelText(row.level) }}</td>
            <td>{{ row.startDate }} ~ {{ row.endDate || '—' }}</td>
            <td><span class="state" :class="'state--' + row.verifyStatus">{{ statusText(row.verifyStatus) }}</span></td>
            <td v-if="isCounselor" class="ops">
              <button v-if="row.verifyStatus !== 'PASSED'" class="btn btn--small" @click="pass('event', row.id)">通过</button>
              <button v-if="row.verifyStatus !== 'REJECTED'" class="btn btn--small btn--ghost" @click="openReject('event', row.id)">退回</button>
            </td>
          </tr>
          <tr v-if="data.events.length === 0"><td :colspan="isCounselor ? 6 : 5" class="empty">暂无竞赛/科研</td></tr>
        </tbody>
      </table>
    </div>

    <div v-if="verify" class="modal">
      <div class="modal__card">
        <h3>退回理由</h3>
        <textarea v-model="verify.remark" rows="3" placeholder="必填，学生可见"></textarea>
        <div class="ops">
          <button class="btn" @click="submitReject">确认退回</button>
          <button class="btn btn--ghost" @click="verify = null">取消</button>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.records {
  margin: 0 24px 20px;
  background: var(--color-surface);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  padding: 18px;
}
.records__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}
.records__head h2 {
  margin: 0 0 4px;
  font-size: 16px;
}
.muted {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 13px;
}
.tabs {
  display: flex;
  gap: 8px;
  margin: 14px 0;
}
.import {
  margin-top: 12px;
  padding: 12px;
  border: 1px dashed var(--color-border);
  border-radius: 6px;
  display: grid;
  gap: 8px;
}
.import h3 {
  margin: 0;
  font-size: 14px;
}
.import__row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.tab {
  height: 30px;
  padding: 0 12px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: #fff;
  cursor: pointer;
  font-size: 13px;
}
.tab--active {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: #fff;
}
table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
th,
td {
  text-align: left;
  padding: 8px 10px;
  border-bottom: 1px solid var(--color-border);
}
th {
  color: var(--color-text-muted);
  font-weight: 600;
}
.state {
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 12px;
  background: #f3f4f6;
}
.state--PASSED {
  background: var(--color-success-bg);
  color: var(--color-success);
}
.state--REJECTED {
  background: var(--color-danger-bg);
  color: var(--color-danger);
}
.form {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 10px 0;
}
input,
select,
textarea {
  padding: 7px 10px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  font-size: 13px;
}
.btn {
  height: 32px;
  padding: 0 12px;
  border: none;
  border-radius: 6px;
  background: var(--color-primary);
  color: #fff;
  cursor: pointer;
  font-size: 13px;
}
.btn--small {
  height: 28px;
  padding: 0 10px;
}
.btn--ghost {
  background: #fff;
  color: var(--color-text);
  border: 1px solid var(--color-border);
}
.ops {
  display: flex;
  gap: 6px;
}
.banner {
  margin: 10px 0;
  padding: 8px 10px;
  border-radius: 6px;
  font-size: 13px;
}
.banner--error {
  background: var(--color-danger-bg);
  color: var(--color-danger);
}
.banner--ok {
  background: var(--color-success-bg);
  color: var(--color-success);
}
.empty {
  text-align: center;
  color: var(--color-text-muted);
  padding: 16px;
}
.modal {
  position: fixed;
  inset: 0;
  background: rgb(15 23 42 / 40%);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 20;
}
.modal__card {
  background: #fff;
  border-radius: var(--radius);
  padding: 18px;
  width: 360px;
  display: grid;
  gap: 10px;
}
.modal__card h3 {
  margin: 0;
  font-size: 15px;
}
</style>
