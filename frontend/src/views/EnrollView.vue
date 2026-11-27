<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { isApiError, type ApiError } from '@/api/http'
import { batchCreateProfiles, fetchEnrollClasses } from '@/api/profiles'
import type { BatchCreateResult, EnrollClass } from '@/api/types'
import { DEMO_ACADEMIC_YEAR, currentAcademicYear } from '@/sectionMeta'

/**
 * 批量建档（S-1-01）。
 *
 * <p>页面只做两件事：看清「哪些班还没建档」，以及按班级生成六板块空档案。
 * 生成的是 DRAFT 实例，不写任何字段值，因此这里也不出现表单。</p>
 *
 * <p>重复执行是安全的：已存在的板块由服务端跳过（EX-01），
 * 结果区会明确显示「本次新建 0 条」，而不是报错或覆盖已有内容。</p>
 */
const YEARS = Array.from(new Set([DEMO_ACADEMIC_YEAR, currentAcademicYear()]))

const academicYear = ref(DEMO_ACADEMIC_YEAR)
const classes = ref<EnrollClass[]>([])
const selected = ref<string[]>([])
const loading = ref(false)
const submitting = ref(false)
const error = ref<ApiError | null>(null)
const result = ref<BatchCreateResult | null>(null)

/** 班级全选：只有「待建档」的班可勾选，已全部建档的班没有可做的事。 */
const selectableCodes = computed(() =>
  classes.value.filter((item) => !item.enrolled && item.studentCount > 0).map((item) => item.classCode),
)
const allSelected = computed(
  () => selectableCodes.value.length > 0 && selected.value.length === selectableCodes.value.length,
)

const selectedClasses = computed(() =>
  classes.value.filter((item) => selected.value.includes(item.classCode)),
)
const selectedStudentCount = computed(() =>
  selectedClasses.value.reduce((sum, item) => sum + item.studentCount, 0),
)
const selectedMissingCount = computed(() =>
  selectedClasses.value.reduce((sum, item) => sum + item.missingStudentCount, 0),
)
const totalMissing = computed(() =>
  classes.value.reduce((sum, item) => sum + item.missingStudentCount, 0),
)

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    classes.value = await fetchEnrollClasses(academicYear.value)
    // 名单变化后清空选择，避免把上学期勾选的班级带到新学期
    selected.value = []
  } catch (e) {
    classes.value = []
    selected.value = []
    error.value = isApiError(e) ? e : null
  } finally {
    loading.value = false
  }
}

function toggleAll(): void {
  selected.value = allSelected.value ? [] : [...selectableCodes.value]
}

async function submit(): Promise<void> {
  if (selected.value.length === 0 || submitting.value) {
    return
  }
  submitting.value = true
  error.value = null
  try {
    result.value = await batchCreateProfiles({
      academicYear: academicYear.value,
      classCodes: [...selected.value],
    })
    await load()
  } catch (e) {
    result.value = null
    error.value = isApiError(e) ? e : null
  } finally {
    submitting.value = false
  }
}

function formatSections(codes: string[]): string {
  if (codes.length === 0) {
    return '—'
  }
  const names: Record<string, string> = {
    BASIC: '基本信息',
    ACADEMIC: '学业发展',
    INTEREST: '兴趣特长',
    LIFE: '生活心理',
    CAREER: '职业规划',
    MENTOR: '导师寄语',
  }
  return codes.map((code) => names[code] ?? code).join('、')
}

watch(academicYear, () => {
  result.value = null
  void load()
})

onMounted(load)
</script>

