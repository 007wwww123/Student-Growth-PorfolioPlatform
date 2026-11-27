<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DecisionDialog from '@/components/DecisionDialog.vue'
import FieldList from '@/components/FieldList.vue'
import PendingHint from '@/components/PendingHint.vue'
import { fetchCompletionStatus } from '@/api/completion'
import { isApiError, type ApiError } from '@/api/http'
import { approveSection, rejectSection } from '@/api/review'
import {
  confirmPendingField,
  fetchSection,
  rejectPendingField,
  revealField,
} from '@/api/sections'
import type { CompletionStatus, SectionCode, SectionVO } from '@/api/types'
import { formatValue, formFromFields, renderFieldsOf, type PendingItem } from '@/fieldMeta'
import { DEMO_ACADEMIC_YEAR, SECTION_MAP, STATUS_LABELS } from '@/sectionMeta'

/**
 * 审核详情（S-1-06 详情 / S-1-07 结论）。
 *
 * <p>页面本身不复制一套字段渲染：字段清单与权限位仍由板块详情接口下发，
 * 这里只把控件设为只读，并补上审核动作与待确认值处理。</p>
 *
 * <p>可见性由后端判定（非本班 403），前端不根据身份隐藏数据，
 * 否则同一份页面在不同入口会表现不一致。</p>
 */
const route = useRoute()
const router = useRouter()

const code = computed(() => String(route.params.code).toLowerCase() as SectionCode)
const studentId = computed(() => Number(route.params.studentId))
const academicYear = computed(() => String(route.query.academicYear ?? DEMO_ACADEMIC_YEAR))

const section = ref<SectionVO | null>(null)
const form = ref<Record<string, string>>({})
const revealed = ref<Record<string, string>>({})
const loading = ref(false)
const error = ref<ApiError | null>(null)
const actionError = ref('')
const notice = ref('')
const processing = ref<string | null>(null)

/** 完成标记（BR-S10）：服务端派生值，页面只读展示，通过审核后自动跟随刷新。 */
const completion = ref<CompletionStatus | null>(null)
const completionError = ref('')

const dialog = reactive({
  open: false,
  mode: 'approve' as 'approve' | 'reject',
  error: '',
})
const submitting = ref(false)

const fields = computed(() =>
  renderFieldsOf(section.value?.fieldMeta, SECTION_MAP[code.value]?.fields),
)

const statusLabel = computed(() =>
  section.value ? (STATUS_LABELS[section.value.status] ?? section.value.status) : '',
)

const canDecide = computed(() => section.value?.status === 'SUBMITTED')

const studentName = computed(
  () => String(route.query.name ?? form.value.name ?? `学生 ${studentId.value}`),
)
const className = computed(() => String(route.query.className ?? form.value.classId ?? '—'))

const completenessText = computed(() => {
  const value = section.value?.completeness
  if (value == null) {
    return '—'
  }
  return `${Number(value).toFixed(2).replace(/\.00$/, '')}%`
})

/** 待确认值：新值已提交但尚未生效，审核页在这里处理。 */
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

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    const data = await fetchSection(code.value, studentId.value, academicYear.value)
    section.value = data
    form.value = formFromFields(data.fields, renderFieldsOf(data.fieldMeta))
    revealed.value = {}
  } catch (e) {
    section.value = null
    error.value = isApiError(e) ? e : null
  } finally {
    loading.value = false
  }
  await loadCompletion()
}

/**
 * 读取完成标记。
 *
 * <p>单独一个接口而不是塞进板块详情：标记是「学生 × 学年」级别，
 * 与当前正在看哪个板块无关；失败也不该让整个详情页报错，只在卡片里提示。</p>
 */
async function loadCompletion(): Promise<void> {
  completionError.value = ''
  try {
    completion.value = await fetchCompletionStatus(studentId.value, academicYear.value)
  } catch (e) {
    completion.value = null
    completionError.value = isApiError(e) ? `${e.message}（${e.code}）` : '完成标记加载失败'
  }
}

/** 查看敏感字段明文：每次点击都会在后端写一条审计日志。 */
async function onReveal(key: string): Promise<void> {
  const meta = section.value?.fieldMeta?.find((item) => item.key === key)
  if (!meta) {
    return
  }
  processing.value = key
  actionError.value = ''
  try {
    const data = await revealField(code.value, studentId.value, meta.fieldCode, academicYear.value)
    revealed.value = { ...revealed.value, [key]: data.value }
  } catch (e) {
    actionError.value = isApiError(e) ? `${e.message}（${e.code}）` : '查看失败'
  } finally {
    processing.value = null
  }
}

async function onConfirmPending(key: string): Promise<void> {
  const meta = section.value?.fieldMeta?.find((item) => item.key === key)
  if (!meta) {
    return
  }
  processing.value = key
  actionError.value = ''
  try {
    await confirmPendingField(code.value, studentId.value, meta.fieldCode, academicYear.value)
    notice.value = `「${meta.fieldName}」的新值已生效`
    await load()
  } catch (e) {
    actionError.value = isApiError(e) ? `${e.message}（${e.code}）` : '确认失败'
  } finally {
    processing.value = null
  }
}

