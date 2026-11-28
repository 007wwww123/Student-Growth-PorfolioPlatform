<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { archiveBatch, fetchArchiveCandidates } from '@/api/archive'
import { applyUnfreeze } from '@/api/unfreeze'
import { isApiError, type ApiError } from '@/api/http'
import type { ArchiveCandidate, ArchiveResult } from '@/api/types'
import { DEMO_ACADEMIC_YEAR, currentAcademicYear } from '@/sectionMeta'

/**
 * 毕业归档（S-1-11）。
 *
 * <p>页面的核心不是「点按钮」，而是让辅导员在点之前看清每个人的六板块完成情况。
 * 归档是终态：六板块转 LOCKED 后任何角色都不能再改内容，所以确认动作单独一步，
 * 且只允许勾选「可归档」的人。</p>
 *
 * <p>整批语义：名单内只要有一人未完成，整批都不执行（EX-12），
 * 结果区直接给出未完成清单，辅导员补齐后再提交。</p>
 */
const YEARS = Array.from(new Set([DEMO_ACADEMIC_YEAR, currentAcademicYear()]))

const academicYear = ref(DEMO_ACADEMIC_YEAR)
const classCode = ref('')
const candidates = ref<ArchiveCandidate[]>([])
const selected = ref<number[]>([])
const loading = ref(false)
const submitting = ref(false)
const error = ref<ApiError | null>(null)
const result = ref<ArchiveResult | null>(null)
const confirming = ref(false)
const unfreezeMessage = ref('')

const classOptions = computed(() => {
  const seen = new Map<string, string>()
  for (const item of candidates.value) {
    if (item.classCode) {
      seen.set(item.classCode, item.className ?? item.classCode)
    }
  }
  return [...seen.entries()].map(([code, name]) => ({ code, name }))
})

const selectable = computed(() =>
  candidates.value.filter((item) => item.archivable && !item.archived).map((item) => item.studentId),
)
const allSelected = computed(
  () => selectable.value.length > 0 && selected.value.length === selectable.value.length,
)
const archivableCount = computed(() => candidates.value.filter((item) => item.archivable && !item.archived).length)
const archivedCount = computed(() => candidates.value.filter((item) => item.archived).length)
const incompleteCount = computed(() => candidates.value.filter((item) => !item.archivable).length)

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  selected.value = []
  confirming.value = false
  try {
    candidates.value = await fetchArchiveCandidates(academicYear.value, classCode.value || undefined)
  } catch (e) {
    candidates.value = []
    error.value = isApiError(e) ? e : null
  } finally {
    loading.value = false
  }
}

function toggleAll(): void {
  selected.value = allSelected.value ? [] : [...selectable.value]
}

function completenessText(item: ArchiveCandidate): string {
  const done = item.approvedCount + item.lockedCount
  return `${done}/${6}`
}

function askConfirm(): void {
  if (selected.value.length === 0) {
    return
  }
  confirming.value = true
  result.value = null
}

async function submit(): Promise<void> {
  if (selected.value.length === 0 || submitting.value) {
    return
  }
  submitting.value = true
  error.value = null
  try {
    result.value = await archiveBatch({
      academicYear: academicYear.value,
      studentIds: [...selected.value],
    })
    confirming.value = false
    await load()
  } catch (e) {
    result.value = null
    error.value = isApiError(e) ? e : null
  } finally {
    submitting.value = false
  }
}

async function onUnfreeze(item: ArchiveCandidate): Promise<void> {
  const reason = window.prompt('请填写解冻原因（不少于 10 字）') ?? ''
  if (reason.trim().length < 10) {
    unfreezeMessage.value = '解冻原因不少于 10 字'
    return
  }
  try {
    await applyUnfreeze(item.studentId, academicYear.value, reason.trim())
    unfreezeMessage.value = '解冻申请已提交，等待管理员审批'
  } catch (e) {
    error.value = isApiError(e) ? e : null
  }
}

function incompleteNames(item: ArchiveCandidate): string {
  return item.incompleteSections.map((section) => section.sectionName).join('、')
}

watch([academicYear, classCode], () => {
  result.value = null
  void load()
})

onMounted(load)
</script>

