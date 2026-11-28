<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { isApiError, type ApiError } from '@/api/http'
import { appendMentorNote, fetchMentorSection, submitMentorFeedback } from '@/api/mentor'
import { submitSection, withdrawSection } from '@/api/sections'
import type { MentorNoteVO, MentorSectionVO } from '@/api/types'
import { getUser } from '@/auth/session'
import { DEMO_ACADEMIC_YEAR, DEMO_STUDENT_ID, STATUS_LABELS } from '@/sectionMeta'

/**
 * 板块六 导师寄语：独立谈话时间线页面。
 *
 * <p>本板块不使用 SectionShell：需求 4.7 明确「没有主表字段值，全部内容在子表，
 * 一行一次谈话」，用「一张表单 + 一个字段网格」表达不了 N 次谈话，
 * 也没法表达「每条记录只能反馈一次」。页面直接消费后端下发的 notes 时间线。</p>
 *
 * <p>权限不在前端判定：canAppend / canFeedback 由服务端按角色与状态给出，
 * 这里只决定展示，后端仍会二次校验（越权追加 EC-40303 / EC-40302）。</p>
 */
const user = getUser()
const studentId = computed(() => user?.studentId ?? DEMO_STUDENT_ID)
const academicYear = ref(DEMO_ACADEMIC_YEAR)
const isStudent = computed(() => user?.role === 'STU')
const isCounselor = computed(() => user?.role === 'COU')

const section = ref<MentorSectionVO | null>(null)
const loading = ref(false)
const appending = ref(false)
const submitting = ref(false)
const withdrawing = ref(false)
const feedbacking = ref<number | null>(null)
const error = ref<ApiError | null>(null)
const notice = ref('')

/** 追加表单初值。谈话日期默认今天，主题取字典第一项。 */
const today = new Date().toISOString().slice(0, 10)
const draft = reactive({
  talkDate: today,
  topic: 'ACADEMIC',
  advice: '',
  starLevel: 0,
  internalRemark: '',
})
const feedbackDrafts = reactive<Record<number, string>>({})

/** 主题选项以后端 fieldMeta 为准，接口未下发时才用兜底，避免前端再存一份字典。 */
const topicOptions = computed(() => {
  const meta = section.value?.fieldMeta?.find((item) => item.key === 'topic')
  return meta?.options ?? [
    { value: 'ACADEMIC', label: '学业规划' },
    { value: 'CAREER', label: '就业指导' },
    { value: 'PSYCH', label: '心理疏导' },
    { value: 'DAILY', label: '日常交流' },
  ]
})

const statusLabel = computed(() =>
  section.value ? (STATUS_LABELS[section.value.status] ?? section.value.status) : '',
)
const completenessText = computed(
  () => `${Number(section.value?.completeness ?? 0).toFixed(2).replace(/\.00$/, '')}%`,
)
const canWithdraw = computed(() => isStudent.value && section.value?.status === 'SUBMITTED')
const withdrawUsed = computed(() => (section.value?.withdrawCount ?? 0) >= 1)
const canSubmit = computed(
  () => isStudent.value && (section.value?.status === 'DRAFT' || section.value?.status === 'RETURNED'),
)

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    section.value = await fetchMentorSection(studentId.value, academicYear.value)
  } catch (e) {
    error.value = isApiError(e) ? e : null
  } finally {
    loading.value = false
  }
}

async function onAppend(): Promise<void> {
  error.value = null
  notice.value = ''
  appending.value = true
  try {
    await appendMentorNote(studentId.value, {
      academicYear: academicYear.value,
      talkDate: draft.talkDate,
      topic: draft.topic,
      advice: draft.advice,
      starLevel: Number(draft.starLevel),
      internalRemark: draft.internalRemark,
    })
    notice.value = '谈话记录已追加。记录只可追加，发现有误请再追加一条更正记录。'
    draft.talkDate = today
    draft.topic = 'ACADEMIC'
    draft.advice = ''
    draft.starLevel = 0
    draft.internalRemark = ''
    await load()
  } catch (e) {
    error.value = isApiError(e) ? e : null
  } finally {
    appending.value = false
  }
}

