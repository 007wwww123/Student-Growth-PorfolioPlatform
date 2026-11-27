<script setup lang="ts">
import { computed } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import AppSideMenu from '@/components/layout/AppSideMenu.vue'
import AppTopBar from '@/components/layout/AppTopBar.vue'

/**
 * 公共框架：顶部固定栏 + 左侧固定菜单 + 右侧内容区。
 *
 * <p>三者是并列结构，只有 RouterView 随路由变化，顶部栏与菜单不重新挂载，
 * 因此切换页面时导航始终在位、内容不变。登录页没有可点的导航，
 * 也用不上账号下拉，直接不套框架。</p>
 */
const route = useRoute()

const showChrome = computed(() => route.name !== 'login')
</script>

<template>
  <div class="app" :class="{ 'app--bare': !showChrome }">
    <AppTopBar v-if="showChrome" />
    <AppSideMenu v-if="showChrome" />

    <main class="app__main">
      <div class="app__content">
        <RouterView />
        <footer v-if="showChrome" class="app__footer">
          接口契约见仓库根目录 openapi.yaml；后端未实现时由 MSW 提供 mock 数据。
        </footer>
      </div>
    </main>
  </div>
</template>

<style scoped>
.app {
  min-height: 100%;
}

/* 内容区让出固定栏的宽度与高度：左侧留菜单宽、上方留顶部栏高，再叠常规内边距 */
.app__main {
  min-height: 100vh;
  margin-left: var(--layout-sidebar-width);
  padding: calc(var(--layout-topbar-height) + 24px) 28px 24px;
}

.app__content {
  max-width: 1080px;
  margin: 0 auto;
}

.app__footer {
  margin-top: 32px;
  padding-top: 14px;
  border-top: 1px solid var(--color-border);
  color: var(--color-text-muted);
  font-size: 12px;
  text-align: center;
}

.app--bare .app__main {
  margin-left: 0;
  padding: 32px 24px;
}

.app--bare .app__content {
  max-width: none;
}

@media (max-width: 1180px) {
  .app__main {
    padding-right: 20px;
    padding-left: 20px;
  }
}
</style>
