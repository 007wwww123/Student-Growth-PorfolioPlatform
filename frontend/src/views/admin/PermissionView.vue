<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { isApiError, type ApiError } from '@/api/http'
import {
  fetchPermissionMatrix,
  resetSectionPermissions,
  saveSectionPermissions,
} from '@/api/permission'
import type { FieldPermission, PermissionMatrix, PermissionMode, SectionPermission } from '@/api/types'

/**
 * 管理员 · 字段权限配置。
 *
 * <p>一个字段只能处于四种状态：均不可修改、仅学生、仅老师、师生均可。页面按板块切换，
 * 修改先落在本地草稿（不即时生效），点「保存并生效」后由后端写
 * `arc_field_def.editable_roles`，六个板块的表单与保存校验都读这一列，因此无需重启或刷新缓存。</p>
 *
 * <p>管理员在这里只配置权限，不读写任何学生档案内容；系统推导/系统生成的字段
 * （如专业、关注标记）固定只读，界面直接禁用并说明原因。</p>
 *
 * <p><b>为什么还留一个「全部板块」只读视图</b>：权限本身是全局的（一行
 * `editable_roles` 管六个板块），但保存事务是<b>板块级</b>的。所以配置仍按板块保存，
 * 另给一屏总览用于核对「全部字段现在谁能改」——这是把配置拆到六个独立页面后
 * 拿不回来的能力。总览只读，不放保存按钮，避免跨板块多次提交产生半成功状态。</p>
 */

type EditableRole = 'STU' | 'COU'

/** 页面视图：按板块配置 / 全部板块总览（只读）。 */
type ViewMode = 'section' | 'all'

const MODE_ROLES: Record<PermissionMode, EditableRole[]> = {
  NONE: [],
  STU: ['STU'],
  COU: ['COU'],
  BOTH: ['STU', 'COU'],
}

const MODE_LABELS: Record<PermissionMode, string> = {
  NONE: '均不可修改',
  STU: '仅学生可修改',
  COU: '仅老师可修改',
  BOTH: '师生均可修改',
}

const MODE_OPTIONS: Array<{ mode: PermissionMode; label: string; hint: string }> = [
  { mode: 'NONE', label: '均不可修改', hint: '任何角色都只能查看' },
  { mode: 'STU', label: '仅学生', hint: '辅导员只读' },
  { mode: 'COU', label: '仅老师', hint: '学生只读' },
  { mode: 'BOTH', label: '师生均可', hint: '双方都可修改' },
]

const matrix = ref<PermissionMatrix | null>(null)
const activeCode = ref('BASIC')
/** 当前视图：默认按板块；带 `?section=all` 时进全部板块总览 */
const viewMode = ref<ViewMode>('section')
const route = useRoute()
const router = useRouter()
const drafts = reactive<Record<string, Record<string, EditableRole[]>>>({})
const loading = ref(false)
const saving = ref(false)
const resetting = ref(false)
const confirmingReset = ref(false)
const error = ref<ApiError | null>(null)
const notice = ref('')

const sections = computed(() => matrix.value?.sections ?? [])
const activeSection = computed<SectionPermission | undefined>(() =>
  sections.value.find((section) => section.sectionCode === activeCode.value),
)

/** 初始化/重载草稿：每次都以服务端返回的当前生效值为准。 */
function resetDrafts(data: PermissionMatrix): void {
  for (const key of Object.keys(drafts)) {
    delete drafts[key]
  }
  for (const section of data.sections) {
    const sectionDraft: Record<string, EditableRole[]> = {}
    for (const field of section.fields) {
      sectionDraft[field.fieldCode] = [...field.editableRoles]
    }
    drafts[section.sectionCode] = sectionDraft
  }
}

/**
 * 按 URL 的 `?section=` 定位视图。
 *
 * <p>板块页的「本板块字段权限」链接会带上 `?section=basic`，管理员从内容页跳过来
 * 直接落在对应板块，不用再点一次页签；`?section=all` 进全部板块总览。</p>
 */
