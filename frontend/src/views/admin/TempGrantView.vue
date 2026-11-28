<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  applyTempGrant,
  approveTempGrant,
  fetchActiveTempGrant,
  fetchTempGrants,
  rejectTempGrant,
  revokeTempGrant,
  type TempGrant,
} from '@/api/tempGrants'
import { isApiError, type ApiError } from '@/api/http'
import { getUser } from '@/auth/session'

/**
 * 临时授权（F-17 / AC-053）。
 *
 * <p>管理员申请 1–4 小时只读授权，由另一名管理员审批；审批通过后刷新页面
 * 或重新登录即可看到业务菜单。审批人不能是申请人本人。</p>
 */
const rows = ref<TempGrant[]>([])
const active = ref<TempGrant[]>([])
const loading = ref(false)
const error = ref<ApiError | null>(null)
const message = ref('')
const submitting = ref(false)
const hours = ref(2)
const reason = ref('')

const currentUser = getUser()
const currentUserId = computed(() => currentUser?.userId ?? 0)

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    const [list, activeList] = await Promise.all([fetchTempGrants(), fetchActiveTempGrant()])
    rows.value = list
    active.value = activeList
  } catch (e) {
    error.value = isApiError(e) ? e : null
  } finally {
    loading.value = false
  }
}

async function onApply(): Promise<void> {
  if (reason.value.trim().length < 5) {
    message.value = '申请理由至少 5 个字'
    return
  }
  submitting.value = true
  error.value = null
  try {
    await applyTempGrant(hours.value, reason.value.trim())
    message.value = '申请已提交，等待另一名管理员审批'
    reason.value = ''
    await load()
  } catch (e) {
    error.value = isApiError(e) ? e : null
  } finally {
    submitting.value = false
  }
}

async function onApprove(row: TempGrant): Promise<void> {
  await decide(row, 'approve')
}

async function onReject(row: TempGrant): Promise<void> {
  const remark = window.prompt('请填写拒绝理由（至少 2 个字）') ?? ''
  if (remark.trim().length < 2) {
    message.value = '拒绝必须填写理由'
    return
  }
  await decide(row, 'reject', remark.trim())
}

async function decide(row: TempGrant, action: 'approve' | 'reject', remark = ''): Promise<void> {
  error.value = null
  try {
    if (action === 'approve') {
      await approveTempGrant(row.id, remark)
      message.value = '已通过；刷新页面后业务菜单生效'
    } else {
      await rejectTempGrant(row.id, remark)
      message.value = '已拒绝'
    }
    await load()
  } catch (e) {
    error.value = isApiError(e) ? e : null
  }
}

async function onRevoke(row: TempGrant): Promise<void> {
  if (!window.confirm('撤销后该管理员立即失去临时只读权限，确认撤销？')) return
  error.value = null
  try {
    await revokeTempGrant(row.id)
    message.value = '已撤销'
    await load()
  } catch (e) {
    error.value = isApiError(e) ? e : null
  }
}

function canApprove(row: TempGrant): boolean {
  return row.status === 'PENDING' && row.applicantId !== currentUserId.value
}

function canRevoke(row: TempGrant): boolean {
  return (row.status === 'PENDING' || row.status === 'APPROVED')
    && (row.applicantId === currentUserId.value || row.approverId === currentUserId.value)
}

function statusText(status: string): string {
  return {
    PENDING: '待审批',
    APPROVED: '已通过',
    REJECTED: '已拒绝',
    EXPIRED: '已到期',
    REVOKED: '已撤销',
  }[status] ?? status
}

function formatTime(value?: string): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '—'
}

onMounted(load)
</script>

<template>
  <section class="admin">
    <header class="admin__head">
      <div>
        <h1>临时授权</h1>
        <p class="muted">管理员默认无业务数据权限；审批通过后获得 1–4 小时只读权限，到期自动失效。</p>
      </div>
      <span class="tag">{{ active.length > 0 ? '生效中' : '无生效授权' }}</span>
    </header>

    <p v-if="error" class="banner banner--error">{{ error.message }}（{{ error.code }}）</p>
    <p v-if="message" class="banner">{{ message }}</p>

    <section v-if="active.length > 0" class="card card--active">
      <strong>当前授权生效中</strong>
      <span v-for="item in active" :key="item.id" class="muted">
        {{ item.hours }} 小时，到期 {{ formatTime(item.expireAt) }}
      </span>
    </section>

    <section class="card">
      <h3>申请临时授权</h3>
      <div class="form">
        <label>
          时长
          <select v-model.number="hours">
            <option v-for="n in [1, 2, 3, 4]" :key="n" :value="n">{{ n }} 小时</option>
          </select>
        </label>
        <input v-model="reason" maxlength="200" placeholder="申请理由（如：数据核对需要只读权限）" />
        <button class="btn" :disabled="submitting" @click="onApply">提交申请</button>
      </div>
      <p class="muted">审批人不能是申请人本人；同一时间只能有一条待审批或生效中的授权。</p>
    </section>

    <section class="card">
      <table>
        <thead>
          <tr><th>申请人</th><th>时长</th><th>理由</th><th>状态</th><th>审批人</th><th>到期</th><th>操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="row in rows" :key="row.id">
            <td>{{ row.applicantName || row.applicantId }}</td>
            <td>{{ row.hours }} 小时</td>
            <td>{{ row.reason }}</td>
            <td>{{ statusText(row.status) }}</td>
            <td>{{ row.approverName || '—' }}</td>
            <td>{{ formatTime(row.expireAt) }}</td>
            <td class="actions">
              <button v-if="canApprove(row)" class="btn btn--small" @click="onApprove(row)">通过</button>
              <button v-if="canApprove(row)" class="btn btn--small btn--ghost" @click="onReject(row)">拒绝</button>
              <button v-if="canRevoke(row)" class="btn btn--small btn--ghost" @click="onRevoke(row)">撤销</button>
              <span v-if="!canApprove(row) && !canRevoke(row)" class="muted">—</span>
            </td>
          </tr>
          <tr v-if="!loading && rows.length === 0"><td colspan="7" class="empty">暂无申请</td></tr>
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
.card { background: var(--color-surface); border-radius: var(--radius); box-shadow: var(--shadow); padding: 18px; }
.card h3 { margin: 0 0 10px; font-size: 15px; }
.card--active { display: flex; align-items: center; gap: 12px; border-left: 4px solid var(--color-success); }
.form { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
input, select { height: 32px; padding: 0 10px; border: 1px solid var(--color-border); border-radius: 6px; font-size: 13px; }
input { flex: 1; min-width: 260px; }
table { width: 100%; border-collapse: collapse; font-size: 13px; }
th, td { text-align: left; padding: 8px 10px; border-bottom: 1px solid var(--color-border); }
th { color: var(--color-text-muted); font-weight: 600; }
.btn { height: 32px; padding: 0 12px; border: none; border-radius: 6px; background: var(--color-primary); color: #fff; cursor: pointer; font-size: 13px; }
.btn:disabled { opacity: 0.5; cursor: not-allowed; }
.btn--small { height: 26px; padding: 0 8px; }
.btn--ghost { background: #fff; color: var(--color-text); border: 1px solid var(--color-border); }
.actions { display: flex; gap: 6px; }
.banner { margin: 0; padding: 10px 12px; border-radius: 6px; background: var(--color-surface); font-size: 13px; }
.banner--error { background: var(--color-danger-bg); color: var(--color-danger); }
.empty { text-align: center; color: var(--color-text-muted); padding: 18px; }
</style>