async function onFeedback(note: MentorNoteVO): Promise<void> {
  error.value = null
  notice.value = ''
  feedbacking.value = note.id
  try {
    await submitMentorFeedback(studentId.value, note.id, {
      academicYear: academicYear.value,
      feedback: feedbackDrafts[note.id] ?? '',
    })
    notice.value = '反馈已提交，该条谈话不可再次反馈。'
    delete feedbackDrafts[note.id]
    await load()
  } catch (e) {
    error.value = isApiError(e) ? e : null
  } finally {
    feedbacking.value = null
  }
}

async function onSubmit(): Promise<void> {
  error.value = null
  notice.value = ''
  submitting.value = true
  try {
    await submitSection('mentor', studentId.value, academicYear.value)
    notice.value = '已提交，等待辅导员审核'
    await load()
  } catch (e) {
    error.value = isApiError(e) ? e : null
  } finally {
    submitting.value = false
  }
}

async function onWithdraw(): Promise<void> {
  error.value = null
  notice.value = ''
  withdrawing.value = true
  try {
    await withdrawSection('mentor', studentId.value, academicYear.value)
    notice.value = '已撤回，可继续补充后重新提交'
    await load()
  } catch (e) {
    error.value = isApiError(e) ? e : null
  } finally {
    withdrawing.value = false
  }
}

function stars(level: number): string {
  return level > 0 ? '★'.repeat(level) : '—'
}

load()
</script>

