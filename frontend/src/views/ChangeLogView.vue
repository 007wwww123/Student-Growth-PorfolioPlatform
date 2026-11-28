<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  approveAppeal,
  createAppeal,
  fetchAppeals,
  fetchChanges,
  rejectAppeal,
  type Appeal,
  type ChangeLog,
} from '@/api/changes'
import { isApiError, type ApiError } from '@/api/http'
import { getUser } from '@/auth/session'

/**
 * 变更记录与申诉（F-18 / AC-070）。
 *
 * <p>学生看本人、辅导员看本班、管理员看全部。学生对某条变更发起申诉，
 * 辅导员在「申诉工单」中处理；变更前后值本身已在写入时脱敏。</p>
 */
const tab = ref<'changes' | 'appeals'>('changes')
const rows = ref<ChangeLog[]>([])
const appeals = ref<Appeal[]>([])
const total = ref(0)
const appealTotal = ref(0)
const loading = ref(false)
const error = ref<ApiError | null>(null)
const message = ref('')
const user = getUser()
const isStudent = computed(() => user?.role === 'STU')
const isStaff = computed(() => user?.role === 'COU' || user?.role === 'ADMIN')

const query = reactive({
  studentId: undefined as number | undefined,
  sectionCode: '',
  bizType: '',
  from: '',
  to: '',
  pageNum: 1,
  pageSize: 20,
})
const appealStatus = ref('')

async function loadChanges(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    const page = await fetchChanges({
      studentId: query.studentId,
      sectionCode: query.sectionCode || undefined,
      bizType: query.bizType || undefined,
      from: query.from || undefined,
      to: query.to || undefined,
      pageNum: query.pageNum,
      pageSize: query.pageSize,
    })
    rows.value = page.records
    total.value = page.total
  } catch (e) {
    error.value = isApiError(e) ? e : null
  } finally {
    loading.value = false
  }
}

async function loadAppeals(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    const page = await fetchAppeals(appealStatus.value, 1, 50)
    appeals.value = page.records
    appealTotal.value = page.total
  } catch (e) {
    error.value = isApiError(e) ? e : null
  } finally {
    loading.value = false
  }
}

function switchTab(next: 'changes' | 'appeals'): void {
  tab.value = next
  if (next === 'changes') void loadChanges()
  else void loadAppeals()
}

async function onAppeal(row: ChangeLog): Promise<void> {
  const reason = window.prompt('请填写申诉理由（5-500 字）') ?? ''
  if (reason.trim().length < 5) {
    message.value = '申诉理由至少 5 个字'
    return
  }
  error.value = null
  try {
    await createAppeal(row.id, reason.trim())
    message.value = '申诉已提交，等待辅导员处理'
    await loadChanges()
  } catch (e) {
    error.value = isApiError(e) ? e : null
  }
}

async function decide(row: Appeal, action: 'approve' | 'reject'): Promise<void> {
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
    if (action === 'approve') await approveAppeal(row.id, remark)
    else await rejectAppeal(row.id, remark.trim())
    message.value = action === 'approve' ? '申诉已通过' : '申诉已拒绝'
    await loadAppeals()
  } catch (e) {
    error.value = isApiError(e) ? e : null
  }
}

function canAppeal(row: ChangeLog): boolean {
  return isStudent.value && (!row.appealStatus || row.appealStatus === 'REJECTED')
}

function formatTime(value?: string): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '—'
}

function statusText(status?: string): string {
  return { PENDING: '待处理', APPROVED: '已通过', REJECTED: '已拒绝' }[status ?? ''] ?? '—'
}

onMounted(loadChanges)
</script>

