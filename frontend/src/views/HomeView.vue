<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { fetchDashboardOverview } from '@/api/dashboard'
import { isApiError } from '@/api/http'
import type { ClassProgress, DashboardOverview, SectionProgress } from '@/api/types'
import { getUser } from '@/auth/session'
import { DEMO_ACADEMIC_YEAR, SECTIONS } from '@/sectionMeta'

/**
 * 总览页。
 *
 * <p>辅导员与持临时授权的管理员看到的是本学年进度聚合：学生数、完成数、
 * 切审量、六板块状态分布与班级完成率；学生仍然只看六个板块入口——
 * 学生本人进度在各板块页面与审核详情的完成标记里，重复一套聚合只会带来两份口径。</p>
 *
 * <p>聚合数字全部由服务端算好，页面只做百分比换算用于条形宽度，
 * 不在这里重新累加或推导完成率。</p>
 */
const user = getUser()

const canSeeOverview = computed(
  () => user?.role === 'COU' || (user?.role === 'ADMIN' && user?.tempGrant === true),
)

const introDesc = computed(() => {
  if (canSeeOverview.value) {
    return `${DEMO_ACADEMIC_YEAR} 学年进度：建档规模、六板块完成情况与班级完成率，数据按你的带班范围统计。`
  }
  if (user?.role === 'ADMIN') {
    return '管理员默认无业务数据权限，申请临时授权后可在这里查看看板；当前可浏览六大模块入口。'
  }
  return '从这里进入六大档案模块，按板块填写、提交并查看审核结果。'
})

const overview = ref<DashboardOverview | null>(null)
const loading = ref(false)
const error = ref('')

async function load(): Promise<void> {
  if (!canSeeOverview.value) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    overview.value = await fetchDashboardOverview(DEMO_ACADEMIC_YEAR)
  } catch (e) {
    overview.value = null
    error.value = isApiError(e) ? `${e.message}（${e.code}）` : '看板数据加载失败'
  } finally {
    loading.value = false
  }
}

/** 条形宽度：把「某状态条数 / 该板块建档数」换算成百分比，分母为 0 时不画。 */
function widthOf(part: number, total: number): string {
  if (!total) {
    return '0%'
  }
  return `${Math.round((part / total) * 1000) / 10}%`
}

function rateText(value: number | undefined): string {
  return `${Number(value ?? 0).toFixed(1)}%`
}

/** 板块完成段 = 已通过 + 已归档；两段同色，因为对「是否完成」它们是同一含义。 */
function doneOf(section: SectionProgress): number {
  return section.approved + section.locked
}

const kpis = computed(() => {
  const data = overview.value
  if (!data) {
    return []
  }
  return [
    { key: 'students', label: '建档学生', value: String(data.studentTotal), unit: '人' },
    { key: 'completed', label: '六板块完成', value: String(data.completedStudents), unit: '人' },
    { key: 'rate', label: '完成率', value: rateText(data.completionRate), unit: '' },
    { key: 'pending', label: '待审核板块', value: String(data.pendingReview), unit: '个' },
    { key: 'returned', label: '待修改板块', value: String(data.returnedProfiles), unit: '个' },
    { key: 'draft', label: '草稿板块', value: String(data.draftProfiles), unit: '个' },
  ]
})

const classes = computed<ClassProgress[]>(() => overview.value?.classes ?? [])

onMounted(load)
</script>