<template>
  <section class="enroll">
    <header class="enroll__head">
      <div>
        <h1 class="enroll__title">批量建档</h1>
        <p class="enroll__summary">
          按学年与班级为在读学生生成六板块空档案（草稿）。已存在的板块自动跳过，不会覆盖已填内容。
        </p>
      </div>
      <span class="enroll__scope">仅本班</span>
    </header>

    <div class="enroll__toolbar">
      <label class="enroll__select">
        学年
        <select v-model="academicYear">
          <option v-for="year in YEARS" :key="year" :value="year">
            {{ year }}{{ year === currentAcademicYear() ? '（当前）' : '' }}
          </option>
        </select>
      </label>

      <p class="enroll__hint">
        共 {{ classes.length }} 个班 · 待建档 {{ totalMissing }} 人
        <template v-if="selected.length > 0">
          · 已选 {{ selected.length }} 个班 / {{ selectedStudentCount }} 人（待建档
          {{ selectedMissingCount }} 人）
        </template>
      </p>

      <button
        type="button"
        class="enroll__submit"
        :disabled="selected.length === 0 || submitting"
        @click="submit"
      >
        {{ submitting ? '正在生成…' : '生成六板块档案' }}
      </button>
    </div>

    <div v-if="error" class="alert alert--danger">
      <div class="alert__head">
        <strong>{{ error.code }}</strong>
        <span>{{ error.message }}</span>
      </div>
      <p v-if="error.code === 'EC-40302'" class="alert__tip">
        批量建档仅限辅导员本人带班班级；管理员为只读授权，不能执行建档。
      </p>
      <button type="button" class="alert__retry" @click="load">重试</button>
    </div>

    <p v-else-if="loading" class="enroll__loading">加载中…</p>

    <div v-else-if="classes.length === 0" class="enroll__empty">
      <p>当前学年没有你带班的在读学生。</p>
      <p class="enroll__empty-tip">
        学生名单来自学生主数据；班级的现任辅导员决定这里的可见范围。
      </p>
    </div>

    <table v-else class="table">
      <thead>
        <tr>
          <th class="table__check">
            <input
              type="checkbox"
              :checked="allSelected"
              :disabled="selectableCodes.length === 0"
              aria-label="全选待建档班级"
              @change="toggleAll"
            />
          </th>
          <th>班级</th>
          <th>专业</th>
          <th>年级</th>
          <th>在读人数</th>
          <th>已建档</th>
          <th>待建档</th>
          <th>状态</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in classes" :key="item.classCode">
          <td class="table__check">
            <input
              v-model="selected"
              type="checkbox"
              :value="item.classCode"
              :disabled="item.enrolled || item.studentCount === 0"
              :aria-label="`选择 ${item.className}`"
            />
          </td>
          <td>{{ item.className }}</td>
          <td>{{ item.majorName ?? '—' }}</td>
          <td>{{ item.gradeYear ?? '—' }}</td>
          <td>{{ item.studentCount }}</td>
          <td>{{ item.enrolledStudentCount }}</td>
          <td>{{ item.missingStudentCount }}</td>
          <td>
            <span class="tag" :class="item.enrolled ? 'tag--APPROVED' : 'tag--DRAFT'">
              {{ item.enrolled ? '已建档' : '待建档' }}
            </span>
          </td>
        </tr>
      </tbody>
    </table>

    <section v-if="result" class="result">
      <header class="result__head">
        <h2 class="result__title">
          {{ result.academicYear }} 建档结果
          <span v-if="result.createdProfiles === 0" class="result__badge">无需重复建档</span>
        </h2>
        <button type="button" class="result__close" aria-label="关闭结果" @click="result = null">
          ×
        </button>
      </header>

      <dl class="stats">
        <div class="stats__item">
          <dt>学生总数</dt>
          <dd>{{ result.totalStudents }}</dd>
        </div>
        <div class="stats__item">
          <dt>本次新建学生</dt>
          <dd>{{ result.createdStudents }}</dd>
        </div>
        <div class="stats__item">
          <dt>跳过（已建档）</dt>
          <dd>{{ result.skippedStudents }}</dd>
        </div>
        <div class="stats__item">
          <dt>新建板块实例</dt>
          <dd>{{ result.createdProfiles }}</dd>
        </div>
      </dl>

      <p v-if="result.createdProfiles === 0" class="result__tip">
        所选班级的六板块实例均已存在，未做任何写入。学生可直接登录填写档案。
      </p>

      <table class="table">
        <thead>
          <tr>
            <th>学号</th>
            <th>姓名</th>
            <th>班级</th>
            <th>本次新建板块</th>
            <th>跳过</th>
            <th>结果</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in result.students" :key="item.studentId">
            <td>{{ item.studentNo ?? item.studentId }}</td>
            <td>{{ item.studentName ?? '—' }}</td>
            <td>{{ item.className ?? item.classCode ?? '—' }}</td>
            <td class="result__sections">{{ formatSections(item.createdSections) }}</td>
            <td>{{ item.skippedSectionCount }}</td>
            <td>
              <span class="tag" :class="item.status === 'CREATED' ? 'tag--APPROVED' : 'tag--LOCKED'">
                {{ item.status === 'CREATED' ? '已新建' : '已存在' }}
              </span>
            </td>
          </tr>
        </tbody>
      </table>
    </section>
  </section>
</template>

<style scoped>
.enroll {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.enroll__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.enroll__title {
  margin: 0 0 4px;
  font-size: 19px;
  font-weight: 600;
}

.enroll__summary {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 13px;
}

.enroll__scope {
  flex: none;
  padding: 2px 10px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  color: var(--color-text-muted);
  font-size: 12px;
}

.enroll__toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.enroll__select {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--color-text-muted);
  font-size: 13px;
}

.enroll__select select {
  padding: 5px 8px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: #fff;
}

.enroll__hint {
  flex: 1;
  margin: 0;
  color: var(--color-text-muted);
  font-size: 12.5px;
}

.enroll__submit {
  padding: 6px 14px;
  border: 1px solid var(--color-primary);
  border-radius: 6px;
  background: var(--color-primary);
  color: #fff;
  font-size: 13px;
}

.enroll__submit:disabled {
  border-color: var(--color-border);
  background: #f3f4f6;
  color: var(--color-text-muted);
  cursor: not-allowed;
}

.enroll__loading {
  color: var(--color-text-muted);
}

.enroll__empty {
  padding: 28px 20px;
  border: 1px dashed var(--color-border);
  border-radius: var(--radius);
  background: var(--color-surface);
  text-align: center;
}

.enroll__empty p {
  margin: 0;
}

.enroll__empty-tip {
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

.result__close {
  border: none;
  background: transparent;
  color: var(--color-text-muted);
  font-size: 18px;
  line-height: 1;
}

.stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 10px;
  margin: 0;
}

.stats__item {
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: #fbfcfe;
}

.stats__item dt {
  color: var(--color-text-muted);
  font-size: 12px;
}

.stats__item dd {
  margin: 2px 0 0;
  font-size: 20px;
  font-weight: 600;
}

.result__tip {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 12.5px;
}

.result__sections {
  white-space: normal;
  color: var(--color-text-muted);
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
