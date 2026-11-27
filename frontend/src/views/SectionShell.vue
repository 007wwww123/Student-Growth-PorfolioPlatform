<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import FieldList from '@/components/FieldList.vue'
import PendingHint from '@/components/PendingHint.vue'
import { isApiError, type ApiError } from '@/api/http'
import { getUser } from '@/auth/session'
import { fetchSection, saveSection, submitSection, withdrawSection } from '@/api/sections'
import type { SectionCode, SectionVO } from '@/api/types'
import { formatValue, formFromFields, renderFieldsOf, type PendingItem } from '@/fieldMeta'
import { DEMO_ACADEMIC_YEAR, DEMO_STUDENT_ID, SECTION_MAP, STATUS_LABELS } from '@/sectionMeta'

/**
 * 板块页面的公共外壳。
 *
 * 六个板块的交互完全一致（查详情 → 改草稿 → 提交 → 必要时撤回），差异只在字段清单。
 * 字段清单以服务端下发的 fieldMeta 为准（arc_field_def 是唯一来源，含控件类型、
 * 枚举选项与「当前角色能不能写」），sectionMeta 只在接口未提供 fieldMeta 时兜底。
 */
const props = defineProps<{ code: SectionCode }>()

const meta = computed(() => SECTION_MAP[props.code])
const section = ref<SectionVO | null>(null)
const form = ref<Record<string, string>>({})
/** 载入时的表单快照：用于识别「脱敏值没改过」，避免把 138****1234 当成新值提交。 */
const initialForm = ref<Record<string, string>>({})
const loading = ref(false)
const saving = ref(false)
const submitting = ref(false)
const withdrawing = ref(false)
const confirmingWithdraw = ref(false)
const error = ref<ApiError | null>(null)
const notice = ref('')

/** 学生看自己的档案；辅导员/管理员从这里进入时只能查看，学生 ID 由路由上下文决定。 */
const user = getUser()
const studentId = computed(() => user?.studentId ?? DEMO_STUDENT_ID)

/**
 * 管理员只配置字段权限，不编辑档案内容。
 *
 * <p>后端保存接口对 ADMIN 统一返回 EC-40303，这里再截一层，避免管理员点了保存才发现
 * 没有权限；强制只读也保证即使某个字段的 editable 位被误配为 true，也不会在管理端出现输入框。</p>
 */
const isAdmin = computed(() => user?.role === 'ADMIN')
const editable = computed(() => (section.value?.editable ?? true) && !isAdmin.value)
const statusLabel = computed(() =>
  section.value ? (STATUS_LABELS[section.value.status] ?? section.value.status) : '',
)

/** 学生本人才有撤回入口；辅导员与管理员进入时只看得到只读内容。 */
const isStudent = computed(() => user?.role === 'STU')
const canWithdraw = computed(() => isStudent.value && section.value?.status === 'SUBMITTED')
const withdrawUsed = computed(() => (section.value?.withdrawCount ?? 0) >= 1)

/**
 * 退回提示（BR-S04：退回理由必须让学生看见）。
 *
 * 理由由后端详情接口带回，仅在「已退回」状态下下发；学生重新提交后后端不再返回，
 * 这里自然隐藏，不会把旧意见当成当前结论。
 */
const returnNote = computed(() => {
  const current = section.value
  if (!current?.reviewReason) {
    return null
  }
  return { reason: current.reviewReason, at: current.reviewedAt ?? '' }
})

/** 完成度由服务端按必填字段现算，前端只负责展示。 */
const completenessText = computed(
  () => `${Number(section.value?.completeness ?? 0).toFixed(2).replace(/\.00$/, '')}%`,
)

const fields = computed(() => renderFieldsOf(section.value?.fieldMeta, meta.value.fields))

/**
 * 学校维护、需辅导员确认的字段被学生改过之后，新值先进入待确认区。
 * 学生端只展示，不能自行确认（确认权限在后端限定为该班辅导员）。
 */
const pendingItems = computed<PendingItem[]>(() => {
  const metas = section.value?.fieldMeta ?? []
  return metas
    .filter((item) => item.pendingConfirm === true)
    .map((item) => ({
      key: item.key,
      label: item.fieldName,
      current: formatValue(section.value?.fields?.[item.key]),
      pending: formatValue(item.pendingValue),
    }))
})

