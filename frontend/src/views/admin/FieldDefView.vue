<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  createFieldDef,
  fetchFieldDefs,
  fetchFieldMeta,
  updateFieldDef,
  updateFieldDefRequired,
  updateFieldDefStatus,
  type FieldDef,
  type FieldDefPayload,
  type FieldMeta,
} from '@/api/fields'
import { isApiError, type ApiError } from '@/api/http'
import { SECTIONS } from '@/sectionMeta'

/**
 * 字段定义配置（F-04 / AC-007）。
 *
 * <p>维护 arc_field_def：字段增改、必填开关、类型/校验器/选项、可见与可编辑角色、
 * 敏感级别、排序与启停。保存即生效；已有数据的字段不能改类型，系统字段不能停用。</p>
 */
const sections = SECTIONS
const activeSection = ref('basic')
const defs = ref<FieldDef[]>([])
const meta = ref<FieldMeta>({ types: [], sources: [], validators: [], roles: [] })
const loading = ref(false)
const saving = ref(false)
const error = ref<ApiError | null>(null)
const message = ref('')
const formOpen = ref(false)

const form = reactive<{
  id?: number
  fieldCode: string
  fieldName: string
  fieldType: string
  required: number
  source: string
  visibleRoles: string[]
  editableRoles: string[]
  validator: string
  ruleJson: string
  optionsJson: string
  sensitiveLevel: number
  sortNo?: number
  remark: string
}>({
  fieldCode: '',
  fieldName: '',
  fieldType: 'TEXT',
  required: 0,
  source: 'STU',
  visibleRoles: ['STU', 'COU'],
  editableRoles: ['STU'],
  validator: '',
  ruleJson: '',
  optionsJson: '',
  sensitiveLevel: 0,
  remark: '',
})

const isEnumType = computed(() => ['SELECT', 'MULTI_SELECT', 'RADIO', 'CHECKBOX'].includes(form.fieldType))
const editing = computed(() => form.id != null)

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    defs.value = await fetchFieldDefs(activeSection.value)
  } catch (e) {
    defs.value = []
    error.value = isApiError(e) ? e : null
  } finally {
    loading.value = false
  }
}

async function loadMeta(): Promise<void> {
  try {
    meta.value = await fetchFieldMeta()
  } catch {
    // 元数据失败不阻塞列表；下拉会退化为空
  }
}

function flash(text: string): void {
  message.value = text
  window.setTimeout(() => {
    if (message.value === text) message.value = ''
  }, 3500)
}

function openCreate(): void {
  resetForm()
  formOpen.value = true
}

function resetForm(): void {
  form.id = undefined
  form.fieldCode = ''
  form.fieldName = ''
  form.fieldType = 'TEXT'
  form.required = 0
  form.source = 'STU'
  form.visibleRoles = ['STU', 'COU']
  form.editableRoles = ['STU']
  form.validator = ''
  form.ruleJson = ''
  form.optionsJson = ''
  form.sensitiveLevel = 0
  form.sortNo = undefined
  form.remark = ''
}

function edit(row: FieldDef): void {
  form.id = row.id
  form.fieldCode = row.fieldCode
  form.fieldName = row.fieldName
  form.fieldType = row.fieldType
  form.required = row.required
  form.source = row.source
  form.visibleRoles = [...row.visibleRoles]
  form.editableRoles = [...row.editableRoles]
  form.validator = row.validator ?? ''
  form.ruleJson = row.ruleJson ?? ''
  form.optionsJson = row.optionsJson ?? ''
  form.sensitiveLevel = row.sensitiveLevel ?? 0
  form.sortNo = row.sortNo
  form.remark = row.remark ?? ''
  formOpen.value = true
}

async function save(): Promise<void> {
  if (!form.fieldName.trim() || (!editing.value && !form.fieldCode.trim())) return
  if (saving.value) return
  saving.value = true
  error.value = null
  try {
    const visible = form.visibleRoles.filter((role) => meta.value.roles.includes(role))
    const editable = form.editableRoles.filter((role) => visible.includes(role))
    const payload: FieldDefPayload = {
      sectionCode: activeSection.value.toUpperCase(),
      fieldCode: editing.value ? undefined : form.fieldCode.trim(),
      fieldName: form.fieldName.trim(),
      fieldType: form.fieldType,
      required: form.required,
      source: form.source,
      visibleRoles: visible,
      editableRoles: editable,
      validator: form.validator,
      ruleJson: form.ruleJson.trim() || undefined,
      optionsJson: isEnumType.value ? form.optionsJson.trim() || undefined : undefined,
      sensitiveLevel: form.sensitiveLevel,
      sortNo: form.sortNo,
      remark: form.remark.trim() || undefined,
    }
    if (editing.value && form.id) {
      await updateFieldDef(form.id, payload)
      flash('字段已保存，下一次表单请求即生效')
    } else {
      await createFieldDef(payload)
      flash('字段已新增')
    }
    formOpen.value = false
    resetForm()
    await load()
  } catch (e) {
    error.value = isApiError(e) ? e : null
  } finally {
    saving.value = false
  }
}