function syncFromQuery(data: PermissionMatrix): void {
  const raw = route.query.section
  const value = Array.isArray(raw) ? raw[0] : raw
  if (typeof value !== 'string' || value.trim() === '') {
    return
  }
  const code = value.trim().toUpperCase()
  if (code === 'ALL') {
    viewMode.value = 'all'
    return
  }
  if (data.sections.some((section) => section.sectionCode === code)) {
    activeCode.value = code
    viewMode.value = 'section'
  }
}

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    const data = await fetchPermissionMatrix()
    matrix.value = data
    resetDrafts(data)
    if (!data.sections.some((section) => section.sectionCode === activeCode.value)) {
      activeCode.value = data.sections[0]?.sectionCode ?? 'BASIC'
    }
    syncFromQuery(data)
  } catch (e) {
    matrix.value = null
    error.value = isApiError(e) ? e : null
  } finally {
    loading.value = false
  }
}

function rolesOf(field: FieldPermission): EditableRole[] {
  return drafts[activeCode.value]?.[field.fieldCode] ?? [...field.editableRoles]
}

function modeOfRoles(roles: EditableRole[]): PermissionMode {
  const student = roles.includes('STU')
  const counselor = roles.includes('COU')
  if (student && counselor) return 'BOTH'
  if (student) return 'STU'
  if (counselor) return 'COU'
  return 'NONE'
}

function modeOf(field: FieldPermission): PermissionMode {
  return modeOfRoles(rolesOf(field))
}

/**
 * 角色是否可见该字段。
 *
 * <p>接口未下发可见性时按「可见」处理，避免旧版本 mock 让整页选项被禁用；
 * 后端对「不可见却配成可编辑」一律返回 EC-40003，前端禁用只是为了少走一次往返。</p>
 */
function roleVisible(field: FieldPermission, role: EditableRole): boolean {
  return role === 'STU' ? field.studentVisible !== false : field.counselorVisible !== false
}

/** 该选项是否可选：不可见的角色不能被配成可编辑。 */
function modeAllowed(field: FieldPermission, mode: PermissionMode): boolean {
  if (field.locked) {
    return false
  }
  return MODE_ROLES[mode].every((role) => roleVisible(field, role))
}

function modeHint(field: FieldPermission, option: (typeof MODE_OPTIONS)[number]): string {
  const blocked = MODE_ROLES[option.mode].filter((role) => !roleVisible(field, role))
  if (blocked.length === 0) {
    return option.hint
  }
  const labels = blocked.map((role) => (role === 'STU' ? '学生' : '老师')).join('、')
  return `${labels}看不到该字段，不能配置为可编辑`
}

/** 只对老师可见 / 只对学生可见，列表里标出来，避免管理员误以为漏配。 */
function visibilityNote(field: FieldPermission): string {
  if (field.studentVisible === false) {
    return '学生不可见'
  }
  if (field.counselorVisible === false) {
    return '老师不可见'
  }
  return ''
}

function setMode(field: FieldPermission, mode: PermissionMode): void {
  if (!modeAllowed(field, mode)) {
    return
  }
  drafts[activeCode.value][field.fieldCode] = [...MODE_ROLES[mode]]
}

function sameRoles(left: EditableRole[], right: EditableRole[]): boolean {
  return left.length === right.length && left.every((role) => right.includes(role))
}

function isDirty(field: FieldPermission): boolean {
  return !sameRoles(rolesOf(field), field.editableRoles)
}

function dirtyCountOf(sectionCode: string): number {
  const section = sections.value.find((item) => item.sectionCode === sectionCode)
  const draft = drafts[sectionCode]
  if (!section || !draft) return 0
  return section.fields.filter((field) =>
    !sameRoles(draft[field.fieldCode] ?? field.editableRoles, field.editableRoles),
  ).length
}

const dirtyCount = computed(() => dirtyCountOf(activeCode.value))

