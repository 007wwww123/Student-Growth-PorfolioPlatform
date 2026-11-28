<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  applySensitiveExport,
  approveClassExport,
  createClassExport,
  fetchClassExportApprovals,
  rejectClassExport,
  type ClassExportApproval,
} from '@/api/classExport'
import { downloadExport } from '@/api/export'
import { isApiError, type ApiError } from '@/api/http'
import { getUser } from '@/auth/session'

/**
 * 班级汇总 Excel（F-13 / AC-044）。
 *
 * <p>COU 导出本班；手机号/身份证号属敏感列，必须先申请并由管理员审批，
 * 审批通过后 4 小时内可导出；管理员在右侧处理审批。</p>
 */
const user = getUser()
const isAdmin = computed(() => user?.role === 'ADMIN')
const classCode = ref('RJ2401')
const academicYear = ref('2025-2026-1')
const columns = ref<string[]>([
  'studentNo', 'name', 'className', 'gender', 'nation', 'completeness',
  'basic', 'academic', 'interest', 'life', 'career', 'mentor',
])
const approvals = ref<ClassExportApproval[]>([])
const approvalId = ref<number | undefined>(undefined)
const loading = ref(false)
const exporting = ref(false)
const error = ref<ApiError | null>(null)
const message = ref('')

const columnOptions = [
  { key: 'studentNo', label: '学号' },
  { key: 'name', label: '姓名' },
  { key: 'className', label: '班级' },
  { key: 'gender', label: '性别' },
  { key: 'nation', label: '民族' },
  { key: 'phone', label: '手机号（敏感）' },
  { key: 'idCard', label: '身份证号（敏感）' },
  { key: 'completeness', label: '完成度' },
  { key: 'basic', label: '基本信息' },
  { key: 'academic', label: '学业发展' },
  { key: 'interest', label: '兴趣特长' },
  { key: 'life', label: '生活心理' },
  { key: 'career', label: '职业规划' },
  { key: 'mentor', label: '导师寄语' },
]

const sensitiveSelected = computed(() => columns.value.includes('phone') || columns.value.includes('idCard'))
const activeApprovals = computed(() => approvals.value.filter((item) => item.active))

async function loadApprovals(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    approvals.value = await fetchClassExportApprovals(isAdmin.value ? 'PENDING' : '')
  } catch (e) {
    error.value = isApiError(e) ? e : null
  } finally {
    loading.value = false
  }
}

async function onApply(): Promise<void> {
  const reason = window.prompt('请填写敏感列导出理由（5-200 字）') ?? ''
  if (reason.trim().length < 5) {
    message.value = '理由至少 5 个字'
    return
  }
  error.value = null
  try {
    await applySensitiveExport(classCode.value.trim(), reason.trim())
    message.value = '敏感列导出申请已提交，等待管理员审批'
    await loadApprovals()
  } catch (e) {
    error.value = isApiError(e) ? e : null
  }
}

async function onExport(): Promise<void> {
  if (sensitiveSelected.value && !approvalId.value) {
    message.value = '含敏感列，请先选择已生效的审批单'
    return
  }
  exporting.value = true
  error.value = null
  try {
    const task = await createClassExport({
      classCode: classCode.value.trim(),
      academicYear: academicYear.value,
      columns: columns.value,
      approvalId: sensitiveSelected.value ? approvalId.value : undefined,
    })
    await downloadExport(task.id)
    message.value = 'Excel 已生成并开始下载（24 小时内有效）'
  } catch (e) {
    error.value = isApiError(e) ? e : null
  } finally {
    exporting.value = false
  }
}

async function decide(row: ClassExportApproval, action: 'approve' | 'reject'): Promise<void> {
  let remark = ''
  if (action === 'reject') {
    remark = window.prompt('请填写拒绝理由') ?? ''
    if (remark.trim().length < 2) {
      message.value = '拒绝必须填写理由'
      return
    }
  }
  error.value = null
  try {
    if (action === 'approve') await approveClassExport(row.id, remark)
    else await rejectClassExport(row.id, remark.trim())
    message.value = action === 'approve' ? '已通过，4 小时内有效' : '已拒绝'
    await loadApprovals()
  } catch (e) {
    error.value = isApiError(e) ? e : null
  }
}

function statusText(status: string): string {
  return { PENDING: '待审批', APPROVED: '已通过', REJECTED: '已拒绝' }[status] ?? status
}

onMounted(loadApprovals)
</script>

