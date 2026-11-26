<script setup lang="ts">
import { RouterLink, RouterView, useRoute } from 'vue-router'
import SessionPanel from '@/views/SessionPanel.vue'
import { availableSections, currentStudentId, currentAcademicYear, identity, mockMode } from '@/api/context'

const route = useRoute()
</script>

<template>
  <div class="layout">
    <header class="layout__header">
      <div class="layout__brand">
        <span class="layout__logo">成长档案</span>
        <span class="layout__subtitle">学生成长档案平台</span>
      </div>
      <SessionPanel v-if="!mockMode" />
      <nav v-if="mockMode || identity" class="layout__nav">
        <RouterLink to="/" class="layout__nav-item" :class="{ 'is-active': route.name === 'home' }">
          总览
        </RouterLink>
        <RouterLink
          v-for="section in availableSections"
          :key="section.code"
          :to="`/sections/${section.code}`"
          class="layout__nav-item"
          :class="{ 'is-active': route.name === `section-${section.code}` }"
        >
          {{ section.name }}
        </RouterLink>
        <RouterLink v-if="!mockMode" to="/workflow" class="layout__nav-item">审核与归档</RouterLink>
      </nav>
    </header>

    <main class="layout__main">
      <RouterView v-if="mockMode || (identity && currentStudentId && currentAcademicYear)" :key="[identity?.actorId,currentStudentId,currentAcademicYear,route.path].join('/')" />
    </main>

    <footer class="layout__footer">
      {{ mockMode ? '演示数据' : '学生成长档案平台' }}
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