/** 字段级错误：field -> 提示文案，用于在输入框下方就地提示。 */
const fieldHints = computed<Record<string, string>>(() => {
  const hints: Record<string, string> = {}
  for (const detail of error.value?.details ?? []) {
    if (detail.field) {
      hints[detail.field] = detail.message ?? detail.reason ?? '校验未通过'
    }
  }
  return hints
})

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  notice.value = ''
  confirmingWithdraw.value = false
  try {
    const data = await fetchSection(props.code, studentId.value, DEMO_ACADEMIC_YEAR)
    section.value = data
    form.value = formFromFields(data.fields, renderFieldsOf(data.fieldMeta))
    initialForm.value = { ...form.value }
  } catch (e) {
    section.value = null
    error.value = isApiError(e) ? e : null
  } finally {
    loading.value = false
  }
}

/**
 * 组装保存请求体。
 *
 * <p>后端 SaveDTO 是 {@code { academicYear, fields: {...} }} 的信封结构，不是扁平表单；
 * 同时只提交「当前角色可写」的字段——学校维护的字段（SIS/SYS）混进请求会被整体拒绝（403），
 * 而不是忽略该字段。没有可写字段时返回 null，由调用方提示，不发无效请求。</p>
 */
function buildPayload(): Record<string, unknown> | null {
  const values: Record<string, unknown> = {}
  for (const field of fields.value) {
    // 附件上传尚未开放，不提交，避免把已有附件清空
    if (!field.editable || field.type === 'file') {
      continue
    }
    const raw = (form.value[field.key] ?? '').trim()
    // 脱敏字段（手机、身份证）载入时展示的是掩码值；没改过就代表「保持原值」，
    // 不能把 138****1234 当新值提交，否则会被格式校验拦下或写坏数据。
    if (field.masked === true && raw === (initialForm.value[field.key] ?? '').trim()) {
      continue
    }
    if (field.type === 'tags') {
      values[field.key] = raw === ''
        ? null
        : raw.split(/[,，、]/).map((item) => item.trim()).filter(Boolean)
    } else if (field.type === 'number') {
      values[field.key] = raw === '' ? null : Number(raw)
    } else {
      values[field.key] = raw === '' ? null : raw
    }
  }
  if (Object.keys(values).length === 0) {
    return null
  }
  return {
    academicYear: section.value?.academicYear ?? DEMO_ACADEMIC_YEAR,
    fields: values,
  }
}

async function onSave(): Promise<void> {
  error.value = null
  notice.value = ''
  const payload = buildPayload()
  if (!payload) {
    notice.value = '当前没有可修改的字段'
    return
  }
  saving.value = true
  try {
    await saveSection(props.code, studentId.value, payload)
    notice.value = '草稿已保存'
    await load()
  } catch (e) {
    error.value = isApiError(e) ? e : null
  } finally {
    saving.value = false
  }
}

async function onSubmit(): Promise<void> {
  submitting.value = true
  error.value = null
  notice.value = ''
  try {
    await submitSection(
      props.code,
      studentId.value,
      section.value?.academicYear ?? DEMO_ACADEMIC_YEAR,
    )
    notice.value = '已提交，等待审核'
    await load()
  } catch (e) {
    error.value = isApiError(e) ? e : null
  } finally {
    submitting.value = false
  }
}

/**
 * 撤回提交（S-1-09）。
 *
 * 全生命周期只允许一次：按钮在有撤回记录后保持禁用，且后端再校验一次（EC-40903）。
 * 前端禁用只是减少一次必然失败的请求，不作为权限判断。
 */
async function onWithdraw(): Promise<void> {
  if (withdrawUsed.value) {
    notice.value = '撤回次数已用完，每个板块只能撤回一次'
    confirmingWithdraw.value = false
    return
  }
  withdrawing.value = true
  error.value = null
  notice.value = ''
  try {
    await withdrawSection(
      props.code,
      studentId.value,
      section.value?.academicYear ?? DEMO_ACADEMIC_YEAR,
    )
    notice.value = '已撤回，可继续修改后重新提交'
    confirmingWithdraw.value = false
    await load()
  } catch (e) {
    error.value = isApiError(e) ? e : null
    confirmingWithdraw.value = false
  } finally {
    withdrawing.value = false
  }
}