async function toggleRequired(row: FieldDef): Promise<void> {
  try {
    await updateFieldDefRequired(row.id, row.required === 1 ? 0 : 1)
    await load()
  } catch (e) {
    error.value = isApiError(e) ? e : null
  }
}

async function toggleStatus(row: FieldDef): Promise<void> {
  try {
    await updateFieldDefStatus(row.id, row.status === 1 ? 0 : 1)
    await load()
  } catch (e) {
    error.value = isApiError(e) ? e : null
  }
}

function onTypeChange(): void {
  if (!isEnumType.value) {
    form.optionsJson = ''
  }
  form.validator = ''
}

function toggleRole(role: string, target: 'visible' | 'editable'): void {
  const list = target === 'visible' ? form.visibleRoles : form.editableRoles
  const index = list.indexOf(role)
  if (index >= 0) list.splice(index, 1)
  else list.push(role)
  if (target === 'visible') {
    form.editableRoles = form.editableRoles.filter((item) => list.includes(item))
  }
}

watch(activeSection, () => {
  formOpen.value = false
  resetForm()
  void load()
})

onMounted(async () => {
  await Promise.all([load(), loadMeta()])
})
</script>

<template>
  <section class="admin">
    <header class="admin__head">
      <div>
        <h1>字段定义</h1>
        <p class="muted">维护表单字段、校验器与权限位；保存后下一次请求即生效，已有数据的字段不可改类型。</p>
      </div>
      <button class="btn" @click="openCreate">新增字段</button>
    </header>

    <p v-if="error" class="banner banner--error">{{ error.message }}（{{ error.code }}）</p>
    <p v-if="message" class="banner banner--ok">{{ message }}</p>

    <div class="tabs">
      <button
        v-for="section in sections"
        :key="section.code"
        class="tab"
        :class="{ 'tab--active': activeSection === section.code }"
        @click="activeSection = section.code"
      >{{ section.name }}</button>
    </div>

    <section v-if="formOpen" class="card">
      <h2>{{ editing ? '编辑字段' : '新增字段' }}</h2>
      <div class="form-grid">
        <label>字段编码<input v-model="form.fieldCode" :disabled="editing" placeholder="如 basic.dormNo" /></label>
        <label>字段名称<input v-model="form.fieldName" placeholder="界面显示名" /></label>
        <label>类型
          <select v-model="form.fieldType" @change="onTypeChange">
            <option v-for="type in meta.types" :key="type.code" :value="type.code">{{ type.label }}</option>
          </select>
        </label>
        <label>来源
          <select v-model="form.source">
            <option v-for="source in meta.sources" :key="source.code" :value="source.code">{{ source.label }}</option>
          </select>
        </label>
        <label>校验器
          <select v-model="form.validator">
            <option value="">按类型默认</option>
            <option v-for="validator in meta.validators" :key="validator" :value="validator">{{ validator }}</option>
          </select>
        </label>
        <label>必填
          <select v-model.number="form.required">
            <option :value="0">选填</option>
            <option :value="1">必填</option>
          </select>
        </label>
        <label>敏感级别
          <select v-model.number="form.sensitiveLevel">
            <option :value="0">普通</option>
            <option :value="1">脱敏展示</option>
            <option :value="2">仅辅导员明文</option>
          </select>
        </label>
        <label>排序<input v-model.number="form.sortNo" type="number" placeholder="留空自动追加" /></label>
      </div>

      <div class="roles">
        <span class="muted">可见角色：</span>
        <label v-for="role in meta.roles" :key="'v' + role" class="chip">
          <input type="checkbox" :checked="form.visibleRoles.includes(role)" @change="toggleRole(role, 'visible')" />
          {{ role }}
        </label>
        <span class="muted">可编辑角色：</span>
        <label v-for="role in meta.roles" :key="'e' + role" class="chip">
          <input
            type="checkbox"
            :checked="form.editableRoles.includes(role)"
            :disabled="!form.visibleRoles.includes(role)"
            @change="toggleRole(role, 'editable')"
          />
          {{ role }}
        </label>
      </div>

      <label v-if="isEnumType" class="block">选项 JSON
        <textarea v-model="form.optionsJson" rows="3" placeholder='[{"value":"M","label":"男"}]'></textarea>
      </label>
      <label class="block">校验规则 JSON
        <textarea v-model="form.ruleJson" rows="2" placeholder='{"minLen":2,"maxLen":32}'></textarea>
      </label>
      <label class="block">备注
        <input v-model="form.remark" placeholder="口径备注，可空" />
      </label>

      <div class="ops">
        <button class="btn" :disabled="saving" @click="save">{{ saving ? '保存中' : '保存' }}</button>
        <button class="btn btn--ghost" @click="formOpen = false">取消</button>
      </div>
    </section>

    <section class="card">
      <p v-if="loading" class="muted">加载中…</p>
      <table>
        <thead>
          <tr>
            <th>字段编码</th><th>名称</th><th>类型</th><th>必填</th><th>来源</th>
            <th>可见/可编辑</th><th>敏感</th><th>排序</th><th>状态</th><th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in defs" :key="row.id">
            <td>{{ row.fieldCode }}</td>
            <td>{{ row.fieldName }}</td>
            <td>{{ row.fieldTypeLabel }}</td>
            <td>
              <button class="btn btn--small btn--ghost" @click="toggleRequired(row)">
                {{ row.required === 1 ? '必填' : '选填' }}
              </button>
            </td>
            <td>{{ row.sourceLabel }}</td>
            <td>{{ row.visibleRoles.join('/') }} <span class="muted">|</span> {{ row.editableRoles.join('/') || '只读' }}</td>
            <td>{{ row.sensitiveLevel }}</td>
            <td>{{ row.sortNo }}</td>
            <td>{{ row.status === 1 ? '启用' : '停用' }}</td>
            <td class="ops">
              <button class="btn btn--small" @click="edit(row)">编辑</button>
              <button
                class="btn btn--small btn--ghost"
                :disabled="row.systemOwned && row.status === 1"
                @click="toggleStatus(row)"
              >{{ row.status === 1 ? '停用' : '启用' }}</button>
            </td>
          </tr>
          <tr v-if="!loading && defs.length === 0">
            <td colspan="10" class="empty">该板块暂无字段</td>
          </tr>
        </tbody>
      </table>
    </section>
  </section>
