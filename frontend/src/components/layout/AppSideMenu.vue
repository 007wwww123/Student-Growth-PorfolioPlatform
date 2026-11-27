<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import AppIcon from './AppIcon.vue'
import { getUser } from '@/auth/session'
import { navGroupsOf } from '@/navMenu'

/**
 * 左侧固定菜单。
 *
 * <p>固定在顶部栏之下，路由切换时只换右侧内容区，菜单本身不重新挂载，
 * 因此滚动位置与展开状态都不会丢。菜单分组与可见项由 {@link navGroupsOf} 按角色给出。</p>
 */
const route = useRoute()

const user = getUser()

const groups = computed(() => navGroupsOf(user?.role, user?.tempGrant === true))

function isActive(matchNames: string[]): boolean {
  return matchNames.includes(String(route.name))
}
</script>

<template>
  <aside class="side">
    <nav class="side__nav" aria-label="主导航">
      <div v-for="group in groups" :key="group.key" class="side__group">
        <p v-if="group.title" class="side__title">{{ group.title }}</p>
        <RouterLink
          v-for="item in group.items"
          :key="item.key"
          :to="item.to"
          class="side__item"
          :class="{ 'is-active': isActive(item.matchNames) }"
          :aria-current="isActive(item.matchNames) ? 'page' : undefined"
        >
          <AppIcon :name="item.icon" :size="17" class="side__icon" />
          <span class="side__label">{{ item.label }}</span>
        </RouterLink>
      </div>
    </nav>
  </aside>
</template>

<style scoped>
.side {
  position: fixed;
  top: var(--layout-topbar-height);
  bottom: 0;
  left: 0;
  z-index: 20;
  width: var(--layout-sidebar-width);
  padding: 14px 12px 20px;
  overflow-y: auto;
  background: var(--color-surface);
  border-right: 1px solid var(--color-border);
}

.side__group + .side__group {
  margin-top: 6px;
}

.side__title {
  margin: 14px 0 6px;
  padding: 0 10px;
  color: #9ca3af;
  font-size: 11.5px;
  font-weight: 500;
  letter-spacing: 0.4px;
}

.side__group:first-child .side__title:first-child {
  margin-top: 2px;
}

.side__item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 2px;
  padding: 8px 10px;
  border-radius: 7px;
  color: #4b5563;
  font-size: 13.5px;
  transition: background-color 0.15s ease, color 0.15s ease;
}

.side__item:hover {
  background: #f1f5f9;
  color: var(--color-text);
}

.side__item.is-active {
  background: #eff6ff;
  color: var(--color-primary);
  font-weight: 500;
}

/* 选中态左侧竖条：只用伪元素，不占布局宽度，避免图标左右跳动 */
.side__item.is-active::before {
  content: '';
  position: absolute;
  top: 50%;
  left: -12px;
  width: 3px;
  height: 18px;
  border-radius: 0 3px 3px 0;
  background: var(--color-primary);
  transform: translateY(-50%);
}

.side__icon {
  flex: 0 0 auto;
  opacity: 0.85;
}

.side__item.is-active .side__icon {
  opacity: 1;
}

.side__label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 1180px) {
  .side {
    padding: 12px 8px 16px;
  }

  .side__item.is-active::before {
    left: -8px;
  }
}
</style>