<template>
  <section class="admin">
    <header class="admin__head">
      <div>
        <h1>班级汇总 Excel</h1>
        <p class="muted">按列导出本班学生；手机号、身份证号需管理员审批后 4 小时内导出。</p>
      </div>
    </header>

    <p v-if="error" class="banner banner--error">{{ error.message }}（{{ error.code }}）</p>
    <p v-if="message" class="banner">{{ message }}</p>

    <section v-if="!isAdmin" class="card">
      <h3>导出设置</h3>
      <div class="form">
        <label>班级 <input v-model="classCode" placeholder="班级编码，如 RJ2401" /></label>
        <label>学年 <input v-model="academicYear" /></label>
      </div>
      <div class="columns">
        <label v-for="item in columnOptions" :key="item.key" class="check">
          <input v-model="columns" type="checkbox" :value="item.key" />
          {{ item.label }}
        </label>
      </div>
      <div v-if="sensitiveSelected" class="sensitive">
        <p class="muted">已选择敏感列，需选择一条生效中的审批单：</p>
        <select v-model.number="approvalId">
          <option :value="undefined">请选择审批单</option>
          <option v-for="item in activeApprovals" :key="item.id" :value="item.id">
            #{{ item.id }} · {{ item.classCode }} · 到期 {{ item.expireAt?.replace('T', ' ').slice(0, 19) }}
          </option>
        </select>
        <button class="btn btn--ghost" @click="onApply">申请敏感列导出</button>
      </div>
      <div class="actions">
        <button class="btn" :disabled="exporting" @click="onExport">
          {{ exporting ? '生成中…' : '导出 Excel' }}
        </button>
      </div>
    </section>

    <section v-if="isAdmin" class="card">
      <h3>敏感列导出审批（待处理）</h3>
      <table>
        <thead><tr><th>申请人</th><th>班级</th><th>理由</th><th>状态</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="row in approvals" :key="row.id">
            <td>{{ row.applicantName || row.applicantId }}</td>
            <td>{{ row.classCode }}</td>
            <td>{{ row.reason }}</td>
            <td>{{ statusText(row.status) }}</td>
            <td class="actions">
              <button class="btn btn--small" @click="decide(row, 'approve')">通过</button>
              <button class="btn btn--small btn--ghost" @click="decide(row, 'reject')">拒绝</button>
            </td>
          </tr>
          <tr v-if="!loading && approvals.length === 0"><td colspan="5" class="empty">暂无待审批申请</td></tr>
        </tbody>
      </table>
    </section>

    <section v-if="!isAdmin" class="card">
      <h3>我的敏感导出申请</h3>
      <table>
        <thead><tr><th>班级</th><th>理由</th><th>状态</th><th>审批意见</th><th>到期</th></tr></thead>
        <tbody>
          <tr v-for="row in approvals" :key="row.id">
            <td>{{ row.classCode }}</td>
            <td>{{ row.reason }}</td>
            <td>{{ statusText(row.status) }}{{ row.active ? '（生效中）' : '' }}</td>
            <td>{{ row.approverRemark || '—' }}</td>
            <td>{{ row.expireAt ? row.expireAt.replace('T', ' ').slice(0, 19) : '—' }}</td>
          </tr>
          <tr v-if="!loading && approvals.length === 0"><td colspan="5" class="empty">暂无申请</td></tr>
        </tbody>
      </table>
    </section>
  </section>
</template>

<style scoped>
.admin { padding: 24px; display: grid; gap: 16px; }
.admin__head h1 { margin: 0 0 4px; font-size: 22px; }
.muted { margin: 0; color: var(--color-text-muted); font-size: 13px; }
.card { background: var(--color-surface); border-radius: var(--radius); box-shadow: var(--shadow); padding: 18px; }
.card h3 { margin: 0 0 10px; font-size: 15px; }
.form { display: flex; flex-wrap: wrap; gap: 12px; margin-bottom: 12px; }
.form label { display: flex; align-items: center; gap: 6px; font-size: 13px; }
input, select { height: 32px; padding: 0 10px; border: 1px solid var(--color-border); border-radius: 6px; font-size: 13px; }
.columns { display: flex; flex-wrap: wrap; gap: 8px 14px; margin-bottom: 12px; }
.check { display: flex; align-items: center; gap: 4px; font-size: 13px; }
.sensitive { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; padding: 10px; background: var(--color-warning-bg); border-radius: 6px; margin-bottom: 12px; }
.actions { display: flex; gap: 8px; }
.btn { height: 32px; padding: 0 12px; border: none; border-radius: 6px; background: var(--color-primary); color: #fff; cursor: pointer; font-size: 13px; }
.btn:disabled { opacity: 0.5; cursor: not-allowed; }
.btn--small { height: 26px; padding: 0 8px; }
.btn--ghost { background: #fff; color: var(--color-text); border: 1px solid var(--color-border); }
table { width: 100%; border-collapse: collapse; font-size: 13px; }
th, td { text-align: left; padding: 8px 10px; border-bottom: 1px solid var(--color-border); }
th { color: var(--color-text-muted); font-weight: 600; }
.banner { margin: 0; padding: 10px 12px; border-radius: 6px; background: var(--color-surface); font-size: 13px; }
.banner--error { background: var(--color-danger-bg); color: var(--color-danger); }
.empty { text-align: center; color: var(--color-text-muted); padding: 18px; }
</style>