<template>
  <section class="archive">
    <header class="archive__head">
      <div>
        <h1 class="archive__title">毕业归档</h1>
        <p class="archive__summary">
          六板块全部通过后整批转「已归档」，归档后内容锁定、不可再修改；存在未完成板块则整批不执行。
        </p>
      </div>
      <span class="archive__scope">仅本班</span>
    </header>

    <p v-if="unfreezeMessage" class="archive__summary">{{ unfreezeMessage }}</p>
    <div class="archive__toolbar">
      <label class="archive__select">
        学年
        <select v-model="academicYear">
          <option v-for="year in YEARS" :key="year" :value="year">
            {{ year }}{{ year === currentAcademicYear() ? '（当前）' : '' }}
          </option>
        </select>
      </label>

      <label class="archive__select">
        班级
        <select v-model="classCode">
          <option value="">全部班级</option>
          <option v-for="item in classOptions" :key="item.code" :value="item.code">
            {{ item.name }}
          </option>
        </select>
      </label>

      <p class="archive__hint">
        共 {{ candidates.length }} 人 · 可归档 {{ archivableCount }} · 已归档 {{ archivedCount }} · 未完成
        {{ incompleteCount }}
      </p>
    </div>

    <div v-if="error" class="alert alert--danger">
      <div class="alert__head">
        <strong>{{ error.code }}</strong>
        <span>{{ error.message }}</span>
      </div>
      <p v-if="error.code === 'EC-40302'" class="alert__tip">
        归档仅限辅导员本人带班学生；管理员为只读授权，可查看名单但不能归档。
      </p>
      <button type="button" class="alert__retry" @click="load">重试</button>
    </div>

    <p v-else-if="loading" class="archive__loading">加载中…</p>

    <div v-else-if="candidates.length === 0" class="archive__empty">
      <p>当前学年没有可归档的学生。</p>
      <p class="archive__empty-tip">
        学生名单来自学生主数据；班级的现任辅导员决定这里的可见范围。
      </p>
    </div>

    <template v-else>
      <div v-if="confirming" class="confirm">
        <div class="confirm__text">
          <strong>确认归档 {{ selected.length }} 名学生？</strong>
          <span>归档后六板块转为「已归档」，任何角色都不能再修改内容。</span>
        </div>
        <div class="confirm__actions">
          <button type="button" class="btn btn--ghost" :disabled="submitting" @click="confirming = false">
            取消
          </button>
          <button type="button" class="btn btn--primary" :disabled="submitting" @click="submit">
            {{ submitting ? '归档中…' : '确认归档' }}
          </button>
        </div>
      </div>

      <table class="table">
        <thead>
          <tr>
            <th class="table__check">
              <input
                type="checkbox"
                :checked="allSelected"
                :disabled="selectable.length === 0"
                aria-label="全选可归档学生"
                @change="toggleAll"
              />
            </th>
            <th>学号</th>
            <th>姓名</th>
            <th>班级</th>
            <th>已完成板块</th>
            <th>未完成板块</th>
            <th>状态</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in candidates" :key="item.studentId">
            <td class="table__check">
              <input
                v-model="selected"
                type="checkbox"
                :value="item.studentId"
                :disabled="!item.archivable || item.archived"
                :aria-label="`选择 ${item.studentName ?? item.studentId}`"
              />
            </td>
            <td>{{ item.studentNo ?? item.studentId }}</td>
            <td>{{ item.studentName ?? '—' }}</td>
            <td>{{ item.className ?? item.classCode ?? '—' }}</td>
            <td>{{ completenessText(item) }}</td>
            <td class="archive__missing" :title="incompleteNames(item)">
              {{ item.incompleteSections.length === 0 ? '—' : incompleteNames(item) }}
            </td>
            <td>
              <span v-if="item.archived" class="tag tag--LOCKED">已归档</span>
              <button v-if="item.archived" type="button" class="btn btn--small btn--ghost" @click="onUnfreeze(item)">
                申请解冻
              </button>
              <span v-else-if="item.archivable" class="tag tag--APPROVED">可归档</span>
              <span v-else class="tag tag--RETURNED">未完成 {{ item.incompleteSections.length }} 块</span>
            </td>
          </tr>
        </tbody>
      </table>

      <div class="archive__actions">
        <button
          type="button"
          class="btn btn--primary"
          :disabled="selected.length === 0 || submitting"
          @click="askConfirm"
        >
          批量归档{{ selected.length > 0 ? `（${selected.length}）` : '' }}
        </button>
      </div>
    </template>

    <section v-if="result" class="result" :class="{ 'result--rejected': result.rejected }">
      <header class="result__head">
        <h2 class="result__title">
          {{ result.academicYear }} 归档结果
          <span class="result__badge">{{ result.rejected ? '整批未执行' : '已完成' }}</span>
        </h2>
        <button type="button" class="result__close" aria-label="关闭结果" @click="result = null">×</button>
      </header>

      <p v-if="result.rejected" class="result__notice">
        名单中有 {{ result.students.length }} 人存在未完成板块，按整批拒绝规则未归档任何人（EX-12）。
        请补齐后重新提交。
      </p>
      <p v-else class="result__notice result__notice--ok">
        已归档 {{ result.archivedStudents }} 人 / {{ result.archivedProfiles }} 个板块实例<template
          v-if="result.skippedStudents > 0"
        >，另有 {{ result.skippedStudents }} 人此前已归档</template
        >。
      </p>

      <table class="table">
        <thead>
          <tr>
            <th>学号</th>
            <th>姓名</th>
            <th>班级</th>
            <th v-if="result.rejected">未完成板块</th>
            <th v-else>结果</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in result.students" :key="item.studentId">
            <td>{{ item.studentNo ?? item.studentId }}</td>
            <td>{{ item.studentName ?? '—' }}</td>
            <td>{{ item.className ?? '—' }}</td>
            <td v-if="result.rejected" class="archive__missing">
              {{ item.missingSections?.join('、') || '—' }}
            </td>
            <td v-else>
              <span class="tag tag--LOCKED">已归档</span>
            </td>
          </tr>
        </tbody>
      </table>
    </section>
  </section>