<template>
  <section class="mentor">
    <header class="mentor__head">
      <div>
        <h1 class="mentor__title">导师寄语</h1>
        <p class="mentor__summary">
          辅导员谈话记录时间线，按谈话日期倒序；每条记录学生可反馈一次。内部备注仅辅导员可见。
        </p>
      </div>
      <div class="mentor__state">
        <span class="mentor__completeness">完整度 {{ completenessText }}</span>
        <span v-if="section" class="tag" :class="`tag--${section.status}`">{{ statusLabel }}</span>
      </div>
    </header>

    <p v-if="notice" class="mentor__alert mentor__alert--success">{{ notice }}</p>
    <div v-if="error" class="mentor__alert mentor__alert--danger">
      <strong>{{ error.code }}</strong> {{ error.message }}
      <span v-if="error.traceId" class="mentor__trace">traceId：{{ error.traceId }}</span>
    </div>

    <p v-if="loading" class="mentor__loading">加载中…</p>

    <template v-else-if="section">
      <!-- 辅导员追加：只追加，不提供编辑/删除入口（BR-S07） -->
      <form v-if="section.canAppend" class="mentor__form" @submit.prevent="onAppend">
        <h2 class="mentor__subtitle">追加谈话记录</h2>
        <p class="mentor__hint">
          一行一次谈话，提交后不可修改或删除；发现有误时追加一条更正记录并注明「更正第 N 条」。
        </p>
        <div class="mentor__grid">
          <label class="mentor__field">
            <span>谈话日期 <em>*</em></span>
            <input v-model="draft.talkDate" type="date" :max="today" required />
          </label>
          <label class="mentor__field">
            <span>谈话主题 <em>*</em></span>
            <select v-model="draft.topic" required>
              <option v-for="option in topicOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </label>
          <label class="mentor__field mentor__field--wide">
            <span>建议与寄语 <em>*</em>（20–1000 字，学生可见）</span>
            <textarea v-model="draft.advice" rows="3" minlength="20" maxlength="1000" required />
          </label>
          <label class="mentor__field">
            <span>重要标记</span>
            <select v-model.number="draft.starLevel">
              <option :value="0">无</option>
              <option :value="1">★</option>
              <option :value="2">★★</option>
            </select>
          </label>
          <label class="mentor__field">
            <span>内部备注（仅辅导员，≤500 字）</span>
            <textarea v-model="draft.internalRemark" rows="2" maxlength="500" />
          </label>
        </div>
        <button class="mentor__btn mentor__btn--primary" type="submit" :disabled="appending">
          {{ appending ? '追加中…' : '追加记录' }}
        </button>
      </form>

      <h2 class="mentor__subtitle">谈话时间线（{{ section.notes.length }} 条）</h2>
      <p v-if="section.notes.length === 0" class="mentor__empty">
        暂无谈话记录。{{ isCounselor ? '可在上方追加第一条。' : '辅导员追加后会显示在这里。' }}
      </p>

      <ol class="mentor__timeline">
        <li v-for="note in section.notes" :key="note.id" class="mentor__card">
          <div class="mentor__card-head">
            <strong class="mentor__date">{{ note.talkDate }}</strong>
            <span class="mentor__topic">{{ note.topicLabel }}</span>
            <span class="mentor__stars">{{ stars(note.starLevel) }}</span>
            <span class="mentor__author">{{ note.authorLabel }}</span>
          </div>
          <p class="mentor__advice">{{ note.advice }}</p>

          <!-- 内部备注：学生端响应体里没有这个键，辅导员端才渲染 -->
          <p v-if="note.internalRemark" class="mentor__internal">
            内部备注：{{ note.internalRemark }}
          </p>

          <div v-if="note.feedback" class="mentor__feedback">
            <strong>学生反馈与承诺</strong>
            <p>{{ note.feedback }}</p>
            <small v-if="note.feedbackAt">提交于 {{ note.feedbackAt }}</small>
          </div>
          <form
            v-else-if="isStudent && note.canFeedback"
            class="mentor__feedback-form"
            @submit.prevent="onFeedback(note)"
          >
            <label>
              <span>我的反馈与承诺（≤500 字，提交后不可修改）</span>
              <textarea v-model="feedbackDrafts[note.id]" rows="2" maxlength="500" required />
            </label>
            <button class="mentor__btn" type="submit" :disabled="feedbacking === note.id">
              {{ feedbacking === note.id ? '提交中…' : '提交反馈' }}
            </button>
          </form>
          <p v-else-if="isStudent" class="mentor__locked">该条谈话已完成反馈</p>
        </li>
      </ol>

      <!-- 状态操作：与其余五板块同一套 submit / withdraw 端点 -->
      <div class="mentor__actions">
        <button
          v-if="canSubmit"
          class="mentor__btn mentor__btn--primary"
          type="button"
          :disabled="submitting"
          @click="onSubmit"
        >
          {{ submitting ? '提交中…' : '提交板块' }}
        </button>
        <button
          v-if="canWithdraw"
          class="mentor__btn"
          type="button"
          :disabled="withdrawing || withdrawUsed"
          @click="onWithdraw"
        >
          {{ withdrawing ? '撤回中…' : '撤回提交' }}
        </button>
        <span v-if="withdrawUsed" class="mentor__hint">撤回机会已用完（每板块一次）</span>
        <button class="mentor__btn mentor__btn--ghost" type="button" @click="load">重新加载</button>
      </div>
    </template>
  </section>
</template>

