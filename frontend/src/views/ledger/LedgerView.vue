<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { isApiError, type ApiError } from '@/api/http'
import { fetchLedgerClasses, fetchLedgerTasks } from '@/api/ledger'
import type {
  LedgerClassRow,
  LedgerRow,
  LedgerStatus,
  LedgerTaskQuery,
  PageResult,
  SectionCode,
} from '@/api/types'
import { DEMO_ACADEMIC_YEAR, currentAcademicYear } from '@/sectionMeta'

/**
 * 班级台账（F-07 / AC-020）。
 *
 * <p>页面分两层：顶部是「班级完成度」汇总（整班完成率、建档/未建档、待审/退回/草稿），
 * 下方是可按班级、状态、关键字筛选的明细。数据范围完全由后端按登录身份收口：
 * 辅导员只返回带班，管理员需持临时授权，前端不做二次过滤。</p>
 *
 * <p>明细一行 = 一个学生 × 一个板块档案；未建档学生也会出现一行（板块为空），
 * 因此不能假设每行都有 sectionCode，跳转审核前必须判空。</p>
 */
const router = useRouter()

/** 学年必须显式传：后端缺省会取当前学年，跨学年的历史数据会查不到。 */
const YEARS = Array.from(new Set([DEMO_ACADEMIC_YEAR, currentAcademicYear()]))

/** 状态页签，顺序按需求：全部 / 草稿 / 待审核 / 已通过 / 已退回 / 已归档 / 未建档。 */
const STATUS_TABS: Array<{ value: LedgerStatus; label: string }> = [
  { value: 'ALL', label: '全部' },
  { value: 'DRAFT', label: '草稿' },
  { value: 'SUBMITTED', label: '待审核' },
  { value: 'APPROVED', label: '已通过' },
  { value: 'RETURNED', label: '已退回' },
  { value: 'LOCKED', label: '已归档' },
  { value: 'UNARCHIVED', label: '未建档' },
]

const academicYear = ref(DEMO_ACADEMIC_YEAR)
const classCode = ref('')
const status = ref<LedgerStatus>('ALL')
const keyword = ref('')
const pageNum = ref(1)
const pageSize = ref(10)

const classes = ref<LedgerClassRow[]>([])
const classesLoading = ref(false)
const classesError = ref<ApiError | null>(null)

const page = ref<PageResult<LedgerRow> | null>(null)
const loading = ref(false)
const error = ref<ApiError | null>(null)

const records = computed(() => page.value?.records ?? [])
const total = computed(() => page.value?.total ?? 0)
const totalPages = computed(() => {
  const size = page.value?.pageSize ?? pageSize.value
  return Math.max(1, Math.ceil(total.value / size))
})

const currentStatusLabel = computed(
  () => STATUS_TABS.find((tab) => tab.value === status.value)?.label ?? '全部',
)

/**
 * 顶部合计行：分母用各班在册学生数之和。
 *
 * 一个学生只属于一个班，直接求和不会重复计数；完成率用合计完成人数 / 合计在册人数，
 * 而不是「各班完成率的平均值」——后者会让小班与大班等权，口径就错了。
 */
const summary = computed(() => {
  let studentTotal = 0
  let archivedStudents = 0
  let unarchivedStudents = 0
  let completedStudents = 0
  let pendingProfiles = 0
  let returnedProfiles = 0
  let draftProfiles = 0
  for (const row of classes.value) {
    studentTotal += row.studentTotal
    archivedStudents += row.archivedStudents
    unarchivedStudents += row.unarchivedStudents
    completedStudents += row.completedStudents
    pendingProfiles += row.pendingProfiles
    returnedProfiles += row.returnedProfiles
    draftProfiles += row.draftProfiles
  }
  const completionRate =
    studentTotal > 0 ? Number(((completedStudents * 100) / studentTotal).toFixed(1)) : 0
  return {
    studentTotal,
    archivedStudents,
    unarchivedStudents,
    completedStudents,
    pendingProfiles,
    returnedProfiles,
    draftProfiles,
    completionRate,
  }
})

