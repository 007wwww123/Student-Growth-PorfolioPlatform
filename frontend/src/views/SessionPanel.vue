<script setup lang="ts">
import { ref } from 'vue'
import { request } from '@/api/http'
import { accessToken, currentAcademicYear, currentStudentId, identity, type ArchiveIdentity } from '@/api/context'

const token = ref('')
const message = ref('')
const busy = ref(false)
async function login() {
  busy.value = true
  message.value = ''
  accessToken.value = token.value.trim()
  try {
    const account = await request<ArchiveIdentity>({ url: '/auth/me' })
    currentAcademicYear.value = account.academicYears[0] ?? ''
    currentStudentId.value = account.studentIds[0] ?? 0
    identity.value = account
    token.value = ''
  } catch (e) {
    accessToken.value = ''
    message.value = e instanceof Error ? e.message : '登录失败'
  } finally { busy.value = false }
}
async function logout() {
  busy.value = true
  try {
    await request({url: '/auth/logout', method: 'POST'})
    message.value = ''
  } catch {
    message.value = '已清除本页登录信息，服务端令牌撤销结果未确认。'
  } finally {
    accessToken.value = ''
    identity.value = null
    currentStudentId.value = 0
    currentAcademicYear.value = ''
    busy.value = false
  }
}
</script>

<template>
  <form v-if="!identity" @submit.prevent="login">
    <label>访问令牌 <input v-model="token" type="password" autocomplete="off" required /></label>
    <button :disabled="busy">登录</button>
  </form>
  <div v-else>
    <label>学生 <select v-model="currentStudentId"><option v-for="id in identity.studentIds" :key="id" :value="id">{{ id }}</option></select></label>
    <label>学年 <select v-model="currentAcademicYear"><option v-for="year in identity.academicYears" :key="year">{{ year }}</option></select></label>
    <button :disabled="busy" @click="logout">退出登录</button>
  </div>
  <p v-if="message" role="alert">{{ message }}</p>
</template>
