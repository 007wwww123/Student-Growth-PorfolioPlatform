<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { fetchAuditLogs, fetchLoginLogs, type AuditLog, type LoginLog } from '@/api/audit'
import { isApiError, type ApiError } from '@/api/http'

/**
 * 审计日志 / 登录日志（F-14 / AC-050–052）。
 *
 * <p>管理员可查全部；辅导员与学生只能查本人操作的审计记录（服务端强制）。
 * 登录日志仅管理员可见。</p>
 */
const tab = ref<'audit' | 'login'>('audit')
const auditRows = ref<AuditLog[]>([])
const loginRows = ref<LoginLog[]>([])
const auditTotal = ref(0)
const loginTotal = ref(0)
const loading = ref(false)
const error = ref<ApiError | null>(null)

const auditQuery = reactive({
  bizType: '',
  action: '',
  studentId: undefined as number | undefined,
  from: '',
  to: '',
  pageNum: 1,
  pageSize: 20,
})
const loginQuery = reactive({
  username: '',
  success: undefined as number | undefined,
  from: '',
  to: '',
  pageNum: 1,
  pageSize: 20,
})

async function loadAudit(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    const page = await fetchAuditLogs({
      bizType: auditQuery.bizType || undefined,
      action: auditQuery.action || undefined,
      studentId: auditQuery.studentId,
      from: auditQuery.from || undefined,
      to: auditQuery.to || undefined,
      pageNum: auditQuery.pageNum,
      pageSize: auditQuery.pageSize,
    })
    auditRows.value = page.records
    auditTotal.value = page.total
  } catch (e) {
    error.value = isApiError(e) ? e : null
  } finally {
    loading.value = false
  }
}

async function loadLogin(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    const page = await fetchLoginLogs({
      username: loginQuery.username || undefined,
      success: loginQuery.success,
      from: loginQuery.from || undefined,
      to: loginQuery.to || undefined,
      pageNum: loginQuery.pageNum,
      pageSize: loginQuery.pageSize,
    })
    loginRows.value = page.records
    loginTotal.value = page.total
  } catch (e) {
    error.value = isApiError(e) ? e : null
  } finally {
    loading.value = false
  }
}

function switchTab(next: 'audit' | 'login'): void {
  tab.value = next
  if (next === 'audit') void loadAudit()
  else void loadLogin()
}

function formatTime(value?: string): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '—'
}

onMounted(loadAudit)
</script>

