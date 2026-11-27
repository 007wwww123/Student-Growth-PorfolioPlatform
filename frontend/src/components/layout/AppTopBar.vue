<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { logout } from '@/api/auth'
import { clearSession, getUser } from '@/auth/session'

/**
 * 顶部固定栏。
 *
 * <p>所有角色共用同一套结构：左侧平台名称，右侧当前登录账号。
 * 账号区悬停展开下拉，目前只有「退出登录」一项。</p>
 *
 * <p>下拉用悬停 + 点击双通道：鼠标悬停是主交互；点击、键盘 Enter/Space 也能展开，
 * 便于触屏与键盘用户。移出时延迟 140ms 再关，避免指针从按钮移向菜单的途中闪断。</p>
 */
const route = useRoute()
const router = useRouter()

const user = getUser()

const expanded = ref(false)

let closeTimer: ReturnType<typeof setTimeout> | undefined

const initial = computed(() => (user?.displayName ?? user?.username ?? '?').slice(0, 1))

function clearCloseTimer(): void {
  if (closeTimer != null) {
    clearTimeout(closeTimer)
    closeTimer = undefined
  }
}

function expand(): void {
  clearCloseTimer()
  expanded.value = true
}

function scheduleCollapse(): void {
  clearCloseTimer()
  closeTimer = setTimeout(() => {
    expanded.value = false
    closeTimer = undefined
  }, 140)
}

function collapse(): void {
  clearCloseTimer()
  expanded.value = false
}

function toggle(): void {
  if (expanded.value) {
    collapse()
  } else {
    expand()
  }
}

async function onLogout(): Promise<void> {
  collapse()
  try {
    await logout()
  } catch {
    // 令牌可能已过期，登出失败不影响本地清理
  } finally {
    clearSession()
    await router.replace({ name: 'login' })
  }
}

/** 页面切换后菜单不该继续悬着。 */
watch(() => route.fullPath, collapse)

onBeforeUnmount(clearCloseTimer)
</script>

<template>
  <header class="topbar">
    <div class="topbar__left">
      <span class="topbar__mark" aria-hidden="true">档</span>
      <span class="topbar__name">学生成长档案平台</span>
    </div>

    <div v-if="user" class="topbar__right">
      <div class="account" @mouseenter="expand" @mouseleave="scheduleCollapse">
        <button
          type="button"
          class="account__trigger"
          :class="{ 'is-open': expanded }"
          :aria-expanded="expanded"
          aria-haspopup="menu"
          @click="toggle"
          @keydown.esc="collapse"
        >
          <span class="account__avatar" aria-hidden="true">{{ initial }}</span>
          <span class="account__text">
            <span class="account__name">{{ user.displayName }}</span>
            <span class="account__username">{{ user.username }}</span>
          </span>
          <svg
            class="account__caret"
            viewBox="0 0 16 16"
            fill="none"
            stroke="currentColor"
            stroke-width="1.5"
            stroke-linecap="round"
            stroke-linejoin="round"
            aria-hidden="true"
          >
            <path d="M5 6.5 8 9.5l3-3" />
          </svg>
        </button>

        <transition name="account-menu">
          <div v-if="expanded" class="account__menu" role="menu" @keydown.esc="collapse">
            <button type="button" class="account__item" role="menuitem" @click="onLogout">
              <svg
                class="account__item-icon"
                viewBox="0 0 20 20"
                fill="none"
                stroke="currentColor"
                stroke-width="1.5"
                stroke-linecap="round"
                stroke-linejoin="round"
                aria-hidden="true"
              >
                <path d="M8 3.5H4.5v13H8" />
                <path d="M12 6.5 15.5 10 12 13.5" />
                <path d="M15.5 10H8" />
              </svg>
              退出登录
            </button>
          </div>
        </transition>
      </div>
    </div>
  </header>
</template>

<style scoped>
.topbar {
  position: fixed;
  top: 0;
  right: 0;
  left: 0;
  z-index: 30;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  height: var(--layout-topbar-height);
  padding: 0 24px;
  background: var(--color-surface);
  border-bottom: 1px solid var(--color-border);
}

.topbar__left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.topbar__mark {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border-radius: 7px;
  background: var(--color-primary);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0;
}

.topbar__name {
  overflow: hidden;
  color: var(--color-text);
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.3px;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.topbar__right {
  display: flex;
  align-items: center;
}

/* 悬停热区包住按钮和菜单，两者之间留 6px 仍属同一区域，不会触发 mouseleave */
.account {
  position: relative;
}

.account__trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px 4px 5px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: transparent;
  color: inherit;
  transition: background-color 0.15s ease, border-color 0.15s ease;
}

.account__trigger:hover,
.account__trigger.is-open {
  border-color: var(--color-border);
  background: #f8fafc;
}

.account__avatar {
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #e0ecff;
  color: var(--color-primary-dark);
  font-size: 13px;
  font-weight: 600;
}

.account__text {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  line-height: 1.25;
}

.account__name {
  font-size: 12.5px;
  font-weight: 500;
}

.account__username {
  color: var(--color-text-muted);
  font-size: 11px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

.account__caret {
  width: 14px;
  height: 14px;
  margin-left: 2px;
  color: var(--color-text-muted);
  transition: transform 0.15s ease;
}

.account__trigger.is-open .account__caret {
  transform: rotate(180deg);
}

.account__menu {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  min-width: 168px;
  padding: 6px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  box-shadow: 0 8px 24px rgb(15 23 42 / 10%);
}

.account__item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 10px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--color-text);
  font-size: 13px;
  text-align: left;
}

.account__item:hover {
  background: var(--color-danger-bg);
  color: var(--color-danger);
}

.account__item-icon {
  width: 15px;
  height: 15px;
  opacity: 0.8;
}

/* 菜单出现/消失的轻微上浮，避免生硬弹出 */
.account-menu-enter-active,
.account-menu-leave-active {
  transition: opacity 0.12s ease, transform 0.12s ease;
}

.account-menu-enter-from,
.account-menu-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