<template>
  <section class="admin">
    <header class="admin__head">
      <div>
        <h1>变更记录</h1>
        <p class="muted">谁在什么时间把哪个字段从什么改成了什么；敏感字段只显示脱敏值。</p>
      </div>
      <span class="tag">{{ tab === 'changes' ? total : appealTotal }} 条</span>
    </header>

    <p v-if="error" class="banner banner--error">{{ error.message }}（{{ error.code }}）</p>
    <p v-if="message" class="banner">{{ message }}</p>

    <nav class="tabs">
      <button class="tab" :class="{ 'tab--active': tab === 'changes' }" @click="switchTab('changes')">变更记录</button>
      <button class="tab" :class="{ 'tab--active': tab === 'appeals' }" @click="switchTab('appeals')">
        {{ isStudent ? '我的申诉' : '申诉工单' }}
      </button>
    </nav>

    <section v-if="tab === 'changes'" class="card">
      <div class="filters">
        <input v-if="isStaff" v-model.number="query.studentId" type="number" placeholder="学生 ID（本班/全部）" />
        <select v-model="query.sectionCode">
          <option value="">全部板块</option>
          <option value="BASIC">基本信息</option>
          <option value="ACADEMIC">学业发展</option>
          <option value="INTEREST">兴趣特长</option>
          <option value="LIFE">生活心理</option>
          <option value="CAREER">职业规划</option>
          <option value="MENTOR">导师寄语</option>
        </select>
        <select v-model="query.bizType">
          <option value="">全部类型</option>
          <option value="FIELD_EDIT">字段修改</option>
          <option value="STATUS_CHANGE">状态流转</option>
          <option value="REVIEW">审核</option>
          <option value="ARCHIVE">归档</option>
        </select>
        <input v-model="query.from" type="date" />
        <input v-model="query.to" type="date" />
        <button class="btn" @click="query.pageNum = 1; loadChanges()">查询</button>
      </div>
      <table>
        <thead>
          <tr><th>时间</th><th v-if="isStaff">学生</th><th>板块/字段</th><th>变更</th><th>操作人</th><th>申诉</th></tr>
        </thead>
        <tbody>
          <tr v-for="row in rows" :key="row.id">
            <td>{{ formatTime(row.createdAt) }}</td>
            <td v-if="isStaff">{{ row.studentName || row.studentId }}</td>
            <td>{{ row.sectionName || row.sectionCode }} / {{ row.fieldName || row.bizType }}</td>
            <td class="change">
              <span>{{ row.oldValue || '—' }}</span>
              <span class="arrow">→</span>
              <span>{{ row.newValue || row.toStatus || '—' }}</span>
            </td>
            <td>{{ row.operatorName || row.operatorRole || '—' }}</td>
            <td>
              <span v-if="row.appealStatus" class="muted">{{ statusText(row.appealStatus) }}</span>
              <button v-if="canAppeal(row)" class="btn btn--small btn--ghost" @click="onAppeal(row)">申诉</button>
            </td>
          </tr>
          <tr v-if="!loading && rows.length === 0"><td :colspan="isStaff ? 6 : 5" class="empty">暂无变更记录</td></tr>
        </tbody>
      </table>
      <div class="pager">
        <button class="btn btn--small btn--ghost" :disabled="query.pageNum <= 1" @click="query.pageNum--; loadChanges()">上一页</button>
        <span class="muted">第 {{ query.pageNum }} 页 / 共 {{ Math.max(1, Math.ceil(total / query.pageSize)) }} 页</span>
        <button class="btn btn--small btn--ghost" :disabled="query.pageNum * query.pageSize >= total" @click="query.pageNum++; loadChanges()">下一页</button>
      </div>
    </section>

    <section v-else class="card">
      <div v-if="isStaff" class="filters">
        <select v-model="appealStatus" @change="loadAppeals">
          <option value="">全部状态</option>
          <option value="PENDING">待处理</option>
          <option value="APPROVED">已通过</option>
          <option value="REJECTED">已拒绝</option>
        </select>
      </div>
      <table>
        <thead>
          <tr><th>时间</th><th>学生</th><th>变更</th><th>理由</th><th>状态</th><th>处理意见</th><th v-if="isStaff">操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="row in appeals" :key="row.id">
            <td>{{ formatTime(row.createdAt) }}</td>
            <td>{{ row.studentName || row.studentId }}</td>
            <td>{{ row.sectionName }} / {{ row.fieldName }}</td>
            <td>{{ row.reason }}</td>
            <td>{{ statusText(row.status) }}</td>
            <td>{{ row.approverRemark || '—' }}</td>
            <td v-if="isStaff" class="actions">
              <template v-if="row.status === 'PENDING'">
                <button class="btn btn--small" @click="decide(row, 'approve')">通过</button>
                <button class="btn btn--small btn--ghost" @click="decide(row, 'reject')">拒绝</button>
              </template>
              <span v-else class="muted">—</span>
            </td>
          </tr>
          <tr v-if="!loading && appeals.length === 0"><td :colspan="isStaff ? 7 : 6" class="empty">暂无申诉</td></tr>
        </tbody>
      </table>
    </section>
  </section>
</template>

<style scoped>
.admin { padding: 24px; display: grid; gap: 16px; }
.admin__head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.admin__head h1 { margin: 0 0 4px; font-size: 22px; }
.muted { margin: 0; color: var(--color-text-muted); font-size: 13px; }
.tabs { display: flex; gap: 8px; }
.tab { height: 32px; padding: 0 14px; border: 1px solid var(--color-border); border-radius: 6px; background: #fff; cursor: pointer; font-size: 13px; }
.tab--active { background: var(--color-primary); border-color: var(--color-primary); color: #fff; }
.card { background: var(--color-surface); border-radius: var(--radius); box-shadow: var(--shadow); padding: 18px; }
.filters { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 12px; }
input, select { height: 32px; padding: 0 10px; border: 1px solid var(--color-border); border-radius: 6px; font-size: 13px; }
table { width: 100%; border-collapse: collapse; font-size: 13px; }
th, td { text-align: left; padding: 8px 10px; border-bottom: 1px solid var(--color-border); vertical-align: top; }
th { color: var(--color-text-muted); font-weight: 600; }
.change { display: flex; gap: 6px; align-items: center; max-width: 320px; }
.arrow { color: var(--color-text-muted); }
.btn { height: 32px; padding: 0 12px; border: none; border-radius: 6px; background: var(--color-primary); color: #fff; cursor: pointer; font-size: 13px; }
.btn:disabled { opacity: 0.5; cursor: not-allowed; }
.btn--small { height: 26px; padding: 0 8px; }
.btn--ghost { background: #fff; color: var(--color-text); border: 1px solid var(--color-border); }
.actions { display: flex; gap: 6px; }
.banner { margin: 0; padding: 10px 12px; border-radius: 6px; background: var(--color-surface); font-size: 13px; }
.banner--error { background: var(--color-danger-bg); color: var(--color-danger); }
.pager { display: flex; align-items: center; gap: 12px; margin-top: 12px; }
.empty { text-align: center; color: var(--color-text-muted); padding: 18px; }
</style>