<template>
  <section>
    <div class="intro">
      <h1 class="intro__title">总览</h1>
      <p class="intro__desc">{{ introDesc }}</p>
    </div>

    <template v-if="canSeeOverview">
      <p v-if="loading" class="hint">看板数据加载中…</p>
      <div v-else-if="error" class="alert">
        <div class="alert__head">
          <strong>看板暂不可用</strong>
          <span>{{ error }}</span>
        </div>
        <button type="button" class="alert__retry" @click="load">重试</button>
      </div>

      <template v-else-if="overview">
        <ul class="kpis">
          <li v-for="item in kpis" :key="item.key" class="kpi">
            <span class="kpi__label">{{ item.label }}</span>
            <span class="kpi__value">
              {{ item.value }}<small v-if="item.unit" class="kpi__unit">{{ item.unit }}</small>
            </span>
          </li>
        </ul>

        <div class="panel">
          <div class="panel__head">
            <h2 class="panel__title">六板块进度</h2>
            <span class="panel__scope">{{ overview.scopeLabel }} · {{ overview.academicYear }}</span>
          </div>
          <ul class="bars">
            <li v-for="section in overview.sections" :key="section.sectionCode" class="bars__row">
              <span class="bars__name">{{ section.sectionName }}</span>
              <span class="bars__track">
                <span
                  v-if="section.draft"
                  class="bars__seg bars__seg--draft"
                  :style="{ width: widthOf(section.draft, section.studentCount) }"
                  :title="`草稿 ${section.draft}`"
                />
                <span
                  v-if="section.returned"
                  class="bars__seg bars__seg--returned"
                  :style="{ width: widthOf(section.returned, section.studentCount) }"
                  :title="`已退回 ${section.returned}`"
                />
                <span
                  v-if="section.submitted"
                  class="bars__seg bars__seg--submitted"
                  :style="{ width: widthOf(section.submitted, section.studentCount) }"
                  :title="`待审核 ${section.submitted}`"
                />
                <span
                  v-if="doneOf(section)"
                  class="bars__seg bars__seg--done"
                  :style="{ width: widthOf(doneOf(section), section.studentCount) }"
                  :title="`已完成 ${doneOf(section)}`"
                />
                <span v-if="!section.studentCount" class="bars__empty">未建档</span>
              </span>
              <span class="bars__value">{{ rateText(section.completionRate) }}</span>
              <span class="bars__count">{{ doneOf(section) }} / {{ section.studentCount }}</span>
            </li>
          </ul>
          <ul class="legend">
            <li class="legend__item"><i class="legend__dot legend__dot--draft" />草稿</li>
            <li class="legend__item"><i class="legend__dot legend__dot--returned" />已退回</li>
            <li class="legend__item"><i class="legend__dot legend__dot--submitted" />待审核</li>
            <li class="legend__item"><i class="legend__dot legend__dot--done" />已完成（通过 / 归档）</li>
          </ul>
        </div>

        <div class="panel">
          <div class="panel__head">
            <h2 class="panel__title">班级完成率</h2>
            <span class="panel__scope">有档案的学生计入分母</span>
          </div>
          <p v-if="!classes.length" class="hint">本学年范围内还没有建档数据。</p>
          <ul v-else class="bars">
            <li v-for="row in classes" :key="row.classCode" class="bars__row">
              <span class="bars__name">{{ row.className || row.classCode }}</span>
              <span class="bars__track">
                <span
                  v-if="row.completedStudents"
                  class="bars__seg bars__seg--done"
                  :style="{ width: widthOf(row.completedStudents, row.studentTotal) }"
                  :title="`完成 ${row.completedStudents}`"
                />
              </span>
              <span class="bars__value">{{ rateText(row.completionRate) }}</span>
              <span class="bars__count">
                {{ row.completedStudents }} / {{ row.studentTotal }} 人
                <em v-if="row.pendingReview" class="bars__pending">待审 {{ row.pendingReview }}</em>
              </span>
            </li>
          </ul>
        </div>
      </template>
    </template>

    <h2 class="section-title">六个板块入口</h2>
    <ul class="cards">
      <li v-for="(section, index) in SECTIONS" :key="section.code" class="cards__item">
        <RouterLink :to="`/sections/${section.code}`" class="card">
          <span class="card__index">{{ index + 1 }}</span>
          <span class="card__body">
            <span class="card__name">{{ section.name }}</span>
            <span class="card__summary">{{ section.summary }}</span>
            <span class="card__code">/api/sections/{{ section.code }}</span>
          </span>
        </RouterLink>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.intro {
  margin-bottom: 20px;
}