/** 保存后的权限预览：按草稿现算，帮助管理员在保存前确认影响面。 */
const draftStats = computed(() => {
  const draft = drafts[activeCode.value] ?? {}
  const fields = activeSection.value?.fields ?? []
  let student = 0
  let counselor = 0
  let both = 0
  let readonly = 0
  for (const field of fields) {
    const roles = draft[field.fieldCode] ?? field.editableRoles
    if (roles.includes('STU')) student++
    if (roles.includes('COU')) counselor++
    if (roles.includes('STU') && roles.includes('COU')) both++
    if (roles.length === 0) readonly++
  }
  return { student, counselor, both, readonly }
})

const savedAt = ref('')

function replaceSection(section: SectionPermission): void {
  if (!matrix.value) return
  const index = matrix.value.sections.findIndex((item) => item.sectionCode === section.sectionCode)
  if (index >= 0) {
    matrix.value.sections[index] = section
  }
  const sectionDraft: Record<string, EditableRole[]> = {}
  for (const field of section.fields) {
    sectionDraft[field.fieldCode] = [...field.editableRoles]
  }
  drafts[section.sectionCode] = sectionDraft
  savedAt.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
}

async function onSave(): Promise<void> {
  const section = activeSection.value
  if (!section || dirtyCount.value === 0) {
    notice.value = '没有需要保存的修改'
    return
  }
  saving.value = true
  error.value = null
  notice.value = ''
  confirmingReset.value = false
  try {
    const draft = drafts[section.sectionCode]
    const result = await saveSectionPermissions(
      section.sectionCode,
      section.fields
        .filter((field) => !field.locked)
        .map((field) => ({
          fieldCode: field.fieldCode,
          editableRoles: [...(draft[field.fieldCode] ?? field.editableRoles)],
        })),
    )
    replaceSection(result.section)
    notice.value =
      result.changed > 0
        ? `已保存并生效：更新 ${result.changed} 个字段，${savedAt.value} 起按新权限控制。`
        : '配置未变化，无需更新。'
  } catch (e) {
    error.value = isApiError(e) ? e : null
  } finally {
    saving.value = false
  }
}

async function onReset(): Promise<void> {
  const section = activeSection.value
  if (!section) return
  resetting.value = true
  error.value = null
  notice.value = ''
  try {
    const result = await resetSectionPermissions(section.sectionCode)
    replaceSection(result.section)
    notice.value = `已恢复「${section.sectionName}」的默认权限，更新 ${result.changed} 个字段。`
  } catch (e) {
    error.value = isApiError(e) ? e : null
  } finally {
    resetting.value = false
    confirmingReset.value = false
  }
}

function discard(): void {
  const section = activeSection.value
  if (!section) return
  const sectionDraft: Record<string, EditableRole[]> = {}
  for (const field of section.fields) {
    sectionDraft[field.fieldCode] = [...field.editableRoles]
  }
  drafts[section.sectionCode] = sectionDraft
  notice.value = '已撤销未保存的修改'
}

function switchSection(sectionCode: string): void {
  activeCode.value = sectionCode
  viewMode.value = 'section'
  notice.value = ''
  error.value = null
  confirmingReset.value = false
  void router.replace({ query: { ...route.query, section: sectionCode.toLowerCase() } })
}

/** 全部板块总览：一屏看全全部字段的权限，只读，不动草稿。 */
function showAll(): void {
  viewMode.value = 'all'
  notice.value = ''
  error.value = null
  confirmingReset.value = false
  void router.replace({ query: { ...route.query, section: 'all' } })
}

/** 总览行：带上板块信息，便于跨板块核对。 */
interface MatrixRow {
  sectionCode: string
  sectionName: string
  field: FieldPermission
  /** 该板块的首行：板块名用 rowspan 合并，避免 51 行里「基本信息」重复 16 次 */
  first: boolean
}