<template>
  <section class="admin">
    <header class="admin__head">
      <div>
        <h1>审计日志</h1>
        <p class="muted">记录谁在什么时间对哪些对象执行了什么动作；越权尝试也会留痕。</p>
      </div>
      <span class="tag">{{ tab === 'audit' ? auditTotal : loginTotal }} 条</span>
    </header>

    <p v-if="error" class="banner banner--error">{{ error.message }}（{{ error.code }}）</p>

    <nav class="tabs">
      <button class="tab" :class="{ 'tab--active': tab === 'audit' }" @click="switchTab('audit')">审计日志</button>
      <button class="tab" :class="{ 'tab--active': tab === 'login' }" @click="switchTab('login')">登录日志</button>
    </nav>

    <section v-if="tab === 'audit'" class="card">
      <div class="filters">
        <input v-model="auditQuery.bizType" placeholder="业务类型，如 REVIEW" />
        <input v-model="auditQuery.action" placeholder="动作，如 APPROVE" />
        <input v-model.number="auditQuery.studentId" type="number" placeholder="学生 ID" />
        <input v-model="auditQuery.from" type="date" />
        <input v-model="auditQuery.to" type="date" />
        <button class="btn" @click="auditQuery.pageNum = 1; loadAudit()">查询</button>
      </div>
      <table>
        <thead>
          <tr><th>时间</th><th>类型</th><th>动作</th><th>对象</th><th>学生</th><th>操作人</th><th>说明</th></tr>
        </thead>
        <tbody>
          <tr v-for="row in auditRows" :key="row.id">
            <td>{{ formatTime(row.createdAt) }}</td>
            <td>{{ row.bizType }}</td>
            <td>{{ row.action }}</td>
            <td>{{ row.targetType || '—' }} {{ row.targetId ?? '' }}</td>
            <td>{{ row.studentId ?? '—' }}</td>
            <td>{{ row.operatorId ?? '—' }} / {{ row.operatorRole || '—' }}</td>
            <td>{{ row.detail || '—' }}</td>
          </tr>
          <tr v-if="!loading && auditRows.length === 0"><td colspan="7" class="empty">暂无审计日志</td></tr>
        </tbody>
      </table>
      <div class="pager">
        <button class="btn btn--small btn--ghost" :disabled="auditQuery.pageNum <= 1" @click="auditQuery.pageNum--; loadAudit()">上一页</button>
        <span class="muted">第 {{ auditQuery.pageNum }} 页 / 共 {{ Math.max(1, Math.ceil(auditTotal / auditQuery.pageSize)) }} 页</span>
        <button class="btn btn--small btn--ghost" :disabled="auditQuery.pageNum * auditQuery.pageSize >= auditTotal" @click="auditQuery.pageNum++; loadAudit()">下一页</button>
      </div>
    </section>

    <section v-else class="card">
      <div class="filters">
        <input v-model="loginQuery.username" placeholder="登录名" />
        <select v-model="loginQuery.success">
          <option :value="undefined">全部结果</option>
          <option :value="1">成功</option>
          <option :value="0">失败</option>
        </select>
        <input v-model="loginQuery.from" type="date" />
        <input v-model="loginQuery.to" type="date" />
        <button class="btn" @click="loginQuery.pageNum = 1; loadLogin()">查询</button>
      </div>
      <table>
        <thead><tr><th>时间</th><th>账号</th><th>结果</th><th>原因</th><th>IP</th><th>UA</th></tr></thead>
        <tbody>
          <tr v-for="row in loginRows" :key="row.id">
            <td>{{ formatTime(row.createdAt) }}</td>
            <td>{{ row.username || row.userId || '—' }}</td>
            <td>{{ row.success === 1 ? '成功' : '失败' }}</td>
            <td>{{ row.reason || '—' }}</td>
            <td>{{ row.ip || '—' }}</td>
            <td>{{ row.userAgent || '—' }}</td>
          </tr>
          <tr v-if="!loading && loginRows.length === 0"><td colspan="6" class="empty">暂无登录日志</td></tr>
        </tbody>
      </table>
      <div class="pager">
        <button class="btn btn--small btn--ghost" :disabled="loginQuery.pageNum <= 1" @click="loginQuery.pageNum--; loadLogin()">上一页</button>
        <span class="muted">第 {{ loginQuery.pageNum }} 页 / 共 {{ Math.max(1, Math.ceil(loginTotal / loginQuery.pageSize)) }} 页</span>
        <button class="btn btn--small btn--ghost" :disabled="loginQuery.pageNum * loginQuery.pageSize >= loginTotal" @click="loginQuery.pageNum++; loadLogin()">下一页</button>
      </div>
    </section>
  </section>
</template>

<style scoped>
.admin {
  padding: 24px;
  display: grid;
  gap: 16px;
}
.admin__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}
.admin__head h1 {
  margin: 0 0 4px;
  font-size: 22px;
}
.muted {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 13px;
}
.tabs {
  display: flex;
  gap: 8px;
}
.tab {
  height: 32px;
  padding: 0 14px;
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
.card {
  background: var(--color-surface);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  padding: 18px;
}
.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}
input,
select {
  height: 32px;
  padding: 0 10px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  font-size: 13px;
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
.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
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
.banner {
  margin: 0;
  padding: 10px 12px;
  border-radius: 6px;
  font-size: 13px;
}
.banner--error {
  background: var(--color-danger-bg);
  color: var(--color-danger);
}
.pager {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
}
.empty {
  text-align: center;
  color: var(--color-text-muted);
  padding: 18px;
}
</style>