watch(() => props.code, load, { immediate: true })
</script>

<template>
  <section class="board">
    <header class="board__head">
      <div>
        <h1 class="board__title">{{ meta.name }}</h1>
        <p class="board__summary">{{ meta.summary }}</p>
      </div>
      <div class="board__state">
        <span class="board__completeness">完整度 {{ completenessText }}</span>
        <span v-if="section" class="tag" :class="`tag--${section.status}`">{{ statusLabel }}</span>
      </div>
    </header>

    <!-- 管理员模式：只读查看 + 跳转本板块的权限配置，不允许借板块页改内容 -->
    <div v-if="isAdmin" class="alert alert--admin">
      <strong>管理员只读</strong>
      <span>
        管理员只负责配置字段编辑权限，不能修改板块内容。需要调整「谁能改哪个字段」请前往
        <RouterLink :to="{ path: '/admin/permissions', query: { section: props.code } }">
          本板块字段权限
        </RouterLink>
        。
      </span>
    </div>

    <!-- 退回理由（BR-S04）：学生必须能看到被退回的原因，不能只给一个「已退回」标签 -->
    <div v-if="returnNote" class="alert alert--return">
      <div class="alert__head">
        <strong>当前板块被退回</strong>
        <span v-if="returnNote.at" class="alert__time">{{ returnNote.at }}</span>
      </div>
      <p class="alert__reason">{{ returnNote.reason }}</p>
      <p v-if="section?.status === 'RETURNED'" class="alert__tip">
        修改后重新提交即可再次进入审核。
      </p>
    </div>

    <p v-if="notice" class="alert alert--success">{{ notice }}</p>

    <div v-if="error" class="alert alert--danger">
      <div class="alert__head">
        <strong>{{ error.code }}</strong>
        <span>{{ error.message }}</span>
      </div>
      <ul v-if="error.details?.length" class="alert__details">
        <li v-for="(detail, index) in error.details" :key="index">
          {{ detail.field ?? '字段' }}：{{ detail.message ?? detail.reason ?? '校验未通过' }}
        </li>
      </ul>
      <p v-if="error.code === 'EC-40903'" class="alert__tip">
        撤回只有一次机会，已撤回过的板块不能再撤回。
      </p>
      <p v-if="error.traceId" class="alert__trace">traceId：{{ error.traceId }}</p>
    </div>

    <p v-if="loading" class="board__loading">加载中…</p>

    <template v-else-if="section">
      <!-- 待确认区：学生只能看到「等待确认」，确认动作属于辅导员 -->
      <PendingHint :items="pendingItems" />

      <form class="board__form" @submit.prevent="onSave">
        <FieldList :fields="fields" :form="form" :errors="fieldHints" :readonly="isAdmin" />

        <div class="board__actions">
          <!-- 可编辑：草稿 / 已退回 -->
          <template v-if="editable">
            <button type="submit" class="btn btn--primary" :disabled="saving">
              {{ saving ? '保存中…' : '保存草稿' }}
            </button>
            <button type="button" class="btn" :disabled="submitting" @click="onSubmit">
              {{ submitting ? '提交中…' : '提交板块' }}
            </button>
          </template>

          <!-- 已提交：学生可撤回一次 -->
          <template v-else-if="canWithdraw">
            <button
              type="button"
              class="btn btn--danger-ghost"
              :disabled="withdrawing || withdrawUsed"
              @click="confirmingWithdraw = true"
            >
              {{ withdrawing ? '撤回中…' : '撤回提交' }}
            </button>
            <span class="board__lock">
              审核前可撤回一次（已用 {{ section.withdrawCount ?? 0 }}/1）
            </span>
          </template>

          <span v-else class="board__lock">当前状态不可编辑</span>

          <button type="button" class="btn btn--ghost" @click="load">重新加载</button>
        </div>

        <!-- 撤回二次确认：说明后果与次数限制，避免误点 -->
        <div v-if="confirmingWithdraw" class="confirm">
          <p class="confirm__text">
            撤回后该板块回到「草稿」状态，需要重新提交；每个板块只能撤回一次，确定撤回吗？
          </p>
          <div class="confirm__actions">
            <button
              type="button"
              class="btn"
              :disabled="withdrawing"
              @click="confirmingWithdraw = false"
            >
              取消
            </button>
            <button type="button" class="btn btn--danger" :disabled="withdrawing" @click="onWithdraw">
              {{ withdrawing ? '撤回中…' : '确认撤回' }}
            </button>
          </div>
        </div>
      </form>

      <details class="raw">
        <summary>查看接口原始返回（联调用）</summary>
        <pre>{{ JSON.stringify(section, null, 2) }}</pre>
      </details>
    </template>
  </section>