const allRows = computed<MatrixRow[]>(() =>
  sections.value.flatMap((section) =>
    section.fields.map((field, index) => ({
      sectionCode: section.sectionCode,
      sectionName: section.sectionName,
      field,
      first: index === 0,
    })),
  ),
)

function sectionSizeOf(sectionCode: string): number {
  return sections.value.find((section) => section.sectionCode === sectionCode)?.fields.length ?? 1
}

const allStats = computed(() => {
  const rows = allRows.value
  return {
    total: rows.length,
    student: rows.filter((row) => row.field.editableRoles.includes('STU')).length,
    counselor: rows.filter((row) => row.field.editableRoles.includes('COU')).length,
    readonly: rows.filter((row) => row.field.editableRoles.length === 0).length,
    customized: rows.filter((row) => row.field.customized).length,
    locked: rows.filter((row) => row.field.locked).length,
  }
})

function sourceLabel(source?: string): string {
  const labels: Record<string, string> = {
    SIS: '学籍同步',
    COU: '辅导员维护',
    STU: '学生自填',
    STU_CFM: '学生提交·辅导员确认',
    SYS: '系统生成',
    IMP: '批量导入',
  }
  return source ? (labels[source] ?? source) : '—'
}

load()

/**
 * 从板块页的深链再次跳进来时组件已挂载，query 变化不会重新加载，
 * 需要单独同步一次视图。
 */
watch(
  () => route.query.section,
  () => {
    if (matrix.value) {
      syncFromQuery(matrix.value)
    }
  },
)
</script>

