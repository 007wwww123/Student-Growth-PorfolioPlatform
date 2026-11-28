<script setup lang="ts">
import { onMounted, ref } from 'vue'
import {
  approveUnfreeze,
  fetchUnfreezeRequests,
  rejectUnfreeze,
  type UnfreezeRequest,
} from '@/api/unfreeze'
import { isApiError, type ApiError } from '@/api/http'

/** 归档解冻审批（B-6）：COU 申请、ADMIN 审批。 */
const rows = ref<UnfreezeRequest[]>([])
const loading = ref(false)
const error = ref<ApiError | null>(null)
const message = ref('')

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    rows.value = await fetchUnfreezeRequests()
  } catch (e) {
    error.value = isApiError(e) ? e : null
  } finally {
    loading.value = false
  }
}

async function decide(row: UnfreezeRequest, action: 'approve' | 'reject'): Promise<void> {
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
    if (action === 'approve') await approveUnfreeze(row.id, remark)
    else await rejectUnfreeze(row.id, remark.trim())
    message.value = action === 'approve' ? '已通过，六板块回到已通过' : '已拒绝'
    await load()
  } catch (e) {
    error.value = isApiError(e) ? e : null
  }
}

function statusText(status: string): string {
  return { PENDING: '待审批', APPROVED: '已通过', REJECTED: '已拒绝' }[status] ?? status
}

onMounted(load)
</script>

<template>
  <section class="admin">
    <header class="admin__head">
      <div>
        <h1>归档解冻</h1>
        <p class="muted">辅导员申请、管理员审批；通过后六板块从已归档回到已通过，可继续更正。</p>
      </div>
    </header>

    <p v-if="error" class="banner banner--error">{{ error.message }}（{{ error.code }}）</p>
    <p v-if="message" class="banner">{{ message }}</p>

    <section class="card">
      <table>
        <thead><tr><th>申请人</th><th>学生</th><th>学年</th><th>原因</th><th>状态</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="row in rows" :key="row.id">
            <td>{{ row.applicantName || row.applicantId }}</td>
            <td>{{ row.studentName || row.studentId }}</td>
            <td>{{ row.academicYear }}</td>
            <td>{{ row.reason }}</td>
            <td>{{ statusText(row.status) }}</td>
            <td class="actions">
              <template v-if="row.status === 'PENDING'">
                <button class="btn btn--small" @click="decide(row, 'approve')">通过</button>
                <button class="btn btn--small btn--ghost" @click="decide(row, 'reject')">拒绝</button>
              </template>
              <span v-else class="muted">—</span>
            </td>
          </tr>
          <tr v-if="!loading && rows.length === 0"><td colspan="6" class="empty">暂无解冻申请</td></tr>
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
table { width: 100%; border-collapse: collapse; font-size: 13px; }
th, td { text-align: left; padding: 8px 10px; border-bottom: 1px solid var(--color-border); }
th { color: var(--color-text-muted); font-weight: 600; }
.btn { height: 32px; padding: 0 12px; border: none; border-radius: 6px; background: var(--color-primary); color: #fff; cursor: pointer; font-size: 13px; }
.btn--small { height: 26px; padding: 0 8px; }
.btn--ghost { background: #fff; color: var(--color-text); border: 1px solid var(--color-border); }
.actions { display: flex; gap: 6px; }
.banner { margin: 0; padding: 10px 12px; border-radius: 6px; background: var(--color-surface); font-size: 13px; }
.banner--error { background: var(--color-danger-bg); color: var(--color-danger); }
.empty { text-align: center; color: var(--color-text-muted); padding: 18px; }
</style>
