<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import {
  fetchNotifications,
  fetchUnreadCount,
  markAllNotificationsRead,
  markNotificationRead,
  type Notification,
} from '@/api/notifications'
import { isApiError, type ApiError } from '@/api/http'

/**
 * 顶部通知铃铛（S-1-05/07）。
 *
 * <p>60 秒轮询未读数；展开时拉最近 20 条；点击单条标记已读，支持全部已读。</p>
 */
const open = ref(false)
const unread = ref(0)
const rows = ref<Notification[]>([])
const error = ref<ApiError | null>(null)
let timer: ReturnType<typeof setInterval> | undefined

async function loadCount(): Promise<void> {
  try {
    unread.value = await fetchUnreadCount()
  } catch {
    // 静默失败，不打断页面
  }
}

async function loadList(): Promise<void> {
  try {
    const page = await fetchNotifications(false, 20)
    rows.value = page.records
    unread.value = page.records.filter((row) => !row.read).length
  } catch (e) {
    error.value = isApiError(e) ? e : null
  }
}

async function toggle(): Promise<void> {
  open.value = !open.value
  if (open.value) await loadList()
}

async function onRead(row: Notification): Promise<void> {
  if (row.read) return
  try {
    await markNotificationRead(row.id)
    row.read = true
    unread.value = Math.max(0, unread.value - 1)
  } catch (e) {
    error.value = isApiError(e) ? e : null
  }
}

async function onReadAll(): Promise<void> {
  try {
    await markAllNotificationsRead()
    rows.value.forEach((row) => (row.read = true))
    unread.value = 0
  } catch (e) {
    error.value = isApiError(e) ? e : null
  }
}

function formatTime(value: string): string {
  return value.replace('T', ' ').slice(0, 16)
}

onMounted(() => {
  void loadCount()
  timer = setInterval(loadCount, 60000)
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <div class="bell">
    <button type="button" class="bell__trigger" :aria-expanded="open" @click="toggle">
      <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true">
        <path d="M5 8a5 5 0 0 1 10 0v3l1.5 2.5H3.5L5 11V8Z" stroke-linejoin="round" />
        <path d="M8 16a2 2 0 0 0 4 0" stroke-linecap="round" />
      </svg>
      <span v-if="unread > 0" class="bell__badge">{{ unread > 99 ? '99+' : unread }}</span>
    </button>

    <div v-if="open" class="bell__panel">
      <header class="bell__head">
        <strong>通知</strong>
        <button class="bell__link" type="button" @click="onReadAll">全部已读</button>
      </header>
      <p v-if="error" class="bell__error">{{ error.message }}</p>
      <ul class="bell__list">
        <li v-for="row in rows" :key="row.id" :class="{ 'is-unread': !row.read }" @click="onRead(row)">
          <p class="bell__title">{{ row.title }}</p>
          <p v-if="row.content" class="bell__content">{{ row.content }}</p>
          <span class="bell__time">{{ formatTime(row.createdAt) }}</span>
        </li>
        <li v-if="rows.length === 0" class="bell__empty">暂无通知</li>
      </ul>
    </div>
  </div>
</template>

<style scoped>
.bell { position: relative; }
.bell__trigger {
  position: relative;
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border: 1px solid var(--color-border);
  border-radius: 50%;
  background: #fff;
  cursor: pointer;
  color: var(--color-text);
}
.bell__trigger svg { width: 18px; height: 18px; }
.bell__badge {
  position: absolute;
  top: -4px;
  right: -4px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 8px;
  background: var(--color-danger);
  color: #fff;
  font-size: 10px;
  line-height: 16px;
  text-align: center;
}
.bell__panel {
  position: absolute;
  top: 42px;
  right: 0;
  width: 320px;
  max-height: 420px;
  overflow: auto;
  background: #fff;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  box-shadow: var(--shadow);
  z-index: 50;
}
.bell__head { display: flex; justify-content: space-between; align-items: center; padding: 10px 12px; border-bottom: 1px solid var(--color-border); }
.bell__link { border: none; background: none; color: var(--color-primary); cursor: pointer; font-size: 12px; }
.bell__list { list-style: none; margin: 0; padding: 0; }
.bell__list li { padding: 10px 12px; border-bottom: 1px solid var(--color-border); cursor: pointer; }
.bell__list li.is-unread { background: var(--color-primary-soft, #f2f7ff); }
.bell__title { margin: 0 0 4px; font-size: 13px; }
.bell__content { margin: 0 0 4px; font-size: 12px; color: var(--color-text-muted); }
.bell__time { font-size: 11px; color: var(--color-text-muted); }
.bell__empty { text-align: center; color: var(--color-text-muted); cursor: default; }
.bell__error { margin: 8px 12px; color: var(--color-danger); font-size: 12px; }
</style>
