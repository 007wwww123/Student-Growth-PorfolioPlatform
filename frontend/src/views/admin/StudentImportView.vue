<script setup lang="ts">
import { onMounted, ref } from 'vue'
import {
  downloadExcel,
  executeStudentImport,
  fetchImportTasks,
  previewStudents,
  type ImportPreview,
  type ImportResult,
  type ImportTask,
} from '@/api/import'
import { isApiError, type ApiError } from '@/api/http'

/**
 * 学生批量导入（F-09 / S-2 / AC-030–032）。
 *
 * <p>模板 → 上传预览（前 20 行 + 错误原因）→ 确认导入 → 回执下载。
 * 服务端逐行校验、部分成功；辅导员只能导入本班。</p>
 */
const file = ref<File | null>(null)
const preview = ref<ImportPreview | null>(null)
const result = ref<ImportResult | null>(null)
const tasks = ref<ImportTask[]>([])
const loading = ref(false)
const error = ref<ApiError | null>(null)
const message = ref('')

function onFile(event: Event): void {
  const input = event.target as HTMLInputElement
  file.value = input.files && input.files.length > 0 ? input.files[0] : null
  preview.value = null
  result.value = null
  error.value = null
}

async function loadTasks(): Promise<void> {
  try {
    tasks.value = await fetchImportTasks()
  } catch {
    tasks.value = []
  }
}

async function doPreview(): Promise<void> {
  if (!file.value) return
  loading.value = true
  error.value = null
  result.value = null
  try {
    preview.value = await previewStudents(file.value)
  } catch (e) {
    preview.value = null
    error.value = isApiError(e) ? e : null
  } finally {
    loading.value = false
  }
}

async function doExecute(): Promise<void> {
  if (!file.value) return
  loading.value = true
  error.value = null
  try {
    result.value = await executeStudentImport(file.value)
    preview.value = null
    message.value = `导入完成：成功 ${result.value.successRows} 行，失败 ${result.value.failedRows} 行`
    await loadTasks()
  } catch (e) {
    result.value = null
    error.value = isApiError(e) ? e : null
  } finally {
    loading.value = false
  }
}

function statusLabel(status: string): string {
  return status === 'SUCCESS' ? '全部成功' : status === 'PARTIAL' ? '部分成功' : '全部失败'
}

function formatTime(value?: string): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '—'
}

onMounted(loadTasks)
</script>

<template>
  <section class="admin">
    <header class="admin__head">
      <div>
        <h1>学生导入</h1>
        <p class="muted">上传 .xlsx 模板，先预览校验结果，再确认导入；合法行入库，非法行生成回执。</p>
      </div>
      <button class="btn btn--ghost" @click="downloadExcel('/admin/import/students/template', 'student-import-template.xlsx')">
        下载模板
      </button>
    </header>

    <p v-if="error" class="banner banner--error">{{ error.message }}（{{ error.code }}）</p>
    <p v-if="message" class="banner banner--ok">{{ message }}</p>

    <section class="card">
      <h2>1. 选择文件并预览</h2>
      <div class="row">
        <input type="file" accept=".xlsx" @change="onFile" />
        <button class="btn" :disabled="!file || loading" @click="doPreview">{{ loading ? '处理中…' : '预览校验' }}</button>
      </div>
      <p class="muted">表头顺序：学号、姓名、性别、民族、身份证号、籍贯、班级编码、入学年份；最多 2000 行。</p>

      <div v-if="preview" class="preview">
        <p>
          共 <strong>{{ preview.totalRows }}</strong> 行，合法 <strong>{{ preview.validRows }}</strong> 行，
          非法 <strong>{{ preview.invalidRows }}</strong> 行。以下为前 20 行：
        </p>
        <table>
          <thead><tr><th>行号</th><th>学号</th><th>姓名</th><th>校验</th><th>错误原因</th></tr></thead>
          <tbody>
            <tr v-for="row in preview.rows" :key="row.rowNo" :class="{ 'row--bad': !row.valid }">
              <td>{{ row.rowNo }}</td>
              <td>{{ row.studentNo || '—' }}</td>
              <td>{{ row.name || '—' }}</td>
              <td>{{ row.valid ? '通过' : '不通过' }}</td>
              <td>{{ row.errors.join('；') || '—' }}</td>
            </tr>
          </tbody>
        </table>
        <button class="btn" :disabled="loading" @click="doExecute">
          {{ loading ? '导入中…' : `确认导入（${preview.validRows} 行合法）` }}
        </button>
      </div>
    </section>

    <section v-if="result" class="card">
      <h2>2. 导入结果</h2>
      <p>
        任务 #{{ result.taskId }}：共 {{ result.totalRows }} 行，成功 {{ result.successRows }} 行，
        失败 {{ result.failedRows }} 行（{{ statusLabel(result.status) }}）。
        <button class="btn btn--small btn--ghost" @click="downloadExcel(`/admin/import/tasks/${result.taskId}/receipt`, `student-import-receipt-${result.taskId}.xlsx`)">
          下载回执
        </button>
      </p>
      <table v-if="result.errors.length > 0">
        <thead><tr><th>行号</th><th>学号</th><th>姓名</th><th>错误原因</th></tr></thead>
        <tbody>
          <tr v-for="row in result.errors" :key="row.rowNo" class="row--bad">
            <td>{{ row.rowNo }}</td>
            <td>{{ row.studentNo || '—' }}</td>
            <td>{{ row.name || '—' }}</td>
            <td>{{ row.errors.join('；') }}</td>
          </tr>
        </tbody>
      </table>
    </section>

    <section class="card">
      <h2>3. 导入记录</h2>
      <table>
        <thead><tr><th>任务</th><th>文件</th><th>总数</th><th>成功</th><th>失败</th><th>状态</th><th>时间</th><th>回执</th></tr></thead>
        <tbody>
          <tr v-for="task in tasks" :key="task.id">
            <td>#{{ task.id }}</td>
            <td>{{ task.fileName || '—' }}</td>
            <td>{{ task.totalRows }}</td>
            <td>{{ task.successRows }}</td>
            <td>{{ task.failedRows }}</td>
            <td>{{ statusLabel(task.status) }}</td>
            <td>{{ formatTime(task.createdAt) }}</td>
            <td>
              <button class="btn btn--small btn--ghost" @click="downloadExcel(`/admin/import/tasks/${task.id}/receipt`, `student-import-receipt-${task.id}.xlsx`)">
                下载
              </button>
            </td>
          </tr>
          <tr v-if="tasks.length === 0"><td colspan="8" class="empty">暂无导入记录</td></tr>
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
.row {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 8px;
}
.preview {
  margin-top: 12px;
}
table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
  margin-bottom: 12px;
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
.row--bad {
  background: var(--color-danger-bg);
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
  height: 26px;
  padding: 0 8px;
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
  padding: 18px;
}
</style>
