<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { isApiError, type ApiError } from '@/api/http'
import { fetchReviewTasks, type ReviewTaskQuery } from '@/api/review'
import type { PageResult, ReviewTask, SectionCode } from '@/api/types'
import {
  DEMO_ACADEMIC_YEAR,
  SECTION_MAP,
  SECTIONS,
  STATUS_LABELS,
  currentAcademicYear,
} from '@/sectionMeta'

/**
 * 待审列表（S-1-06）。
 *
 * <p>列表只负责筛选、分页与跳转，打开详情走各板块详情接口，
 * 审核结论在详情页完成——避免在列表里做「不看内容直接通过」的操作。</p>
 *
 * <p>数据范围由后端按登录身份判定：辅导员只看本班，管理员需持临时授权。
 * 前端不做二次过滤，否则会出现「页面上看不到、接口其实能查到」的错觉。</p>
 */
const router = useRouter()

/** 学年必须显式传：后端缺省会取当前学年，跨学年的历史数据会查不到。 */
const YEARS = Array.from(new Set([DEMO_ACADEMIC_YEAR, currentAcademicYear()]))

const filters = ref<{ sectionCode: '' | SectionCode; status: string; academicYear: string }>({
  sectionCode: '',
  status: 'SUBMITTED',
  academicYear: DEMO_ACADEMIC_YEAR,
})

const page = ref<PageResult<ReviewTask> | null>(null)
const pageNum = ref(1)
const pageSize = ref(10)
const loading = ref(false)
const error = ref<ApiError | null>(null)
/** 待审 / 已通过 / 已退回 三个常用视图，切换时只改状态参数 */
const STATUS_TABS = [
  { value: 'SUBMITTED', label: '待审核' },
  { value: 'APPROVED', label: '已通过' },
  { value: 'RETURNED', label: '已退回' },
]

const records = computed(() => page.value?.records ?? [])
const total = computed(() => page.value?.total ?? 0)
const totalPages = computed(() => {
  const size = page.value?.pageSize ?? pageSize.value
  return Math.max(1, Math.ceil(total.value / size))
})

const currentStatusLabel = computed(
  () => STATUS_TABS.find((tab) => tab.value === filters.value.status)?.label ?? '全部',
)

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  const query: ReviewTaskQuery = {
    status: filters.value.status,
    academicYear: filters.value.academicYear,
    pageNum: pageNum.value,
    pageSize: pageSize.value,
  }
  if (filters.value.sectionCode) {
    query.sectionCode = filters.value.sectionCode
  }
  try {
    page.value = await fetchReviewTasks(query)
  } catch (e) {
    page.value = null
    error.value = isApiError(e) ? e : null
  } finally {
    loading.value = false
  }
}

function changeFilter(): void {
  pageNum.value = 1
  void load()
}

function goPage(next: number): void {
  if (next < 1 || next > totalPages.value) {
    return
  }
  pageNum.value = next
  void load()
}

function openTask(task: ReviewTask): void {
  const code = task.sectionCode.toLowerCase() as SectionCode
  void router.push({
    name: 'review-detail',
    params: { code, studentId: task.studentId },
    query: {
      academicYear: task.academicYear,
      // 姓名与班级只用于详情页标题，不作为数据来源
      ...(task.studentName ? { name: task.studentName } : {}),
      ...(task.className ? { className: task.className } : {}),
    },
  })
}

function sectionLabel(code: string): string {
  const meta = SECTION_MAP[code.toLowerCase() as SectionCode]
  return meta?.name ?? code
}

function formatTime(value?: string): string {
  if (!value) {
    return '—'
  }
  return value.replace('T', ' ').slice(0, 16)
}

function completenessText(value?: number): string {
  if (value == null) {
    return '—'
  }
  return `${Number(value).toFixed(2).replace(/\.00$/, '')}%`
}

watch(() => filters.value.status, changeFilter)

onMounted(load)
</script>