async function onRejectPending(key: string): Promise<void> {
  const meta = section.value?.fieldMeta?.find((item) => item.key === key)
  if (!meta) {
    return
  }
  processing.value = key
  actionError.value = ''
  try {
    await rejectPendingField(code.value, studentId.value, meta.fieldCode, academicYear.value)
    notice.value = `已驳回「${meta.fieldName}」的新值，档案保留原值`
    await load()
  } catch (e) {
    actionError.value = isApiError(e) ? `${e.message}（${e.code}）` : '驳回失败'
  } finally {
    processing.value = null
  }
}

function openDialog(mode: 'approve' | 'reject'): void {
  dialog.open = true
  dialog.mode = mode
  dialog.error = ''
}

async function onDecision(payload: { comment?: string; reason?: string }): Promise<void> {
  submitting.value = true
  dialog.error = ''
  try {
    if (dialog.mode === 'approve') {
      await approveSection(code.value, studentId.value, {
        academicYear: academicYear.value,
        comment: payload.comment,
      })
      notice.value = '已通过该板块，学生不可再修改'
    } else {
      await rejectSection(code.value, studentId.value, {
        academicYear: academicYear.value,
        reason: payload.reason,
      })
      notice.value = '已退回，理由已回传学生端'
    }
    dialog.open = false
    await load()
  } catch (e) {
    if (isApiError(e)) {
      // 状态冲突说明别人已经处理过，继续留在弹窗里没有意义
      if (e.code.startsWith('EC-409')) {
        dialog.open = false
        actionError.value = `${e.message}（${e.code}），已刷新最新状态`
        await load()
      } else {
        dialog.error = `${e.message}（${e.code}）`
      }
    } else {
      dialog.error = '操作失败，请重试'
    }
  } finally {
    submitting.value = false
  }
}

function goBack(): void {
  void router.push({ name: 'review-list' })
}

watch([code, studentId], load)

onMounted(load)
</script>

<template>
  <section class="detail">
    <button type="button" class="detail__back" @click="goBack">← 返回待审列表</button>

    <p v-if="loading" class="detail__loading">加载中…</p>

    <div v-else-if="error" class="alert alert--danger">
      <div class="alert__head">
        <strong>{{ error.code }}</strong>
        <span>{{ error.message }}</span>
      </div>
      <p v-if="error.code === 'EC-40302'" class="alert__tip">
        该学生不在你负责的班级内，或当前身份不是辅导员。
      </p>
      <button type="button" class="alert__retry" @click="load">重试</button>
    </div>

    <template v-else-if="section">
      <header class="detail__head">
        <div>
          <h1 class="detail__title">{{ studentName }}</h1>
          <p class="detail__meta">
            {{ section.sectionName }} · {{ section.academicYear }} · 班级 {{ className }}
          </p>
        </div>
        <div class="detail__state">
          <span class="detail__completeness">完整度 {{ completenessText }}</span>
          <span class="tag" :class="`tag--${section.status}`">{{ statusLabel }}</span>
        </div>
      </header>

      <dl class="facts">
        <div class="facts__item">
          <dt>学号</dt>
          <dd>{{ form.studentNo || '—' }}</dd>
        </div>
        <div class="facts__item">
          <dt>提交次数</dt>
          <dd>{{ section.submitCount ?? 0 }}</dd>
        </div>
        <div class="facts__item">
          <dt>提交时间</dt>
          <dd>{{ section.submittedAt?.replace('T', ' ').slice(0, 16) ?? '—' }}</dd>
        </div>
        <div class="facts__item">
          <dt>撤回次数</dt>
          <dd>{{ section.withdrawCount ?? 0 }} / 1</dd>
        </div>
      </dl>

      <div v-if="section.status === 'RETURNED' && section.reviewReason" class="alert alert--warn">
        <div class="alert__head">
          <strong>已退回</strong>
          <span v-if="section.reviewedAt" class="alert__time">{{ section.reviewedAt }}</span>
        </div>
        <p class="alert__reason">{{ section.reviewReason }}</p>
      </div>

      <p v-if="notice" class="alert alert--success">{{ notice }}</p>
      <p v-if="actionError" class="alert alert--danger">{{ actionError }}</p>

      <PendingHint
        :items="pendingItems"
        actionable
        :processing="processing"
        @confirm="onConfirmPending"
        @reject="onRejectPending"
      />

      <div class="card">
        <div class="card__head">
          <h2 class="card__title">六板块完成标记</h2>
          <span
            v-if="completion"
            class="tag"
            :class="completion.completed ? 'tag--APPROVED' : 'tag--DRAFT'"
          >
            {{ completion.completed ? '已完成' : '未完成' }}
          </span>
        </div>
        <p v-if="completionError" class="alert alert--danger">{{ completionError }}</p>
        <template v-else-if="completion">
          <p class="completion__summary">
            已通过 {{ completion.completedSectionCount }} / {{ completion.sections.length }} 个板块
            <template v-if="completion.pendingSections.length">
              · 待完成：{{ completion.pendingSections.join('、') }}
            </template>
          </p>
          <ul class="completion__grid">
            <li
              v-for="item in completion.sections"
              :key="item.sectionCode"
              class="completion__item"
              :class="{
                'completion__item--done': item.completed,
                'completion__item--missing': !item.status,
              }"
            >
              <span class="completion__name">{{ item.sectionName }}</span>
              <span class="completion__state">{{ item.statusLabel }}</span>
            </li>
          </ul>
          <p class="completion__tip">
            标记由服务端按「六板块全部通过」判定并落库，页面不参与计算；已归档板块仍计为完成。
          </p>
        </template>
        <p v-else class="completion__summary">完成标记加载中…</p>
      </div>

      <div class="card">
        <h2 class="card__title">档案内容</h2>
        <FieldList
          :fields="fields"
          :form="form"
          :revealed="revealed"
          revealable
          readonly
          @reveal="onReveal"
        />
      </div>

      <div class="actions">
        <template v-if="canDecide">
          <button type="button" class="btn btn--primary" @click="openDialog('approve')">
            审核通过
          </button>
          <button type="button" class="btn btn--danger" @click="openDialog('reject')">
            退回修改
          </button>
          <span class="actions__tip">退回理由会回传学生端，不少于 10 字</span>
        </template>
        <p v-else class="actions__locked">
          当前状态为「{{ statusLabel }}」，无需审核；只有学生新提交的档案才会进入待审。
        </p>
      </div>
    </template>

    <DecisionDialog
      :open="dialog.open"
      :mode="dialog.mode"
      :submitting="submitting"
      :server-error="dialog.error"
      @close="dialog.open = false"
      @submit="onDecision"
    />
  </section>