<template>
  <section class="perm">
    <header class="perm__head">
      <div>
        <h1 class="perm__title">字段权限配置</h1>
        <p class="perm__summary">
          按板块设置每个字段允许谁修改。保存后立即按新权限控制学生与辅导员的实际编辑能力。
        </p>
      </div>
      <span class="perm__mode">管理员模式</span>
    </header>

    <div class="perm__notice">
      <strong>权限边界</strong>
      <span>管理员只负责配置权限，不能修改任何板块的档案内容；系统推导与学籍唯一标识类字段固定只读。</span>
    </div>

    <p v-if="notice" class="alert alert--success">{{ notice }}</p>

    <div v-if="error" class="alert alert--danger">
      <div class="alert__head">
        <strong>{{ error.code }}</strong>
        <span>{{ error.message }}</span>
      </div>
      <p v-if="error.traceId" class="alert__trace">traceId：{{ error.traceId }}</p>
    </div>

    <p v-if="loading" class="perm__loading">加载权限矩阵…</p>

    <template v-else-if="matrix">
      <nav class="perm__tabs" aria-label="板块切换">
        <button
          v-for="section in sections"
          :key="section.sectionCode"
          type="button"
          class="perm__tab"
          :class="{ 'is-active': viewMode === 'section' && section.sectionCode === activeCode }"
          @click="switchSection(section.sectionCode)"
        >
          <span>{{ section.sectionName }}</span>
          <span v-if="dirtyCountOf(section.sectionCode) > 0" class="perm__tab-dirty">
            {{ dirtyCountOf(section.sectionCode) }} 项待保存
          </span>
          <span v-else class="perm__tab-count">{{ section.fields.length }} 字段</span>
        </button>
        <button
          type="button"
          class="perm__tab perm__tab--all"
          :class="{ 'is-active': viewMode === 'all' }"
          @click="showAll"
        >
          <span>全部板块</span>
          <span class="perm__tab-count">{{ allStats.total }} 字段</span>
        </button>
      </nav>

      <!-- 全部板块总览：只读。配置仍按板块保存，这里只做跨板块核对 -->
      <div v-if="viewMode === 'all'" class="perm__panel">
        <header class="perm__panel-head">
          <div>
            <h2 class="perm__panel-title">全部板块 · 权限总览</h2>
            <p class="perm__panel-sub">
              共 {{ allStats.total }} 个字段 · 学生可编辑
              <strong>{{ allStats.student }}</strong> · 老师可编辑
              <strong>{{ allStats.counselor }}</strong> · 只读
              <strong>{{ allStats.readonly }}</strong> · 已调整
              <strong>{{ allStats.customized }}</strong> · 固定只读
              <strong>{{ allStats.locked }}</strong>
            </p>
          </div>
          <div class="perm__actions">
            <button type="button" class="btn btn--ghost" :disabled="loading" @click="load">
              重新加载
            </button>
            <button type="button" class="btn btn--primary" @click="switchSection(activeCode)">
              按板块配置
            </button>
          </div>
        </header>

        <p class="perm__all-tip">
          总览只读：配置按板块整批保存（一次事务），避免跨板块提交出现半成功。点板块名可直接进入该板块配置。
        </p>

        <div class="perm__table-wrap">
          <table class="perm__table perm__table--all">
            <thead>
              <tr>
                <th class="col-section">板块</th>
                <th class="col-field">字段</th>
                <th class="col-source">类型 / 来源</th>
                <th class="col-current">当前生效</th>
                <th class="col-visibility">可见性</th>
                <th class="col-status">状态</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="row in allRows"
                :key="`${row.sectionCode}.${row.field.fieldCode}`"
                :class="{ 'is-locked': row.field.locked, 'is-section-first': row.first }"
              >
                <td v-if="row.first" :rowspan="sectionSizeOf(row.sectionCode)" class="perm__section-cell">
                  <button type="button" class="perm__section-link" @click="switchSection(row.sectionCode)">
                    {{ row.sectionName }}
                  </button>
                  <span class="perm__section-count">{{ sectionSizeOf(row.sectionCode) }} 字段</span>
                </td>
                <td>
                  <div class="perm__field-name">
                    {{ row.field.fieldName }}
                    <span v-if="row.field.required" class="perm__required" title="必填">*</span>
                  </div>
                  <div class="perm__field-code">{{ row.field.fieldCode }}</div>
                </td>
                <td>
                  <div class="perm__chip">{{ row.field.fieldType }}</div>
                  <div class="perm__source">{{ sourceLabel(row.field.source) }}</div>
                </td>
                <td>
                  <span class="perm__mode-tag" :class="`mode--${row.field.permissionMode}`">
                    {{ MODE_LABELS[row.field.permissionMode] }}
                  </span>
                </td>
                <td>
                  <span v-if="visibilityNote(row.field)" class="perm__visibility">
                    {{ visibilityNote(row.field) }}
                  </span>
                  <span v-else class="perm__source">师生均可见</span>
                </td>
                <td>
                  <span v-if="row.field.locked" class="status status--locked">固定只读</span>
                  <span v-else-if="row.field.customized" class="status status--custom">已调整</span>
                  <span v-else class="status status--default">默认</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div v-else-if="activeSection" class="perm__panel">
        <header class="perm__panel-head">
          <div>
            <h2 class="perm__panel-title">{{ activeSection.sectionName }}</h2>
            <p class="perm__panel-sub">
              共 {{ activeSection.fields.length }} 个字段 · 学生可编辑
              <strong>{{ draftStats.student }}</strong> · 老师可编辑
              <strong>{{ draftStats.counselor }}</strong> · 其中师生均可
              <strong>{{ draftStats.both }}</strong> · 只读
              <strong>{{ draftStats.readonly }}</strong>
            </p>
          </div>
          <div class="perm__actions">
            <button
              v-if="dirtyCount > 0"
              type="button"
              class="btn btn--ghost"
              :disabled="saving || resetting"
              @click="discard"
            >
              撤销修改
            </button>
            <button
              type="button"
              class="btn btn--ghost"
              :disabled="saving || resetting || activeSection.customized === 0"
              @click="confirmingReset = true"
            >
              恢复默认
            </button>
            <button
              type="button"
              class="btn btn--primary"
              :disabled="saving || resetting || dirtyCount === 0"
              @click="onSave"
            >
              {{ saving ? '保存中…' : dirtyCount > 0 ? `保存并生效（${dirtyCount}）` : '保存并生效' }}
            </button>
          </div>
        </header>

        <div v-if="confirmingReset" class="confirm">
          <p class="confirm__text">
            恢复默认会把「{{ activeSection.sectionName }}」全部 {{ activeSection.fields.length }}
            个字段改回初始化口径，当前已调整的 {{ activeSection.customized }} 项会被覆盖，确定继续吗？
          </p>
          <div class="confirm__actions">
            <button type="button" class="btn" :disabled="resetting" @click="confirmingReset = false">
              取消
            </button>
            <button type="button" class="btn btn--danger" :disabled="resetting" @click="onReset">
              {{ resetting ? '恢复中…' : '确认恢复默认' }}
            </button>
          </div>
        </div>

        <div class="perm__table-wrap">
          <table class="perm__table">
            <thead>
              <tr>
                <th class="col-field">字段</th>
                <th class="col-source">类型 / 来源</th>
                <th class="col-current">当前生效</th>
                <th class="col-config">编辑权限配置</th>
                <th class="col-status">状态</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="field in activeSection.fields"
                :key="field.fieldCode"
                :class="{ 'is-dirty': isDirty(field), 'is-locked': field.locked }"
              >
                <td>
                  <div class="perm__field-name">
                    {{ field.fieldName }}
                    <span v-if="field.required" class="perm__required" title="必填">*</span>
                  </div>
                  <div class="perm__field-code">{{ field.fieldCode }}</div>
                  <div v-if="visibilityNote(field)" class="perm__visibility">{{ visibilityNote(field) }}</div>
                </td>
                <td>
                  <div class="perm__chip">{{ field.fieldType }}</div>
                  <div class="perm__source">{{ sourceLabel(field.source) }}</div>
                </td>
                <td>
                  <span class="perm__mode-tag" :class="`mode--${field.permissionMode}`">
                    {{ MODE_LABELS[field.permissionMode] }}
                  </span>
                </td>
                <td>
                  <div v-if="field.locked" class="perm__locked">
                    <span class="perm__lock-tag">固定只读</span>
                    <span>{{ field.lockedReason }}</span>
                  </div>
                  <div v-else class="segmented" role="radiogroup" :aria-label="`${field.fieldName}编辑权限`">
                    <label
                      v-for="option in MODE_OPTIONS"
                      :key="option.mode"
                      class="segmented__item"
                      :class="{
                        'is-checked': modeOf(field) === option.mode,
                        'is-disabled': !modeAllowed(field, option.mode),
                      }"
                      :title="modeHint(field, option)"
                    >
                      <input
                        type="radio"
                        :name="`perm-${field.fieldCode}`"
                        :value="option.mode"
                        :checked="modeOf(field) === option.mode"
                        :disabled="!modeAllowed(field, option.mode)"
                        @change="setMode(field, option.mode)"
                      />
                      {{ option.label }}
                    </label>
                  </div>
                </td>
                <td>
                  <span v-if="field.locked" class="status status--locked">固定只读</span>
                  <span v-else-if="isDirty(field)" class="status status--dirty">待保存</span>
                  <span v-else-if="field.customized" class="status status--custom">已调整</span>
                  <span v-else class="status status--default">默认</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <footer class="perm__foot">
          <span v-if="dirtyCount > 0" class="perm__dirty-tip">
            有 {{ dirtyCount }} 项修改未保存；切换板块不会丢失，保存后立即生效。
          </span>
          <span v-else-if="savedAt" class="perm__saved-tip">
            当前配置已生效 · 最近保存 {{ savedAt }}
          </span>
          <span v-else class="perm__saved-tip">当前配置与服务端一致</span>
          <button type="button" class="btn btn--ghost" :disabled="loading" @click="load">重新加载</button>
        </footer>
      </div>
    </template>
  </section>
