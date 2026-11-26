<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'
import { DEMO_IDENTITIES, getIdentity, setIdentity, type DemoIdentity } from '@/api/identity'
import { SECTIONS } from '@/sectionMeta'

const route = useRoute()

/**
 * 演示身份切换（骨架期占位）。
 *
 * <p>后端尚未接入登录，身份从请求头读取；这里提供一个切换器，便于在不改代码的情况下
 * 分别以学生、辅导员视角验证权限与审核流程。接入登录后整体删除。</p>
 */
const identity = ref<DemoIdentity>(getIdentity())

const identityKey = computed({
  get: () => `${identity.value.role}-${identity.value.userId}`,
  set: (value: string) => {
    const found = DEMO_IDENTITIES.find((item) => `${item.role}-${item.userId}` === value)
    if (!found) {
      return
    }
    setIdentity(found)
    identity.value = found
    // 切换后整页重载：板块页与列表页都按身份取数，避免残留上一个身份的数据
    window.location.reload()
  },
})
</script>

<template>
  <div class="layout">
    <header class="layout__header">
      <div class="layout__top">
        <div class="layout__brand">
          <span class="layout__logo">成长档案</span>
          <span class="layout__subtitle">学生成长档案平台</span>
        </div>
        <label class="layout__identity">
          <span class="layout__identity-label">演示身份</span>
          <select v-model="identityKey" class="layout__identity-select">
            <option
              v-for="item in DEMO_IDENTITIES"
              :key="`${item.role}-${item.userId}`"
              :value="`${item.role}-${item.userId}`"
            >
              {{ item.label }}
            </option>
          </select>
        </label>
      </div>
      <nav class="layout__nav">
        <RouterLink to="/" class="layout__nav-item" :class="{ 'is-active': route.name === 'home' }">
          总览
        </RouterLink>
        <RouterLink
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

    <footer class="layout__footer">
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

.layout__identity {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--color-text-muted);
  font-size: 12px;
}

.layout__identity-select {
  padding: 4px 8px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: #fff;
  color: var(--color-text);
  font-size: 12.5px;
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