</template>

<style scoped>
.board {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.board__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.board__title {
  margin: 0 0 4px;
  font-size: 19px;
  font-weight: 600;
}

.board__summary {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 13px;
}

.board__state {
  display: flex;
  align-items: center;
  gap: 10px;
}

.board__completeness {
  color: var(--color-text-muted);
  font-size: 12px;
}

.alert--return {
  border: 1px solid #fed7aa;
  background: #fff7ed;
  color: #9a3412;
}

.alert--admin {
  display: flex;
  gap: 10px;
  border: 1px solid #bfdbfe;
  background: #eff6ff;
  color: #1e40af;
}

.alert--admin a {
  color: #1d4ed8;
  font-weight: 600;
  text-decoration: underline;
}

.alert__reason {
  margin: 6px 0 0;
  font-size: 13px;
  line-height: 1.6;
}

.alert__time {
  font-size: 12px;
  opacity: 0.75;
}

.alert__tip {
  margin: 6px 0 0;
  font-size: 12px;
  opacity: 0.85;
}

.board__loading {
  color: var(--color-text-muted);
}

.board__form {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 20px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  background: var(--color-surface);
  box-shadow: var(--shadow);
}

.board__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-top: 4px;
  border-top: 1px dashed var(--color-border);
}

.board__lock {
  color: var(--color-text-muted);
  font-size: 12px;
}

.confirm {
  padding: 12px 14px;
  border: 1px solid #fecaca;
  border-radius: 6px;
  background: var(--color-danger-bg);
}

.confirm__text {
  margin: 0;
  color: var(--color-danger);
  font-size: 13px;
}

.confirm__actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.btn {
  padding: 7px 14px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: #fff;
  color: var(--color-text);
}

.btn:hover:not(:disabled) {
  border-color: #cbd5e1;
  background: #f9fafb;
}

.btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.btn--primary {
  border-color: var(--color-primary);
  background: var(--color-primary);
  color: #fff;
}

.btn--primary:hover:not(:disabled) {
  border-color: var(--color-primary-dark);
  background: var(--color-primary-dark);
}

.btn--danger {
  border-color: var(--color-danger);
  background: var(--color-danger);
  color: #fff;
}

.btn--danger-ghost {
  border-color: #fecaca;
  color: var(--color-danger);
}

.btn--ghost {
  border-color: transparent;
  color: var(--color-text-muted);
}

.alert {
  margin: 0;
  padding: 10px 12px;
  border-radius: 6px;
  font-size: 13px;
}

.alert--success {
  border: 1px solid #bbf7d0;
  background: var(--color-success-bg);
  color: var(--color-success);
}

.alert--danger {
  border: 1px solid #fecaca;
  background: var(--color-danger-bg);
  color: var(--color-danger);
}

.alert__head {
  display: flex;
  gap: 8px;
}

.alert__details {
  margin: 6px 0 0;
  padding-left: 18px;
}

.alert__trace {
  margin: 6px 0 0;
  color: #b45454;
  font-size: 12px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

.raw {
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  background: var(--color-surface);
  padding: 10px 14px;
  color: var(--color-text-muted);
  font-size: 12.5px;
}

.raw pre {
  max-height: 320px;
  overflow: auto;
  margin: 10px 0 0;
  color: var(--color-text);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
}
</style>