<style scoped>
.mentor { max-width: 920px; margin: 0 auto; padding: 24px 20px 48px; }
.mentor__head {
  display: flex; justify-content: space-between; align-items: flex-start; gap: 16px;
  padding-bottom: 16px; border-bottom: 1px solid var(--border, #e5e7eb);
}
.mentor__title { margin: 0 0 6px; font-size: 22px; }
.mentor__summary { margin: 0; color: var(--text-muted, #6b7280); font-size: 13px; line-height: 1.6; }
.mentor__state { display: flex; align-items: center; gap: 10px; white-space: nowrap; }
.mentor__completeness { font-size: 13px; color: var(--text-muted, #6b7280); }
.mentor__subtitle { margin: 24px 0 10px; font-size: 16px; }
.mentor__hint, .mentor__locked { color: var(--text-muted, #6b7280); font-size: 12px; line-height: 1.6; }
.mentor__alert { margin-top: 14px; padding: 10px 14px; border-radius: 6px; font-size: 13px; }
.mentor__alert--success { background: #ecfdf5; color: #047857; }
.mentor__alert--danger { background: #fef2f2; color: #b91c1c; }
.mentor__trace { display: block; margin-top: 4px; opacity: 0.75; }
.mentor__loading, .mentor__empty { padding: 28px 0; color: var(--text-muted, #6b7280); font-size: 13px; }
.mentor__form {
  padding: 16px; border: 1px solid var(--border, #e5e7eb); border-radius: 8px;
  background: var(--surface, #fff);
}
.mentor__form .mentor__subtitle { margin-top: 0; }
.mentor__grid {
  display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; margin: 12px 0;
}
.mentor__field { display: flex; flex-direction: column; gap: 6px; font-size: 13px; }
.mentor__field--wide { grid-column: 1 / -1; }
.mentor__field em { color: #dc2626; font-style: normal; }
.mentor__field input, .mentor__field select, .mentor__field textarea,
.mentor__feedback-form textarea {
  width: 100%; padding: 8px 10px; border: 1px solid var(--border, #d1d5db);
  border-radius: 6px; font: inherit; font-size: 13px; box-sizing: border-box;
  background: var(--surface, #fff); color: inherit;
}
.mentor__timeline { list-style: none; margin: 0; padding: 0; }
.mentor__card {
  position: relative; margin: 0 0 14px; padding: 16px 18px;
  border: 1px solid var(--border, #e5e7eb); border-left: 3px solid var(--primary, #2563eb);
  border-radius: 8px; background: var(--surface, #fff);
}
.mentor__card-head { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; margin-bottom: 10px; }
.mentor__date { font-size: 14px; }
.mentor__topic { padding: 2px 8px; border-radius: 999px; background: #eff6ff; color: #1d4ed8; font-size: 12px; }
.mentor__stars { color: #f59e0b; font-size: 13px; }
.mentor__author { margin-left: auto; color: var(--text-muted, #6b7280); font-size: 12px; }
.mentor__advice { margin: 0; font-size: 13px; line-height: 1.8; white-space: pre-wrap; }
.mentor__internal {
  margin: 10px 0 0; padding: 8px 10px; border-radius: 6px;
  background: #fffbeb; color: #92400e; font-size: 12px;
}
.mentor__feedback { margin-top: 12px; padding: 10px 12px; border-radius: 6px; background: #f9fafb; font-size: 12px; }
.mentor__feedback p { margin: 6px 0; font-size: 13px; line-height: 1.7; }
.mentor__feedback small { color: var(--text-muted, #6b7280); }
.mentor__feedback-form { display: flex; flex-direction: column; gap: 8px; margin-top: 12px; font-size: 12px; }
.mentor__feedback-form label { display: flex; flex-direction: column; gap: 6px; }
.mentor__actions { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; margin-top: 22px; }
.mentor__btn {
  padding: 8px 16px; border: 1px solid var(--border, #d1d5db); border-radius: 6px;
  background: var(--surface, #fff); color: inherit; font: inherit; font-size: 13px; cursor: pointer;
}
.mentor__btn:disabled { opacity: 0.55; cursor: not-allowed; }
.mentor__btn--primary { background: #2563eb; border-color: #2563eb; color: #fff; }
.mentor__btn--ghost { color: var(--text-muted, #6b7280); }
@media (max-width: 640px) {
  .mentor__grid { grid-template-columns: 1fr; }
  .mentor__head { flex-direction: column; }
}
</style>