</template>

<style scoped>
.perm {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.perm__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.perm__title {
  margin: 0 0 4px;
  font-size: 19px;
  font-weight: 600;
}

.perm__summary {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 13px;
}

.perm__mode {
  flex: none;
  padding: 3px 10px;
  border: 1px solid #bfdbfe;
  border-radius: 999px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 12px;
}

.perm__notice {
  display: flex;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  background: #f8fafc;
  color: #475569;
  font-size: 13px;
}

.perm__notice strong {
  flex: none;
  color: var(--color-text);
}

.perm__loading {
  color: var(--color-text-muted);
}

.perm__tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.perm__tab {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  min-width: 116px;
  padding: 8px 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text-muted);
  text-align: left;
}

.perm__tab:hover {
  border-color: #cbd5e1;
}

.perm__tab.is-active {
  border-color: var(--color-primary);
  background: #eff6ff;
  color: var(--color-primary-dark);
  box-shadow: inset 0 0 0 1px var(--color-primary);
}

.perm__tab > span:first-child {
  color: var(--color-text);
  font-size: 13px;
  font-weight: 600;
}

.perm__tab.is-active > span:first-child {
  color: var(--color-primary-dark);
}

.perm__tab-count {
  font-size: 11.5px;
}

.perm__tab-dirty {
  color: var(--color-warning);
  font-size: 11.5px;
}