</template>

<style scoped>
.admin {
  padding: 24px;
  display: grid;
  gap: 16px;
}
.admin__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}
.admin__head h1 {
  margin: 0 0 4px;
  font-size: 22px;
}
.muted {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 13px;
}
.tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.tab {
  height: 32px;
  padding: 0 14px;
  border: 1px solid var(--color-border);
  background: #fff;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
}
.tab--active {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: #fff;
}
.card {
  background: var(--color-surface);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  padding: 18px;
}
.card h2 {
  margin: 0 0 12px;
  font-size: 16px;
}
.form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 10px;
}
label {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: var(--color-text-muted);
}
label.block {
  margin-top: 10px;
}
input,
select,
textarea {
  padding: 7px 10px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: #fff;
  font-size: 13px;
  color: var(--color-text);
}
.roles {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}
.chip {
  flex-direction: row;
  align-items: center;
  gap: 4px;
  padding: 5px 9px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
}
.ops {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}
table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
th,
td {
  text-align: left;
  padding: 8px 10px;
  border-bottom: 1px solid var(--color-border);
}
th {
  color: var(--color-text-muted);
  font-weight: 600;
}
.btn {
  height: 34px;
  padding: 0 14px;
  border: none;
  border-radius: 6px;
  background: var(--color-primary);
  color: #fff;
  cursor: pointer;
  font-size: 13px;
}
.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.btn--small {
  height: 28px;
  padding: 0 10px;
}
.btn--ghost {
  background: #fff;
  color: var(--color-text);
  border: 1px solid var(--color-border);
}
.banner {
  margin: 0;
  padding: 10px 12px;
  border-radius: 6px;
  font-size: 13px;
}
.banner--error {
  background: var(--color-danger-bg);
  color: var(--color-danger);
}
.banner--ok {
  background: var(--color-success-bg);
  color: var(--color-success);
}
.empty {
  text-align: center;
  color: var(--color-text-muted);
  padding: 20px;
}
</style>
