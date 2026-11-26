<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { isApiError, type ApiError } from '@/api/http'
import { fetchSection, saveSection, submitSection } from '@/api/sections'
import type { SectionCode, SectionVO } from '@/api/types'
import { DEMO_ACADEMIC_YEAR, DEMO_STUDENT_ID, SECTION_MAP, STATUS_LABELS } from '@/sectionMeta'

/**
 * 板块页面的公共外壳。
 *
 * 六个板块的交互完全一致（查详情 → 改草稿 → 提交），
 * 差异只在于字段清单，因此由 sectionMeta 驱动渲染；
 * 各板块视图只负责传入 code，后续板块特有的交互（附件、证书列表等）在各自视图内扩展。
 */
const props = defineProps<{ code: SectionCode }>()

const meta = computed(() => SECTION_MAP[props.code])
const section = ref<SectionVO | null>(null)
const form = reactive<Record<string, string>>({})
const loading = ref(false)
const saving = ref(false)
const submitting = ref(false)
const error = ref<ApiError | null>(null)
const notice = ref('')

const editable = computed(() => section.value?.editable ?? true)
const statusLabel = computed(() =>
  section.value ? (STATUS_LABELS[section.value.status] ?? section.value.status) : '',
)

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

function inputType(type: string): string {
  if (type === 'number') return 'number'
  if (type === 'date') return 'date'
  return 'text'
}

function resetForm(fields: Record<string, unknown>): void {
  for (const key of Object.keys(form)) {
    delete form[key]
  }
  for (const field of meta.value.fields) {
    const raw = fields[field.key]
    if (Array.isArray(raw)) {
      form[field.key] = raw.join('、')
    } else {
      form[field.key] = raw == null ? '' : String(raw)
    }
  }
}

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  notice.value = ''
  try {
    const data = await fetchSection(props.code, DEMO_STUDENT_ID, DEMO_ACADEMIC_YEAR)
    section.value = data
    resetForm(data.fields ?? {})
  } catch (e) {
    section.value = null
    error.value = isApiError(e) ? e : null
  } finally {
    loading.value = false
  }
}

/** 把表单值按字段类型还原成后端 SaveDTO 的形状。 */
function buildPayload(): Record<string, unknown> {
  const payload: Record<string, unknown> = { academicYear: section.value?.academicYear ?? DEMO_ACADEMIC_YEAR }
  for (const field of meta.value.fields) {
    const value = (form[field.key] ?? '').trim()
    if (field.type === 'tags') {
      payload[field.key] = value
        .split(/[,，、]/)
        .map((item) => item.trim())
        .filter(Boolean)
    } else if (field.type === 'number') {
      payload[field.key] = value === '' ? null : Number(value)
    } else {
      payload[field.key] = value === '' ? null : value
    }
  }
  return payload
}

async function onSave(): Promise<void> {
  saving.value = true
  error.value = null
  notice.value = ''
  try {
    await saveSection(props.code, DEMO_STUDENT_ID, buildPayload())
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
      DEMO_STUDENT_ID,
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

watch(() => props.code, load, { immediate: true })
</script>

<template>
  <section class="board">
    <header class="board__head">
      <div>
        <h1 class="board__title">{{ meta.name }}</h1>
        <p class="board__summary">{{ meta.summary }}</p>
      </div>
      <span v-if="section" class="tag" :class="`tag--${section.status}`">{{ statusLabel }}</span>
    </header>

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
      <p v-if="error.traceId" class="alert__trace">traceId：{{ error.traceId }}</p>
    </div>

    <p v-if="loading" class="board__loading">加载中…</p>

    <form v-else class="board__form" @submit.prevent="onSave">
      <div v-for="field in meta.fields" :key="field.key" class="field">
        <label class="field__label" :for="`field-${field.key}`">{{ field.label }}</label>
        <textarea
          v-if="field.type === 'textarea'"
          :id="`field-${field.key}`"
          v-model="form[field.key]"
          class="field__control"
          :class="{ 'has-error': fieldHints[field.key] }"
          rows="3"
          :disabled="!editable"
        />
        <input
          v-else
          :id="`field-${field.key}`"
          v-model="form[field.key]"
          class="field__control"
          :class="{ 'has-error': fieldHints[field.key] }"
          :type="inputType(field.type)"
          :disabled="!editable"
        />
        <p v-if="fieldHints[field.key]" class="field__error">{{ fieldHints[field.key] }}</p>
        <p v-else-if="field.type === 'tags'" class="field__hint">多个值用逗号分隔</p>
      </div>

      <div class="board__actions">
        <button type="submit" class="btn btn--primary" :disabled="!editable || saving">
          {{ saving ? '保存中…' : '保存草稿' }}
        </button>
        <button
          type="button"
          class="btn"
          :disabled="!editable || submitting"
          @click="onSubmit"
        >
          {{ submitting ? '提交中…' : '提交板块' }}
        </button>
        <button type="button" class="btn btn--ghost" @click="load">重新加载</button>
        <span v-if="!editable" class="board__lock">当前状态不可编辑</span>
      </div>
    </form>

    <details v-if="section" class="raw">
      <summary>查看接口原始返回（联调用）</summary>
      <pre>{{ JSON.stringify(section, null, 2) }}</pre>
    </details>
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

.field {
  display: grid;
  grid-template-columns: 110px 1fr;
  align-items: start;
  gap: 8px 12px;
}

.field__label {
  padding-top: 7px;
  color: var(--color-text-muted);
  font-size: 13px;
}

.field__control {
  width: 100%;
  padding: 7px 10px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: #fff;
  outline: none;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.field__control:focus {
  border-color: #93c5fd;
  box-shadow: 0 0 0 3px rgb(37 99 235 / 10%);
}

.field__control:disabled {
  background: #f9fafb;
  color: var(--color-text-muted);
}

.field__control.has-error {
  border-color: #fca5a5;
}

.field__error {
  grid-column: 2;
  margin: 0;
  color: var(--color-danger);
  font-size: 12px;
}

.field__hint {
  grid-column: 2;
  margin: 0;
  color: #9ca3af;
  font-size: 12px;
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