/* 「全部板块」是总览而非配置容器：用虚线边框与实线页签区分 */
.perm__tab--all {
  border-style: dashed;
  margin-left: 4px;
}

.perm__tab--all.is-active {
  border-style: solid;
}

.perm__all-tip {
  margin: 0;
  padding: 8px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  background: #f8fafc;
  color: #475569;
  font-size: 12.5px;
}

.perm__table--all {
  min-width: 940px;
}

.col-section {
  width: 12%;
}

.col-visibility {
  width: 12%;
}

.perm__section-link {
  padding: 0;
  border: none;
  background: none;
  color: var(--color-primary-dark);
  font-size: 12.5px;
  text-decoration: underline;
  text-underline-offset: 2px;
  cursor: pointer;
}

.perm__section-link:hover {
  color: var(--color-primary);
}

/* 板块名合并单元格：贴顶对齐 + 右侧细分隔线，读起来像分组表 */
.perm__table td.perm__section-cell {
  border-right: 1px solid #f1f5f9;
  vertical-align: top;
  white-space: nowrap;
}

.perm__section-count {
  display: block;
  margin-top: 3px;
  color: #94a3b8;
  font-size: 11px;
}

.perm__table tr.is-section-first td {
  border-top: 1px solid #e2e8f0;
}

.perm__table tbody tr.is-section-first:first-child td {
  border-top: none;
}

.perm__panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 18px 20px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  background: var(--color-surface);
  box-shadow: var(--shadow);
}

.perm__panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.perm__panel-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.perm__panel-sub {
  margin: 4px 0 0;
  color: var(--color-text-muted);
  font-size: 12.5px;
}

.perm__panel-sub strong {
  color: var(--color-text);
}

.perm__actions {
  display: flex;
  flex: none;
  gap: 8px;
}

.perm__table-wrap {
  overflow-x: auto;
  border: 1px solid var(--color-border);
  border-radius: 6px;
}

.perm__table {
  width: 100%;
  min-width: 860px;
  border-collapse: collapse;
  font-size: 13px;
}

.perm__table th {
  padding: 9px 12px;
  border-bottom: 1px solid var(--color-border);
  background: #f9fafb;
  color: var(--color-text-muted);
  font-size: 12px;
  font-weight: 500;
  text-align: left;
  white-space: nowrap;
}

.perm__table td {
  padding: 10px 12px;
  border-bottom: 1px solid #f1f5f9;
  vertical-align: middle;
}

.perm__table tbody tr:last-child td {
  border-bottom: none;
}

.perm__table tr.is-dirty td {
  background: #fffbeb;
}

.perm__table tr.is-locked td {
  background: #fbfcfd;
}

