<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import {
  assignCounselor,
  createClass,
  createCollege,
  createMajor,
  fetchOrgOptions,
  updateClass,
  updateClassStatus,
  updateCollege,
  updateCollegeStatus,
  updateMajor,
  updateMajorStatus,
  type OrgClassRow,
  type OrgCollege,
  type OrgMajor,
  type OrgOptions,
} from '@/api/org'
import { isApiError, type ApiError } from '@/api/http'

/**
 * 组织架构（F-02 / AC-005）。
 *
 * <p>学院 → 专业 → 班级三级维护；停用受服务端保护（有下级时拒绝），
 * 班级绑定辅导员后立即决定该班的审核权。</p>
 */
const options = ref<OrgOptions>({ colleges: [], majors: [], classes: [], counselors: [] })
const loading = ref(false)
const error = ref<ApiError | null>(null)
const message = ref('')

const collegeForm = reactive<{ id?: number; collegeCode: string; collegeName: string }>({
  collegeCode: '',
  collegeName: '',
})
const majorForm = reactive<{ id?: number; collegeId?: number; majorCode: string; majorName: string }>({
  majorCode: '',
  majorName: '',
})
const classForm = reactive<{
  id?: number
  classCode: string
  className: string
  majorId?: number
  gradeYear: number
  counselorUserId?: number
}>({
  classCode: '',
  className: '',
  gradeYear: new Date().getFullYear(),
})

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    options.value = await fetchOrgOptions()
    if (!majorForm.collegeId && options.value.colleges.length > 0) {
      majorForm.collegeId = options.value.colleges[0].id
    }
    if (!classForm.majorId && options.value.majors.length > 0) {
      classForm.majorId = options.value.majors[0].id
    }
  } catch (e) {
    error.value = isApiError(e) ? e : null
  } finally {
    loading.value = false
  }
}

function flash(text: string): void {
  message.value = text
  window.setTimeout(() => {
    if (message.value === text) message.value = ''
  }, 3000)
}

async function saveCollege(): Promise<void> {
  if (!collegeForm.collegeCode.trim() || !collegeForm.collegeName.trim()) return
  try {
    if (collegeForm.id) {
      await updateCollege(collegeForm.id, {
        collegeCode: collegeForm.collegeCode.trim(),
        collegeName: collegeForm.collegeName.trim(),
      })
      flash('学院已更新')
    } else {
      await createCollege({
        collegeCode: collegeForm.collegeCode.trim(),
        collegeName: collegeForm.collegeName.trim(),
      })
      flash('学院已新增')
    }
    resetCollege()
    await load()
  } catch (e) {
    error.value = isApiError(e) ? e : null
  }
}

function editCollege(row: OrgCollege): void {
  collegeForm.id = row.id
  collegeForm.collegeCode = row.collegeCode
  collegeForm.collegeName = row.collegeName
}

function resetCollege(): void {
  collegeForm.id = undefined
  collegeForm.collegeCode = ''
  collegeForm.collegeName = ''
}

async function toggleCollege(row: OrgCollege): Promise<void> {
  try {
    await updateCollegeStatus(row.id, row.status === 1 ? 0 : 1)
    await load()
  } catch (e) {
    error.value = isApiError(e) ? e : null
  }
}

async function saveMajor(): Promise<void> {
  if (!majorForm.collegeId || !majorForm.majorCode.trim() || !majorForm.majorName.trim()) return
  try {
    const payload = {
      collegeId: majorForm.collegeId,
      majorCode: majorForm.majorCode.trim(),
      majorName: majorForm.majorName.trim(),
    }
    if (majorForm.id) {
      await updateMajor(majorForm.id, payload)
      flash('专业已更新')
    } else {
      await createMajor(payload)
      flash('专业已新增')
    }
    resetMajor()
    await load()
  } catch (e) {
    error.value = isApiError(e) ? e : null
  }
}

function editMajor(row: OrgMajor): void {
  majorForm.id = row.id
  majorForm.collegeId = row.collegeId
  majorForm.majorCode = row.majorCode
  majorForm.majorName = row.majorName
}

function resetMajor(): void {
  majorForm.id = undefined
  majorForm.majorCode = ''
  majorForm.majorName = ''
}

async function toggleMajor(row: OrgMajor): Promise<void> {
  try {
    await updateMajorStatus(row.id, row.status === 1 ? 0 : 1)
    await load()
  } catch (e) {
    error.value = isApiError(e) ? e : null
  }
}

async function saveClass(): Promise<void> {
  if (!classForm.classCode.trim() || !classForm.className.trim() || !classForm.majorId) return
  try {
    const payload = {
      classCode: classForm.classCode.trim(),
      className: classForm.className.trim(),
      majorId: classForm.majorId,
      gradeYear: classForm.gradeYear,
      counselorUserId: classForm.counselorUserId,
    }
    if (classForm.id) {
      await updateClass(classForm.id, payload)
      flash('班级已更新')
    } else {
      await createClass(payload)
      flash('班级已新增')
    }
    resetClass()
    await load()
  } catch (e) {
    error.value = isApiError(e) ? e : null
  }
}

function editClass(row: OrgClassRow): void {
  classForm.id = row.id
  classForm.classCode = row.classCode
  classForm.className = row.className
  classForm.majorId = row.majorId
  classForm.gradeYear = row.gradeYear
  classForm.counselorUserId = row.counselorUserId
}

function resetClass(): void {
  classForm.id = undefined
  classForm.classCode = ''
  classForm.className = ''
  classForm.gradeYear = new Date().getFullYear()
  classForm.counselorUserId = undefined
}