<template>
  <section class="review">
    <header class="review__head">
      <div>
        <h1 class="review__title">审核工作台</h1>
        <p class="review__summary">
          按班级与板块查看学生提交的档案，打开详情后通过或退回；退回理由学生可见。
        </p>
      </div>
      <span class="review__scope">仅本班数据</span>
    </header>

    <div class="review__filters">
      <div class="tabs">
        <button
          v-for="tab in STATUS_TABS"
          :key="tab.value"
          type="button"
          class="tabs__item"
          :class="{ 'is-active': filters.status === tab.value }"
          @click="filters.status = tab.value"
        >
          {{ tab.label }}
        </button>
      </div>

      <label class="review__select">
        板块
        <select v-model="filters.sectionCode" @change="changeFilter">
          <option value="">全部板块</option>
          <option v-for="section in SECTIONS" :key="section.code" :value="section.code">
            {{ section.name }}
          </option>
        </select>
      </label>

      <label class="review__select">
        学年
        <select v-model="filters.academicYear" @change="changeFilter">
          <option v-for="year in YEARS" :key="year" :value="year">
            {{ year }}{{ year === currentAcademicYear() ? '（当前）' : '' }}
          </option>
        </select>
      </label>
    </div>

    <div v-if="error" class="alert alert--danger">
      <div class="alert__head">
        <strong>{{ error.code }}</strong>
        <span>{{ error.message }}</span>
      </div>
      <p v-if="error.code === 'EC-40302'" class="alert__tip">
        当前身份不是辅导员，或该班级不在你的负责范围内。可在页面顶部切换演示身份。
      </p>
      <button type="button" class="alert__retry" @click="load">重试</button>
    </div>

    <p v-else-if="loading" class="review__loading">加载中…</p>

    <template v-else>
      <p class="review__count">
        {{ currentStatusLabel }} · 共 {{ total }} 条
      </p>

      <div v-if="records.length === 0" class="review__empty">
        <p>当前没有{{ currentStatusLabel }}的档案。</p>
        <p class="review__empty-tip">
          学生提交后会自动进入这里；如果刚提交仍为空，确认学年、身份是否为该班辅导员。
        </p>
      </div>

      <table v-else class="table">
        <thead>
          <tr>
            <th>学生</th>
            <th>班级</th>
            <th>板块</th>
            <th>学年</th>
            <th>完成度</th>
            <th>次数</th>
            <th>提交时间</th>
            <th>状态</th>
            <th class="table__action-head">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="task in records" :key="`${task.sectionCode}-${task.studentId}`">
            <td>{{ task.studentName ?? `学生 ${task.studentId}` }}</td>
            <td>{{ task.className ?? task.classId ?? '—' }}</td>
            <td>{{ task.sectionName ?? sectionLabel(task.sectionCode) }}</td>
            <td>{{ task.academicYear }}</td>
            <td>{{ completenessText(task.completeness) }}</td>
            <td>{{ task.submitCount ?? 0 }}</td>
            <td>{{ formatTime(task.submittedAt) }}</td>
            <td>
              <span class="tag" :class="`tag--${task.status}`">
                {{ task.statusLabel ?? STATUS_LABELS[task.status] ?? task.status }}
              </span>
            </td>
            <td>
              <button type="button" class="table__link" @click="openTask(task)">
                查看详情
              </button>
            </td>
          </tr>
        </tbody>
      </table>

      <div v-if="total > pageSize" class="pager">
        <button type="button" class="pager__btn" :disabled="pageNum <= 1" @click="goPage(pageNum - 1)">
          上一页
        </button>
        <span class="pager__info">{{ pageNum }} / {{ totalPages }}</span>
        <button
          type="button"
          class="pager__btn"
          :disabled="pageNum >= totalPages"
          @click="goPage(pageNum + 1)"
        >
          下一页
        </button>
      </div>
    </template>
  </section>
</template>

<style scoped>
.review {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.review__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.review__title {
  margin: 0 0 4px;
  font-size: 19px;
  font-weight: 600;
}

.review__summary {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 13px;
}

.review__scope {
  flex: none;
  padding: 2px 10px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  color: var(--color-text-muted);
  font-size: 12px;
}

.review__filters {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.tabs {
  display: flex;
  gap: 4px;
  padding: 3px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
}

.tabs__item {
  padding: 4px 12px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--color-text-muted);
  font-size: 13px;
}

.tabs__item.is-active {
  background: #eff6ff;
  color: var(--color-primary);
  font-weight: 500;
}

.review__select {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--color-text-muted);
  font-size: 13px;
}

.review__select select {
  padding: 5px 8px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: #fff;
}

.review__count {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 12.5px;
}

.review__loading {
  color: var(--color-text-muted);
}

.review__empty {
  padding: 28px 20px;
  border: 1px dashed var(--color-border);
  border-radius: var(--radius);
  background: var(--color-surface);
  text-align: center;
}

.review__empty p {
  margin: 0;
}

.review__empty-tip {
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

.table__action-head {
  text-align: right;
}

.table__link {
  display: block;
  margin-left: auto;
  padding: 3px 10px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: #fff;
  color: var(--color-primary);
  font-size: 12.5px;
}

.table__link:hover {
  border-color: #bfdbfe;
  background: #eff6ff;
}

.pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
}

.pager__btn {
  padding: 5px 12px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: #fff;
  font-size: 13px;
}

.pager__btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.pager__info {
  color: var(--color-text-muted);
  font-size: 12.5px;
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