.col-field {
  width: 22%;
}

.col-source {
  width: 15%;
}

.col-current {
  width: 13%;
}

.col-config {
  width: 38%;
}

.col-status {
  width: 12%;
}

.perm__field-name {
  font-weight: 500;
}

.perm__required {
  color: var(--color-danger);
}

.perm__field-code {
  margin-top: 2px;
  color: #94a3b8;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 11.5px;
}

.perm__visibility {
  display: inline-block;
  margin-top: 3px;
  padding: 0 6px;
  border: 1px dashed #fcd34d;
  border-radius: 4px;
  background: #fffbeb;
  color: #b45309;
  font-size: 11px;
}

.perm__chip {
  display: inline-block;
  padding: 0 6px;
  border: 1px solid var(--color-border);
  border-radius: 4px;
  background: #f9fafb;
  color: #475569;
  font-size: 11px;
}

.perm__source {
  margin-top: 3px;
  color: var(--color-text-muted);
  font-size: 11.5px;
}

.perm__mode-tag {
  display: inline-block;
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 12px;
  white-space: nowrap;
}

.mode--NONE {
  border: 1px solid #e2e8f0;
  background: #f1f5f9;
  color: #64748b;
}

.mode--STU {
  border: 1px solid #bfdbfe;
  background: #eff6ff;
  color: #1d4ed8;
}

.mode--COU {
  border: 1px solid #ddd6fe;
  background: #f5f3ff;
  color: #6d28d9;
}

.mode--BOTH {
  border: 1px solid #bbf7d0;
  background: #f0fdf4;
  color: #15803d;
}

.perm__locked {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--color-text-muted);
  font-size: 12.5px;
}

.perm__lock-tag {
  flex: none;
  padding: 0 6px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  background: #f1f5f9;
  color: #64748b;
  font-size: 11px;
}

.segmented {
  display: inline-flex;
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: 6px;
}

.segmented__item {
  position: relative;
  padding: 5px 10px;
  border-right: 1px solid var(--color-border);
  background: #fff;
  color: var(--color-text-muted);
  font-size: 12px;
  cursor: pointer;
  user-select: none;
  white-space: nowrap;
}

.segmented__item:last-child {
  border-right: none;
}

.segmented__item:hover {
  background: #f8fafc;
}

.segmented__item.is-checked {
  background: var(--color-primary);
  color: #fff;
}

.segmented__item.is-disabled {
  background: #f8fafc;
  color: #cbd5e1;
  cursor: not-allowed;
  text-decoration: line-through;
}

.segmented__item.is-disabled.is-checked {
  /* 理论上不会出现（不可见不会被配成可编辑），存量脏数据兜底时仍要能看出来 */
  background: #e2e8f0;
  color: #94a3b8;
}

.segmented__item input {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
  pointer-events: none;
}

.segmented__item:focus-within {
  outline: 2px solid #93c5fd;
  outline-offset: -2px;
}

.status {
  display: inline-block;
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 12px;
  white-space: nowrap;
}

.status--default {
  border: 1px solid #e5e7eb;
  background: #f9fafb;
  color: #6b7280;
}

.status--custom {
  border: 1px solid #bfdbfe;
  background: #eff6ff;
  color: #1d4ed8;
}

.status--dirty {
  border: 1px solid #fde68a;
  background: #fffbeb;
  color: var(--color-warning);
}

.status--locked {
  border: 1px solid #e2e8f0;
  background: #f1f5f9;
  color: #64748b;
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

.perm__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-top: 2px;
  color: var(--color-text-muted);
  font-size: 12.5px;
}

.perm__dirty-tip {
  color: var(--color-warning);
}

.perm__saved-tip {
  color: var(--color-text-muted);
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

.alert__trace {
  margin: 6px 0 0;
  color: #b45454;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
}

@media (max-width: 980px) {
  .perm__panel-head {
    flex-direction: column;
  }

  .perm__actions {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>
