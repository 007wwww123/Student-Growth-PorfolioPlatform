<script setup lang="ts">
import { RouterLink, RouterView, useRoute } from 'vue-router'
import { SECTIONS } from '@/sectionMeta'

const route = useRoute()
</script>

<template>
  <div class="layout">
    <header class="layout__header">
      <div class="layout__brand">
        <span class="layout__logo">成长档案</span>
        <span class="layout__subtitle">学生成长档案平台 · 前端骨架</span>
      </div>
      <nav class="layout__nav">
        <RouterLink to="/" class="layout__nav-item" :class="{ 'is-active': route.name === 'home' }">
          总览
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

.layout__brand {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 10px;
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