.intro__title {
  margin: 0 0 6px;
  font-size: 20px;
  font-weight: 600;
}

.intro__desc {
  margin: 0;
  color: var(--color-text-muted);
}

.hint {
  margin: 0 0 14px;
  color: var(--color-text-muted);
  font-size: 13px;
}

.kpis {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 10px;
  margin: 0 0 14px;
  padding: 0;
  list-style: none;
}

.kpi {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px 16px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  background: var(--color-surface);
}

.kpi__label {
  color: var(--color-text-muted);
  font-size: 12px;
}

.kpi__value {
  font-size: 22px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.kpi__unit {
  margin-left: 4px;
  color: var(--color-text-muted);
  font-size: 12px;
  font-weight: 400;
}

.panel {
  margin-bottom: 14px;
  padding: 16px 18px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  background: var(--color-surface);
}

.panel__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.panel__title {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
}

.panel__scope {
  color: var(--color-text-muted);
  font-size: 12px;
}

.bars {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.bars__row {
  display: grid;
  grid-template-columns: 84px minmax(0, 1fr) 56px 96px;
  align-items: center;
  gap: 10px;
  font-size: 12.5px;
}

.bars__name {
  color: var(--color-text);
}

.bars__track {
  display: flex;
  align-items: center;
  height: 10px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--color-bg);
}

.bars__seg {
  height: 100%;
}

.bars__seg--draft {
  background: #cbd5e1;
}

.bars__seg--returned {
  background: #ef4444;
}

.bars__seg--submitted {
  background: #f59e0b;
}

.bars__seg--done {
  background: #10b981;
}

.bars__empty {
  padding-left: 8px;
  color: var(--color-text-muted);
  font-size: 11.5px;
}

.bars__value {
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.bars__count {
  color: var(--color-text-muted);
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.bars__pending {
  margin-left: 6px;
  color: #b45309;
  font-style: normal;
}

.legend {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  margin: 12px 0 0;
  padding: 0;
  list-style: none;
  color: var(--color-text-muted);
  font-size: 11.5px;
}

.legend__item {
  display: flex;
  align-items: center;
  gap: 5px;
}

.legend__dot {
  width: 8px;
  height: 8px;
  border-radius: 2px;
}

.legend__dot--draft {
  background: #cbd5e1;
}

.legend__dot--returned {
  background: #ef4444;
}

.legend__dot--submitted {
  background: #f59e0b;
}

.legend__dot--done {
  background: #10b981;
}

.section-title {
  margin: 22px 0 12px;
  font-size: 14px;
  font-weight: 600;
}

.alert {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin: 0 0 14px;
  padding: 10px 12px;
  border: 1px solid #fecaca;
  border-radius: 6px;
  background: var(--color-danger-bg);
  color: var(--color-danger);
  font-size: 13px;
}

.alert__head {
  display: flex;
  gap: 8px;
}

.alert__retry {
  align-self: flex-start;
  margin-top: 2px;
  padding: 4px 10px;
  border: 1px solid #fecaca;
  border-radius: 6px;
  background: #fff;
  color: var(--color-danger);
  font-size: 12.5px;
}

.cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.card {
  display: flex;
  gap: 12px;
  padding: 16px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  background: var(--color-surface);
  box-shadow: var(--shadow);
  color: inherit;
  transition: border-color 0.15s ease, transform 0.15s ease;
}

.card:hover {
  border-color: #bfdbfe;
  transform: translateY(-1px);
}

.card__index {
  display: grid;
  place-items: center;
  flex: 0 0 26px;
  height: 26px;
  border-radius: 7px;
  background: #eff6ff;
  color: var(--color-primary);
  font-size: 12px;
  font-weight: 600;
}

.card__body {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.card__name {
  font-weight: 600;
}

.card__summary {
  color: var(--color-text-muted);
  font-size: 12.5px;
  line-height: 1.5;
}

.card__code {
  color: #9ca3af;
  font-size: 11.5px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}
</style>
