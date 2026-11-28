<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import {
  assignAccountRole,
  batchOpenAccounts,
  createAccount,
  fetchAccounts,
  resetAccountPassword,
  updateAccountStatus,
  type AccountUser,
  type BatchOpenResult,
  type CreateAccountResult,
} from '@/api/account'
import { fetchOrgOptions, type OrgClassRow } from '@/api/org'
import { isApiError, type ApiError } from '@/api/http'
import type { UserRole } from '@/api/types'

/**
 * 账号管理（F-03 / AC-006）。
 *
 * <p>列表按角色/状态/关键字筛选；支持单个开通、停用/启用、重置密码、
 * 角色分配，以及按班级为已有学生批量开通（初始密码身份证后六位）。</p>
 */
const rows = ref<AccountUser[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref<ApiError | null>(null)
const message = ref('')
const classes = ref<OrgClassRow[]>([])
const selectedClasses = ref<string[]>([])
const batchResult = ref<BatchOpenResult | null>(null)
const created = ref<CreateAccountResult | null>(null)
const resetResult = ref<{ username: string; newPassword: string } | null>(null)

const query = reactive({
  role: '',
  status: undefined as number | undefined,
  keyword: '',
  pageNum: 1,
  pageSize: 20,
})

const createForm = reactive<{
  role: UserRole
  username: string
  displayName: string
  studentId?: number
  password: string
}>({
  role: 'COU',
  username: '',
  displayName: '',
  password: '',
})

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    const page = await fetchAccounts({
      role: query.role || undefined,
      status: query.status,
      keyword: query.keyword || undefined,
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

async function loadOptions(): Promise<void> {
  try {
    const options = await fetchOrgOptions()
    classes.value = options.classes
  } catch {
    classes.value = []
  }
}

function flash(text: string): void {
  message.value = text
  window.setTimeout(() => {
    if (message.value === text) message.value = ''
  }, 4000)
}

async function submitCreate(): Promise<void> {
  if (!createForm.displayName.trim()) return
  if (createForm.role === 'STU' && !createForm.studentId) return
  try {
    created.value = await createAccount({
      role: createForm.role,
      username: createForm.role === 'STU' ? undefined : createForm.username.trim(),
      displayName: createForm.displayName.trim(),
      studentId: createForm.role === 'STU' ? createForm.studentId : undefined,
      password: createForm.password.trim() || undefined,
    })
    createForm.username = ''
    createForm.displayName = ''
    createForm.studentId = undefined
    createForm.password = ''
    flash('账号已开通，初始密码仅展示一次')
    await load()
  } catch (e) {
    error.value = isApiError(e) ? e : null
  }
}

async function reset(row: AccountUser): Promise<void> {
  try {
    const result = await resetAccountPassword(row.id)
    resetResult.value = { username: result.username, newPassword: result.newPassword }
    flash('密码已重置，请立即转告本人')
  } catch (e) {
    error.value = isApiError(e) ? e : null
  }
}

async function toggle(row: AccountUser): Promise<void> {
  try {
    await updateAccountStatus(row.id, row.status === 1 ? 0 : 1)
    await load()
  } catch (e) {
    error.value = isApiError(e) ? e : null
  }
}

async function changeRole(row: AccountUser): Promise<void> {
  try {
    await assignAccountRole(row.id, row.role)
    flash('角色已调整：' + row.username)
    await load()
  } catch (e) {
    error.value = isApiError(e) ? e : null
  }
}

async function openBatch(): Promise<void> {
  if (selectedClasses.value.length === 0) return
  try {
    batchResult.value = await batchOpenAccounts([...selectedClasses.value])
    flash(`批量开通完成：新建 ${batchResult.value.created} 个，跳过 ${batchResult.value.skipped} 个`)
    await load()
  } catch (e) {
    error.value = isApiError(e) ? e : null
  }
}

function toggleClass(code: string): void {
  const index = selectedClasses.value.indexOf(code)
  if (index >= 0) selectedClasses.value.splice(index, 1)
  else selectedClasses.value.push(code)
}

function formatTime(value?: string): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '—'
}

onMounted(async () => {
  await Promise.all([load(), loadOptions()])
})
</script>

<template>
  <section class="admin">
    <header class="admin__head">
      <div>
        <h1>账号管理</h1>
        <p class="muted">学生账号 id 与学籍绑定，登录名固定为学号；初始密码按身份证后六位生成。</p>
      </div>
      <span class="tag">{{ total }} 个账号</span>
    </header>

    <p v-if="error" class="banner banner--error">{{ error.message }}（{{ error.code }}）</p>
    <p v-if="message" class="banner banner--ok">{{ message }}</p>

    <section class="card">
      <h2>筛选</h2>
      <div class="form">
        <select v-model="query.role" @change="query.pageNum = 1; load()">
          <option value="">全部角色</option>
          <option value="STU">学生</option>
          <option value="COU">辅导员</option>
          <option value="ADMIN">管理员</option>
        </select>
        <select v-model="query.status" @change="query.pageNum = 1; load()">
          <option :value="undefined">全部状态</option>
          <option :value="1">启用</option>
          <option :value="0">停用</option>
        </select>
        <input v-model="query.keyword" placeholder="登录名 / 姓名" @keyup.enter="query.pageNum = 1; load()" />
        <button class="btn" @click="query.pageNum = 1; load()">查询</button>
      </div>

      <table>
        <thead>
          <tr>
            <th>登录名</th><th>姓名</th><th>角色</th><th>学号/班级</th>
            <th>状态</th><th>最近登录</th><th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in rows" :key="row.id">
            <td>{{ row.username }}</td>
            <td>{{ row.displayName }}</td>
            <td>
              <select v-if="!row.studentId" v-model="row.role" @change="changeRole(row)">
                <option value="STU">学生</option>
                <option value="COU">辅导员</option>
                <option value="ADMIN">管理员</option>
              </select>
              <span v-else>{{ row.roleLabel }}</span>
            </td>
            <td>{{ row.studentNo || '—' }} {{ row.className ? ' / ' + row.className : '' }}</td>
            <td>{{ row.status === 1 ? '启用' : '停用' }}</td>
            <td>{{ formatTime(row.lastLoginAt) }}</td>
            <td class="ops">
              <button class="btn btn--small btn--ghost" @click="reset(row)">重置密码</button>
              <button class="btn btn--small btn--ghost" @click="toggle(row)">
                {{ row.status === 1 ? '停用' : '启用' }}
              </button>
            </td>
          </tr>
          <tr v-if="!loading && rows.length === 0">
            <td colspan="7" class="empty">暂无账号</td>
          </tr>
        </tbody>
      </table>

      <div class="pager">
        <button class="btn btn--small btn--ghost" :disabled="query.pageNum <= 1" @click="query.pageNum--; load()">上一页</button>
        <span class="muted">第 {{ query.pageNum }} 页 / 共 {{ Math.max(1, Math.ceil(total / query.pageSize)) }} 页</span>
        <button
          class="btn btn--small btn--ghost"
          :disabled="query.pageNum * query.pageSize >= total"
          @click="query.pageNum++; load()"
        >下一页</button>
      </div>
    </section>

    <section class="card">
      <h2>开通账号</h2>
      <div class="form">
        <select v-model="createForm.role">
          <option value="COU">辅导员</option>
          <option value="ADMIN">管理员</option>
          <option value="STU">学生</option>
        </select>
        <input v-if="createForm.role === 'STU'" v-model.number="createForm.studentId" type="number" placeholder="学生 ID（如 1001）" />
        <input v-else v-model="createForm.username" placeholder="登录名" />
        <input v-model="createForm.displayName" placeholder="姓名" />
        <input v-model="createForm.password" placeholder="初始密码（可空，自动生成）" />
        <button class="btn" @click="submitCreate">开通</button>
      </div>
      <p v-if="created" class="banner banner--ok">
        已开通 {{ created.username }}（{{ created.roleLabel }}），初始密码：<strong>{{ created.initialPassword }}</strong>
      </p>
      <p v-if="resetResult" class="banner banner--ok">
        {{ resetResult.username }} 的新密码：<strong>{{ resetResult.newPassword }}</strong>
      </p>
    </section>

    <section class="card">
      <h2>批量开通（按班级）</h2>
      <p class="muted">为所选班级中「已有学生主数据、尚未开通账号」的学生建号，已有的自动跳过。</p>
      <div class="chips">
        <label v-for="c in classes" :key="c.classCode" class="chip">
          <input
            type="checkbox"
            :checked="selectedClasses.includes(c.classCode)"
            @change="toggleClass(c.classCode)"
          />
          {{ c.className }}（{{ c.studentCount }} 人）
        </label>
      </div>
      <button class="btn" :disabled="selectedClasses.length === 0" @click="openBatch">批量开通</button>
      <div v-if="batchResult" class="result">
        <p class="muted">
          学生 {{ batchResult.totalStudents }}，新建 {{ batchResult.created }}，跳过 {{ batchResult.skipped }}
        </p>
        <table v-if="batchResult.accounts.length > 0">
          <thead><tr><th>学号</th><th>姓名</th><th>登录名</th><th>初始密码</th></tr></thead>
          <tbody>
            <tr v-for="item in batchResult.accounts" :key="item.studentId">
              <td>{{ item.studentNo }}</td>
              <td>{{ item.studentName }}</td>
              <td>{{ item.username }}</td>
              <td>{{ item.initialPassword }}</td>
            </tr>
          </tbody>
        </table>
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
.card {
  background: var(--color-surface);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  padding: 18px;
}
.card h2 {
  margin: 0 0 12px;
  font-size: 16px;
}
.form {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}
input,
select {
  height: 34px;
  padding: 0 10px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: #fff;
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
.ops {
  display: flex;
  gap: 6px;
}
.btn {
  height: 34px;
  padding: 0 14px;
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
  margin: 0 0 12px;
  padding: 10px 12px;
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
.pager {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
}
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 10px 0;
}
.chip {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 10px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  font-size: 13px;
}
.result {
  margin-top: 12px;
}
.empty {
  text-align: center;
  color: var(--color-text-muted);
  padding: 20px;
}
</style>
