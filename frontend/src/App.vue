<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { logout } from '@/api/auth'
import { clearSession, getUser } from '@/auth/session'
import { SECTIONS } from '@/sectionMeta'

const route = useRoute()
const router = useRouter()

/**
 * 顶部信息条读的是登录时保存的用户信息，不做二次请求；
 * 刷新页面后 /auth/me 会重新确认账号状态，因此这里的展示允许短暂过期。
 */
const user = getUser()

/** 登录页不显示导航与页脚，避免未登录时点进任何受保护页面。 */
const showChrome = computed(() => route.name !== 'login')

/** 审核工作台只对辅导员与持授权管理员可见；学生看到菜单也进不去，干脆不显示。 */
const canReview = computed(() => user?.role === 'COU' || user?.role === 'ADMIN')

async function onLogout(): Promise<void> {
  try {
    await logout()
  } catch {
    // 令牌可能已过期，登出失败不影响本地清理
  } finally {
    clearSession()
    await router.replace({ name: 'login' })
  }
}
</script>

<template>
  <div class="layout">
    <header v-if="showChrome" class="layout__header">
      <div class="layout__top">
        <div class="layout__brand">
          <span class="layout__logo">成长档案</span>
          <span class="layout__subtitle">学生成长档案平台</span>
        </div>
        <div v-if="user" class="layout__user">
          <span class="layout__user-name">{{ user.displayName }}</span>
          <span class="layout__user-role">{{ user.roleLabel }}</span>
          <button type="button" class="layout__logout" @click="onLogout">退出登录</button>
        </div>
      </div>
      <nav class="layout__nav">
        <RouterLink to="/" class="layout__nav-item" :class="{ 'is-active': route.name === 'home' }">
          总览
        </RouterLink>
        <RouterLink
          v-if="canReview"
          to="/review"
          class="layout__nav-item"
          :class="{ 'is-active': route.name === 'review-list' || route.name === 'review-detail' }"
        >
          审核工作台
        </RouterLink>
        <RouterLink
          v-for="section in SECTIONS"
          :key="section.code"
          :to="`/sections/${section.code}`"
          class="layout__nav-item"
          :class="{ 'is-active': route.name === `section-${section.code}` }"
        >
          {{ section.name }}
        </RouterLink>
      </nav>
    </header>

    <main class="layout__main">
      <RouterView />
    </main>

    <footer v-if="showChrome" class="layout__footer">
      接口契约见仓库根目录 openapi.yaml；后端未实现时由 MSW 提供 mock 数据。
    </footer>
  </div>
</template>

<style scoped>
.layout {
  display: flex;
  flex-direction: column;
  min-height: 100%;
}

.layout__header {
  background: var(--color-surface);
  border-bottom: 1px solid var(--color-border);
  padding: 12px 24px;
}

.layout__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 10px;
}

.layout__brand {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.layout__logo {
  font-size: 17px;
  font-weight: 600;
  letter-spacing: 0.5px;
}

.layout__subtitle {
  color: var(--color-text-muted);
  font-size: 12px;
}

.layout__user {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12.5px;
}

.layout__user-name {
  color: var(--color-text);
}

.layout__user-role {
  padding: 1px 8px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  color: var(--color-text-muted);
  font-size: 11.5px;
}

.layout__logout {
  padding: 3px 10px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: #fff;
  color: var(--color-text-muted);
  font-size: 12.5px;
}

.layout__logout:hover {
  border-color: #fecaca;
  color: var(--color-danger);
}

.layout__nav {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.layout__nav-item {
  padding: 5px 12px;
  border-radius: 6px;
  color: var(--color-text-muted);
  font-size: 13px;
}

.layout__nav-item:hover {
  background: #f1f5f9;
  color: var(--color-text);
}

.layout__nav-item.is-active {
  background: #eff6ff;
  color: var(--color-primary);
  font-weight: 500;
}

.layout__main {
  flex: 1;
  width: 100%;
  max-width: 960px;
  margin: 0 auto;
  padding: 24px;
}

.layout__footer {
  padding: 16px 24px 24px;
  text-align: center;
  color: var(--color-text-muted);
  font-size: 12px;
}
</style>