async function loadClasses(): Promise<void> {
  classesLoading.value = true
  classesError.value = null
  try {
    classes.value = await fetchLedgerClasses(academicYear.value)
  } catch (e) {
    classes.value = []
    classesError.value = isApiError(e) ? e : null
  } finally {
    classesLoading.value = false
  }
}

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  const query: LedgerTaskQuery = {
    academicYear: academicYear.value,
    status: status.value,
    pageNum: pageNum.value,
    pageSize: pageSize.value,
  }
  if (classCode.value) {
    query.classCode = classCode.value
  }
  if (keyword.value.trim()) {
    query.keyword = keyword.value.trim()
  }
  try {
    page.value = await fetchLedgerTasks(query)
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

/** 学年变化要同时刷新汇总与明细，并清掉已不属于该学年的班级筛选。 */
function onYearChange(): void {
  classCode.value = ''
  pageNum.value = 1
  void loadClasses()
  void load()
}

function search(): void {
  changeFilter()
}

function goPage(next: number): void {
  if (next < 1 || next > totalPages.value) {
    return
  }
  pageNum.value = next
  void load()
}

/** 仅待审核行可以进入审核页；其他状态没有可执行动作，不给入口。 */
function canReview(row: LedgerRow): boolean {
  return row.status === 'SUBMITTED' && !!row.sectionCode
}

function openReview(row: LedgerRow): void {
  if (!canReview(row) || !row.sectionCode) {
    return
  }
  const code = row.sectionCode.toLowerCase() as SectionCode
  void router.push({
    name: 'review-detail',
    params: { code, studentId: row.studentId },
    query: {
      academicYear: academicYear.value,
      // 姓名与班级只用于详情页标题，不作为数据来源
      ...(row.studentName ? { name: row.studentName } : {}),
      ...(row.className ? { className: row.className } : {}),
    },
  })
}

function formatTime(value?: string | null): string {
  if (!value) {
    return '—'
  }
  return value.replace('T', ' ').slice(0, 16)
}

function reviewInfo(row: LedgerRow): string {
  const time = formatTime(row.reviewedAt)
  if (time === '—') {
    return '—'
  }
  return row.reviewerName ? `${time} · ${row.reviewerName}` : time
}

function completenessText(value?: number): string {
  if (value == null) {
    return '—'
  }
  return `${Number(value).toFixed(2).replace(/\.00$/, '')}%`
}

function rateText(value?: number): string {
  return `${Number(value ?? 0).toFixed(1)}%`
}

watch(() => status.value, changeFilter)

onMounted(() => {
  void loadClasses()
  void load()
})
</script>

<template>
  <section class="ledger">
    <header class="ledger__head">
      <div>
        <h1 class="ledger__title">班级台账</h1>
        <p class="ledger__summary-text">
          按班级查看整班完成度，按状态筛选到具体学生与板块；未建档学生也会单独列出。
        </p>
      </div>
      <span class="ledger__scope">数据范围由服务端按身份限定</span>
    </header>

    <section class="ledger__panel">
      <div class="ledger__panel-head">
        <h2 class="ledger__panel-title">班级完成度</h2>
        <label class="ledger__select">
          学年
          <select v-model="academicYear" @change="onYearChange">
            <option v-for="year in YEARS" :key="year" :value="year">
              {{ year }}{{ year === currentAcademicYear() ? '（当前）' : '' }}
            </option>
          </select>
        </label>
      </div>

      <p v-if="classesError" class="alert alert--danger">
        <span>{{ classesError.code }}</span>
        <span>{{ classesError.message }}</span>
      </p>
      <p v-else-if="classesLoading" class="ledger__loading">加载中…</p>
      <p v-else-if="classes.length === 0" class="ledger__empty-line">
        当前范围内还没有班级；若是辅导员，请确认管理员已把你绑定为班主任。
      </p>
      <div v-else class="ledger__table-wrap">
        <table class="table table--summary">
          <thead>
            <tr>
              <th>班级</th>
              <th class="num">在册</th>
              <th class="num">已建档</th>
              <th class="num">未建档</th>
              <th class="num">完成人数</th>
              <th class="num">整班完成度</th>
              <th class="num">待审</th>
              <th class="num">退回</th>
              <th class="num">草稿</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in classes" :key="row.classCode">
              <td>{{ row.className ?? row.classCode }}</td>
              <td class="num">{{ row.studentTotal }}</td>
              <td class="num">{{ row.archivedStudents }}</td>
              <td class="num" :class="{ 'is-warn': row.unarchivedStudents > 0 }">
                {{ row.unarchivedStudents }}
              </td>
              <td class="num">{{ row.completedStudents }}</td>
              <td class="num">{{ rateText(row.completionRate) }}</td>
              <td class="num">{{ row.pendingProfiles }}</td>
              <td class="num">{{ row.returnedProfiles }}</td>
              <td class="num">{{ row.draftProfiles }}</td>
            </tr>
          </tbody>
          <tfoot>
            <tr>
              <td>合计</td>
              <td class="num">{{ summary.studentTotal }}</td>
              <td class="num">{{ summary.archivedStudents }}</td>
              <td class="num">{{ summary.unarchivedStudents }}</td>
              <td class="num">{{ summary.completedStudents }}</td>
              <td class="num">{{ rateText(summary.completionRate) }}</td>
              <td class="num">{{ summary.pendingProfiles }}</td>
              <td class="num">{{ summary.returnedProfiles }}</td>
              <td class="num">{{ summary.draftProfiles }}</td>
            </tr>
          </tfoot>
        </table>
      </div>
    </section>

    <div class="ledger__filters">
      <div class="tabs">
        <button
          v-for="tab in STATUS_TABS"
          :key="tab.value"
          type="button"
          class="tabs__item"
          :class="{ 'is-active': status === tab.value }"
          @click="status = tab.value"
        >
          {{ tab.label }}
        </button>
      </div>

      <div class="ledger__filter-right">
        <label class="ledger__select">
          班级
          <select v-model="classCode" @change="changeFilter">
            <option value="">全部班级</option>
            <option v-for="row in classes" :key="row.classCode" :value="row.classCode">
              {{ row.className ?? row.classCode }}
            </option>
          </select>
        </label>
        <label class="ledger__search">
          <input
            v-model="keyword"
            type="search"
            placeholder="姓名或学号"
            @keyup.enter="search"
          />
          <button type="button" class="ledger__search-btn" @click="search">搜索</button>
        </label>
      </div>
    </div>

    <div v-if="error" class="alert alert--danger">
      <div class="alert__head">
        <strong>{{ error.code }}</strong>
        <span>{{ error.message }}</span>
      </div>
      <p v-if="error.code === 'EC-40302'" class="alert__tip">
        当前身份不在该班级的数据范围内：辅导员只能查看所带班级，管理员需持临时授权。
      </p>
      <button type="button" class="alert__retry" @click="load">重试</button>
    </div>

    <p v-else-if="loading" class="ledger__loading">加载中…</p>

    <template v-else>
      <p class="ledger__count">{{ currentStatusLabel }} · 共 {{ total }} 条</p>

      <div v-if="records.length === 0" class="ledger__empty">
        <p>当前筛选下没有台账记录。</p>
        <p class="ledger__empty-tip">
          「待审核」只列学生已提交的板块；「未建档」列本学年还没有任何板块档案的学生。
        </p>
      </div>

      <table v-else class="table">
        <thead>
          <tr>
            <th>学生</th>
            <th>班级</th>
            <th>板块</th>
            <th>状态</th>
            <th>完成度</th>
            <th>提交时间</th>
            <th>审核时间 / 审核人</th>
            <th class="table__action-head">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="row in records"
            :key="`${row.studentId}-${row.sectionCode ?? 'UNARCHIVED'}`"
          >
            <td>
              <div class="ledger__student">
                <span>{{ row.studentName || `学生 ${row.studentId}` }}</span>
                <small>{{ row.studentNo || '—' }}</small>
              </div>
            </td>
            <td>{{ row.className ?? row.classCode ?? '—' }}</td>
            <td>{{ row.sectionName ?? '—' }}</td>
            <td>
              <span class="tag" :class="`tag--${row.status}`">
                {{ row.statusLabel ?? row.status }}
              </span>
            </td>
            <td>{{ completenessText(row.completeness) }}</td>
            <td>{{ formatTime(row.submittedAt) }}</td>
            <td>{{ reviewInfo(row) }}</td>
            <td>
              <button
                v-if="canReview(row)"
                type="button"
                class="table__link"
                @click="openReview(row)"
              >
                去审核
              </button>
              <span v-else class="ledger__muted">—</span>
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
.ledger {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.ledger__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.ledger__title {
  margin: 0 0 4px;
  font-size: 19px;
  font-weight: 600;
}

.ledger__summary-text {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 13px;
}

.ledger__scope {
  flex: none;
  padding: 2px 10px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  color: var(--color-text-muted);
  font-size: 12px;
}

.ledger__panel {
  padding: 14px 16px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  background: var(--color-surface);
  box-shadow: var(--shadow);
}

.ledger__panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.ledger__panel-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.ledger__table-wrap {
  overflow-x: auto;
}

.ledger__filters {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.ledger__filter-right {
  display: flex;
  align-items: center;
  gap: 10px;
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

.ledger__select {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--color-text-muted);
  font-size: 13px;
}

.ledger__select select {
  padding: 5px 8px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: #fff;
}

.ledger__search {
  display: flex;
  align-items: center;
  gap: 6px;
}

.ledger__search input {
  width: 150px;
  padding: 5px 8px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: #fff;
  font-size: 13px;
}

.ledger__search-btn {
  padding: 5px 12px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: #fff;
  color: var(--color-primary);
  font-size: 13px;
}

.ledger__search-btn:hover {
  border-color: #bfdbfe;
  background: #eff6ff;
}

.ledger__count {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 12.5px;
}

.ledger__loading {
  color: var(--color-text-muted);
}

.ledger__empty-line {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 13px;
}

.ledger__empty {
  padding: 28px 20px;
  border: 1px dashed var(--color-border);
  border-radius: var(--radius);
  background: var(--color-surface);
  text-align: center;
}

.ledger__empty p {
  margin: 0;
}

.ledger__empty-tip {
  margin-top: 6px !important;
  color: var(--color-text-muted);
  font-size: 12.5px;
}

.ledger__student {
  display: flex;
  flex-direction: column;
  line-height: 1.35;
}

.ledger__student small {
  color: var(--color-text-muted);
  font-size: 12px;
}

.ledger__muted {
  color: var(--color-text-muted);
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

.table tfoot td {
  border-top: 1px solid var(--color-border);
  border-bottom: none;
  background: #f9fafb;
  font-weight: 500;
}

.table--summary {
  border: none;
}

.table--summary tbody tr:last-child td {
  border-bottom: 1px solid var(--color-border);
}

.num {
  text-align: right;
}

.is-warn {
  color: var(--color-warning);
  font-weight: 500;
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

/* 未建档不是状态机的五态之一，补一个与「草稿」区分开的灰色标签 */
.tag--UNARCHIVED {
  color: #92400e;
  background: var(--color-warning-bg);
  border-color: #fde68a;
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