</template>

<style scoped>
.archive {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.archive__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.archive__title {
  margin: 0 0 4px;
  font-size: 19px;
  font-weight: 600;
}

.archive__summary {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 13px;
}

.archive__scope {
  flex: none;
  padding: 2px 10px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  color: var(--color-text-muted);
  font-size: 12px;
}

.archive__toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.archive__select {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--color-text-muted);
  font-size: 13px;
}

.archive__select select {
  padding: 5px 8px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: #fff;
}

.archive__hint {
  flex: 1;
  margin: 0;
  color: var(--color-text-muted);
  font-size: 12.5px;
}

.archive__loading {
  color: var(--color-text-muted);
}

.archive__empty {
  padding: 28px 20px;
  border: 1px dashed var(--color-border);
  border-radius: var(--radius);
  background: var(--color-surface);
  text-align: center;
}

.archive__empty p {
  margin: 0;
}

.archive__empty-tip {
  margin-top: 6px !important;
  color: var(--color-text-muted);
  font-size: 12.5px;
}

.table {
  width: 100%;
  border-collapse: collapse;
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  background: var(--color-surface);
  overflow: hidden;
  font-size: 13px;
}

.table th,
.table td {
  padding: 10px 12px;
  border-bottom: 1px solid var(--color-border);
  text-align: left;
  white-space: nowrap;
}

.table th {
  background: #f9fafb;
  color: var(--color-text-muted);
  font-weight: 500;
  font-size: 12.5px;
}

.table tbody tr:last-child td {
  border-bottom: none;
}

.table__check {
  width: 36px;
  text-align: center;
}

.archive__missing {
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  color: var(--color-text-muted);
  white-space: nowrap !important;
}

.archive__actions {
  display: flex;
  justify-content: flex-end;
}

.btn {
  padding: 6px 14px;
  border-radius: 6px;
  font-size: 13px;
}

.btn--primary {
  border: 1px solid var(--color-primary);
  background: var(--color-primary);
  color: #fff;
}

.btn--primary:disabled {
  border-color: var(--color-border);
  background: #f3f4f6;
  color: var(--color-text-muted);
  cursor: not-allowed;
}

.btn--ghost {
  border: 1px solid var(--color-border);
  background: #fff;
  color: var(--color-text);
}

.confirm {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 14px;
  border: 1px solid #fde68a;
  border-radius: var(--radius);
  background: var(--color-warning-bg);
  color: var(--color-warning);
  font-size: 13px;
}

.confirm__text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.confirm__text span {
  color: #92400e;
  font-size: 12.5px;
}

.confirm__actions {
  display: flex;
  flex: none;
  gap: 8px;
}

.result {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  background: var(--color-surface);
  box-shadow: var(--shadow);
}

.result--rejected {
  border-color: #fecaca;
}

.result__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.result__title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.result__badge {
  padding: 1px 8px;
  border-radius: 999px;
  background: var(--color-success-bg);
  color: var(--color-success);
  font-size: 12px;
  font-weight: 400;
}

.result--rejected .result__badge {
  background: var(--color-danger-bg);
  color: var(--color-danger);
}

.result__notice {
  margin: 0;
  color: var(--color-danger);
  font-size: 12.5px;
}

.result__notice--ok {
  color: var(--color-success);
}

.result__close {
  border: none;
  background: transparent;
  color: var(--color-text-muted);
  font-size: 18px;
  line-height: 1;
}

.alert {
  padding: 10px 12px;
  border-radius: 6px;
  font-size: 13px;
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
