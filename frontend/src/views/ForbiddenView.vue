<script setup lang="ts">
import { RouterLink } from 'vue-router'
import { getUser } from '@/auth/session'

/** 已登录但角色不匹配时展示，避免白屏或误以为登录失效。 */
const user = getUser()
</script>

<template>
  <section class="forbidden">
    <h1 class="forbidden__title">没有访问权限</h1>
    <p class="forbidden__desc">
      当前账号「{{ user?.displayName ?? '未登录' }}」的角色为
      {{ user?.roleLabel ?? '未知' }}，该页面仅对辅导员或授权管理员开放。
    </p>
    <div class="forbidden__actions">
      <RouterLink to="/" class="forbidden__link">返回总览</RouterLink>
    </div>
  </section>
</template>

<style scoped>
.forbidden {
  padding: 40px 20px;
  text-align: center;
}

.forbidden__title {
  margin: 0 0 8px;
  font-size: 20px;
  font-weight: 600;
}

.forbidden__desc {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 13px;
}

.forbidden__actions {
  margin-top: 16px;
}

.forbidden__link {
  color: var(--color-primary);
  font-size: 13px;
}
</style>