async function toggleClass(row: OrgClassRow): Promise<void> {
  try {
    await updateClassStatus(row.id, row.status === 1 ? 0 : 1)
    await load()
  } catch (e) {
    error.value = isApiError(e) ? e : null
  }
}

async function changeCounselor(row: OrgClassRow): Promise<void> {
  if (!row.counselorUserId) return
  try {
    await assignCounselor(row.id, row.counselorUserId)
    flash('辅导员已绑定：' + row.classCode)
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
        <h1>组织架构</h1>
        <p class="muted">维护学院、专业、班级与辅导员绑定；停用有下级的节点会被拒绝。</p>
      </div>
      <span class="tag">{{ loading ? '加载中' : '管理员' }}</span>
    </header>

    <p v-if="error" class="banner banner--error">{{ error.message }}（{{ error.code }}）</p>
    <p v-if="message" class="banner banner--ok">{{ message }}</p>

    <section class="card">
      <h2>学院</h2>
      <div class="form">
        <input v-model="collegeForm.collegeCode" placeholder="学院编码，如 ZJGCS" />
        <input v-model="collegeForm.collegeName" placeholder="学院名称" />
        <button class="btn" @click="saveCollege">{{ collegeForm.id ? '保存' : '新增' }}</button>
        <button v-if="collegeForm.id" class="btn btn--ghost" @click="resetCollege">取消</button>
      </div>
      <table>
        <thead>
          <tr><th>编码</th><th>名称</th><th>专业数</th><th>状态</th><th>操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="row in options.colleges" :key="row.id">
            <td>{{ row.collegeCode }}</td>
            <td>{{ row.collegeName }}</td>
            <td>{{ row.majorCount ?? 0 }}</td>
            <td>{{ row.status === 1 ? '启用' : '停用' }}</td>
            <td class="ops">
              <button class="btn btn--small" @click="editCollege(row)">编辑</button>
              <button class="btn btn--small btn--ghost" @click="toggleCollege(row)">
                {{ row.status === 1 ? '停用' : '启用' }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <section class="card">
      <h2>专业</h2>
      <div class="form">
        <select v-model="majorForm.collegeId">
          <option v-for="c in options.colleges" :key="c.id" :value="c.id">{{ c.collegeName }}</option>
        </select>
        <input v-model="majorForm.majorCode" placeholder="专业编码" />
        <input v-model="majorForm.majorName" placeholder="专业名称" />
        <button class="btn" @click="saveMajor">{{ majorForm.id ? '保存' : '新增' }}</button>
        <button v-if="majorForm.id" class="btn btn--ghost" @click="resetMajor">取消</button>
      </div>
      <table>
        <thead>
          <tr><th>编码</th><th>名称</th><th>学院</th><th>状态</th><th>操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="row in options.majors" :key="row.id">
            <td>{{ row.majorCode }}</td>
            <td>{{ row.majorName }}</td>
            <td>{{ row.collegeName }}</td>
            <td>{{ row.status === 1 ? '启用' : '停用' }}</td>
            <td class="ops">
              <button class="btn btn--small" @click="editMajor(row)">编辑</button>
              <button class="btn btn--small btn--ghost" @click="toggleMajor(row)">
                {{ row.status === 1 ? '停用' : '启用' }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <section class="card">
      <h2>班级</h2>
      <div class="form">
        <input v-model="classForm.classCode" placeholder="班级编码，如 RJ2401" />
        <input v-model="classForm.className" placeholder="班级名称" />
        <select v-model="classForm.majorId">
          <option v-for="m in options.majors" :key="m.id" :value="m.id">{{ m.majorName }}</option>
        </select>
        <input v-model.number="classForm.gradeYear" type="number" placeholder="年级" />
        <select v-model="classForm.counselorUserId">
          <option :value="undefined">未绑定辅导员</option>
          <option v-for="u in options.counselors" :key="u.userId" :value="u.userId">
            {{ u.displayName }}（{{ u.username }}）
          </option>
        </select>
        <button class="btn" @click="saveClass">{{ classForm.id ? '保存' : '新增' }}</button>
        <button v-if="classForm.id" class="btn btn--ghost" @click="resetClass">取消</button>
      </div>
      <table>
        <thead>
          <tr>
            <th>编码</th><th>名称</th><th>专业</th><th>年级</th><th>学生数</th>
            <th>辅导员</th><th>状态</th><th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in options.classes" :key="row.id">
            <td>{{ row.classCode }}</td>
            <td>{{ row.className }}</td>
            <td>{{ row.majorName }}</td>
            <td>{{ row.gradeYear }}</td>
            <td>{{ row.studentCount }}</td>
            <td>
              <select v-model="row.counselorUserId" @change="changeCounselor(row)">
                <option :value="undefined">未绑定</option>
                <option v-for="u in options.counselors" :key="u.userId" :value="u.userId">
                  {{ u.displayName }}
                </option>
              </select>
            </td>
            <td>{{ row.status === 1 ? '启用' : '停用' }}</td>
            <td class="ops">
              <button class="btn btn--small" @click="editClass(row)">编辑</button>
              <button class="btn btn--small btn--ghost" @click="toggleClass(row)">
                {{ row.status === 1 ? '停用' : '启用' }}
              </button>
            </td>
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
.form {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}
input,
select {
  height: 34px;
  padding: 0 10px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: #fff;
  font-size: 13px;
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
.ops {
  display: flex;
  gap: 6px;
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
</style>
