<script setup lang="ts">
import { RouterLink } from 'vue-router'
</script>

<template>
  <section class="notfound">
    <p class="notfound__code">404</p>
    <p class="notfound__text">页面不存在，可能是地址输错了。</p>
    <RouterLink to="/" class="notfound__link">返回板块总览</RouterLink>
  </section>
</template>

<style scoped>
.notfound {
  padding: 64px 0;
  text-align: center;
}

.notfound__code {
  margin: 0;
  color: #cbd5e1;
  font-size: 48px;
  font-weight: 700;
}

.notfound__text {
  margin: 4px 0 16px;
  color: var(--color-text-muted);
}
</style>
