<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { currentStudentId, currentAcademicYear, identity, mockMode } from '@/api/context'
import { request } from '@/api/http'
import { fetchSection } from '@/api/sections'
import type { ProfileStatus, SectionCode, SectionVO } from '@/api/types'
import { SECTIONS, SECTION_MAP, STATUS_LABELS } from '@/sectionMeta'

interface Entry { code: SectionCode; status: ProfileStatus; version: number }
const entries = ref<Entry[]>([])
const selected = ref<SectionVO>()
const reason = ref('')
const message = ref('')
const busy = ref(false)
const url = '/workflow/' + currentStudentId.value
const params = { academicYear: currentAcademicYear.value }
const canArchive = computed(() => SECTIONS.every(s => entries.value.some(e => e.code === s.code && e.status === 'APPROVED')))
const mayReview = computed(() => identity.value?.role === 'COUNSELOR')
const mayWithdraw = computed(() => selected.value?.sectionCode === 'MENTOR'
  ? identity.value?.role === 'MENTOR' : identity.value?.role === 'STUDENT')

async function run(operation: () => Promise<void>) {
  if (busy.value) return
  busy.value = true
  message.value = ''
  try { await operation() }
  catch (e) { message.value = e instanceof Error ? e.message : '操作失败' }
  finally { busy.value = false }
}
async function reload() {
  selected.value = undefined
  entries.value = await request<Entry[]>({url, params})
}
async function inspect(code: SectionCode) {
  selected.value = undefined
  reason.value = ''
  selected.value = await fetchSection(code, currentStudentId.value, params.academicYear)
}
async function decide(action: 'approve' | 'return' | 'withdraw') {
  if (!selected.value || selected.value.version == null) return
  const code = selected.value.sectionCode.toLowerCase()
  await request({url: url + '/' + code + '/' + action, params, method: 'POST',
    data: {reason: reason.value}, headers: {'If-Match': String(selected.value.version)}})
  await reload()
}
async function archive() {
  const versions = Object.fromEntries(entries.value.map(e => [e.code, e.version]))
  await request({url: url + '/archive', method: 'POST', params, data: versions})
  await reload()
}
onMounted(() => { if (!mockMode) void run(reload) })
</script>

<template>
  <section>
    <h1>审核与归档</h1>
    <p v-if="mockMode">此流程需要登录真实服务。</p>
    <template v-else>
      <p v-if="message" role="alert">{{ message }}</p>
      <button :disabled="busy" @click="run(reload)">刷新进度</button>
      <p v-if="!entries.length && !busy">当前学生本学年还没有已保存的板块。</p>
      <ul><li v-for="e in entries" :key="e.code">
        <button :disabled="busy" @click="run(() => inspect(e.code))">{{ SECTION_MAP[e.code].name }}</button>
        {{ STATUS_LABELS[e.status] }}
      </li></ul>
      <article v-if="selected">
        <h2>{{ selected.sectionName }} · {{ STATUS_LABELS[selected.status] }}</h2>
        <dl><template v-for="f in selected.fieldDefinitions" :key="f.key">
          <dt>{{ f.label }}</dt><dd>{{ selected.fields[f.key] ?? '未填写' }}</dd>
        </template></dl>
        <template v-if="selected.status === 'SUBMITTED'">
          <template v-if="mayReview">
            <button :disabled="busy" @click="run(() => decide('approve'))">审核通过</button>
            <label>退回理由 <textarea v-model="reason" maxlength="500" /></label>
            <button :disabled="busy || Array.from(reason.trim()).length < 10" @click="run(() => decide('return'))">退回修改</button>
          </template>
          <button v-if="mayWithdraw" :disabled="busy" @click="run(() => decide('withdraw'))">撤回提交</button>
        </template>
      </article>
      <button v-if="identity?.role === 'ADMIN'" :disabled="busy || !canArchive" @click="run(archive)">归档六个板块</button>
    </template>
  </section>
</template>