</template>

<style scoped>
.detail {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.detail__back {
  align-self: flex-start;
  padding: 0;
  border: none;
  background: none;
  color: var(--color-primary);
  font-size: 13px;
}

.detail__loading {
  color: var(--color-text-muted);
}

.detail__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.detail__title {
  margin: 0 0 4px;
  font-size: 19px;
  font-weight: 600;
}

.detail__meta {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 13px;
}

.detail__state {
  display: flex;
  align-items: center;
  gap: 10px;
}

.detail__completeness {
  color: var(--color-text-muted);
  font-size: 12px;
}

.facts {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px 16px;
  margin: 0;
  padding: 14px 16px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  background: var(--color-surface);
}

.facts__item dt {
  color: var(--color-text-muted);
  font-size: 12px;
}

.facts__item dd {
  margin: 2px 0 0;
  font-size: 13px;
}

.card {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 18px 20px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  background: var(--color-surface);
  box-shadow: var(--shadow);
}

.card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.card__title {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
}

.completion__summary {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 13px;
}

.completion__grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.completion__item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px 10px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: var(--color-bg);
  font-size: 12px;
}

.completion__item--done {
  border-color: #bbf7d0;
  background: var(--color-success-bg);
}

.completion__item--missing {
  border-style: dashed;
  color: var(--color-text-muted);
}

.completion__name {
  font-weight: 600;
}

.completion__state {
  color: var(--color-text-muted);
}

.completion__tip {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 12px;
}

.actions {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  background: var(--color-surface);
}

.actions__tip {
  color: var(--color-text-muted);
  font-size: 12px;
}

.actions__locked {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 13px;
}

.btn {
  padding: 7px 14px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: #fff;
  color: var(--color-text);
}

.btn--primary {
  border-color: var(--color-primary);
  background: var(--color-primary);
  color: #fff;
}

.btn--primary:hover {
  border-color: var(--color-primary-dark);
  background: var(--color-primary-dark);
}

.btn--danger {
  border-color: var(--color-danger);
  background: var(--color-danger);
  color: #fff;
}

.alert {
  margin: 0;
  padding: 10px 12px;
  border-radius: 6px;
  font-size: 13px;
}

.alert--danger {
  border: 1px solid #fecaca;
  background: var(--color-danger-bg);
  color: var(--color-danger);
}

.alert--success {
  border: 1px solid #bbf7d0;
  background: var(--color-success-bg);
  color: var(--color-success);
}

.alert--warn {
  border: 1px solid #fed7aa;
  background: var(--color-warning-bg);
  color: var(--color-warning);
}

.alert__head {
  display: flex;
  gap: 8px;
}

.alert__time {
  font-size: 12px;
  opacity: 0.8;
}

.alert__reason {
  margin: 6px 0 0;
  font-size: 13px;
  line-height: 1.6;
}

.alert__tip {
  margin: 6px 0 0;
  font-size: 12.5px;
}

.alert__retry {
  margin-top: 8px;
  padding: 4px 10px;
  border: 1px solid #fecaca;
  border-radius: 6px;
  background: #fff;
  color: var(--color-danger);
  font-size: 12.5px;
}
</style>
