<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { addDictItem, fetchDicts, updateDictItemStatus, type Dict } from '@/api/dicts'
import { isApiError, type ApiError } from '@/api/http'

/**
 * 数据字典维护（F-15 / AC-008）。
 *
 * <p>停用不删除：历史值保留 label，新填报按停用项拒绝。绑定字段的选项会同步到
 * `arc_field_def.options_json`。</p>
 */
const dicts = ref<Dict[]>([])
const loading = ref(false)
const error = ref<ApiError | null>(null)
const message = ref('')
const adding = reactive<Record<string, { value: string; label: string; sortNo: number }>>({})

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    dicts.value = await fetchDicts()
    for (const dict of dicts.value) {
      if (!adding[dict.code]) adding[dict.code] = { value: '', label: '', sortNo: 100 }
    }
  } catch (e) {
    error.value = isApiError(e) ? e : null
  } finally {
    loading.value = false
  }
}

async function onAdd(dict: Dict): Promise<void> {
  const form = adding[dict.code]
  if (!form.value.trim() || !form.label.trim()) {
    message.value = '字典值和标签都不能为空'
    return
  }
  error.value = null
  try {
    await addDictItem(dict.code, form.value.trim(), form.label.trim(), Number(form.sortNo) || 0)
    message.value = `已新增「${form.label.trim()}」`
    form.value = ''
    form.label = ''
    await load()
  } catch (e) {
    error.value = isApiError(e) ? e : null
  }
}

async function toggle(dict: Dict, itemId: number, next: number): Promise<void> {
  error.value = null
  try {
    await updateDictItemStatus(dict.code, itemId, next)
    message.value = next === 1 ? '已启用' : '已停用；历史值仍显示并标注“已停用”'
    await load()
  } catch (e) {
    error.value = isApiError(e) ? e : null
  }
}

onMounted(load)
</script>

<template>
  <section class="admin">
    <header class="admin__head">
      <div>
        <h1>数据字典</h1>
        <p class="muted">维护民族、证书类型、兴趣等可配置选项；停用项不删除，历史记录仍可显示。</p>
      </div>
    </header>

    <p v-if="error" class="banner banner--error">{{ error.message }}（{{ error.code }}）</p>
    <p v-if="message" class="banner">{{ message }}</p>

    <section v-for="dict in dicts" :key="dict.code" class="card">
      <div class="card__head">
        <h3>{{ dict.name }} <span class="muted">（{{ dict.code }}<template v-if="dict.fieldCode"> → {{ dict.fieldCode }}</template>）</span></h3>
      </div>
      <table>
        <thead><tr><th>值</th><th>标签</th><th>排序</th><th>状态</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="item in dict.items" :key="item.id">
            <td>{{ item.value }}</td>
            <td>{{ item.label }}</td>
            <td>{{ item.sortNo }}</td>
            <td>{{ item.disabled ? '已停用' : '启用' }}</td>
            <td>
              <button class="btn btn--small btn--ghost" @click="toggle(dict, item.id, item.disabled ? 1 : 0)">
                {{ item.disabled ? '启用' : '停用' }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
      <div class="add">
        <input v-model="adding[dict.code].value" placeholder="值，如 摄影" />
        <input v-model="adding[dict.code].label" placeholder="标签，如 摄影" />
        <input v-model.number="adding[dict.code].sortNo" type="number" placeholder="排序" />
        <button class="btn" @click="onAdd(dict)">新增选项</button>
      </div>
    </section>

    <p v-if="!loading && dicts.length === 0" class="muted">暂无字典</p>
  </section>
</template>

<style scoped>
.admin { padding: 24px; display: grid; gap: 16px; }
.admin__head h1 { margin: 0 0 4px; font-size: 22px; }
.muted { margin: 0; color: var(--color-text-muted); font-size: 13px; }
.card { background: var(--color-surface); border-radius: var(--radius); box-shadow: var(--shadow); padding: 18px; }
.card__head h3 { margin: 0 0 10px; font-size: 15px; }
table { width: 100%; border-collapse: collapse; font-size: 13px; }
th, td { text-align: left; padding: 8px 10px; border-bottom: 1px solid var(--color-border); }
th { color: var(--color-text-muted); font-weight: 600; }
.add { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 12px; }
input { height: 32px; padding: 0 10px; border: 1px solid var(--color-border); border-radius: 6px; font-size: 13px; }
.btn { height: 32px; padding: 0 12px; border: none; border-radius: 6px; background: var(--color-primary); color: #fff; cursor: pointer; font-size: 13px; }
.btn--small { height: 26px; padding: 0 8px; }
.btn--ghost { background: #fff; color: var(--color-text); border: 1px solid var(--color-border); }
.banner { margin: 0; padding: 10px 12px; border-radius: 6px; background: var(--color-surface); font-size: 13px; }
.banner--error { background: var(--color-danger-bg); color: var(--color-danger); }
</style>
