<script setup lang="ts">
import { RouterLink } from 'vue-router'
import { SECTIONS } from '@/sectionMeta'
</script>

<template>
  <section>
    <div class="intro">
      <h1 class="intro__title">总览</h1>
      <p class="intro__desc">
        数据可视化看板待接入：后续在此汇总各板块完成度、审核进度与分布统计。
        当前先提供六大模块的入口，可分别进入填写、提交与查看审核结果。
      </p>
    </div>

    <ul class="cards">
      <li v-for="(section, index) in SECTIONS" :key="section.code" class="cards__item">
        <RouterLink :to="`/sections/${section.code}`" class="card">
          <span class="card__index">{{ index + 1 }}</span>
          <span class="card__body">
            <span class="card__name">{{ section.name }}</span>
            <span class="card__summary">{{ section.summary }}</span>
            <span class="card__code">/api/sections/{{ section.code }}</span>
          </span>
        </RouterLink>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.intro {
  margin-bottom: 20px;
}

.intro__title {
  margin: 0 0 6px;
  font-size: 20px;
  font-weight: 600;
}

.intro__desc {
  margin: 0;
  color: var(--color-text-muted);
}

.cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.card {
  display: flex;
  gap: 12px;
  padding: 16px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  background: var(--color-surface);
  box-shadow: var(--shadow);
  color: inherit;
  transition: border-color 0.15s ease, transform 0.15s ease;
}

.card:hover {
  border-color: #bfdbfe;
  transform: translateY(-1px);
}

.card__index {
  display: grid;
  place-items: center;
  flex: 0 0 26px;
  height: 26px;
  border-radius: 7px;
  background: #eff6ff;
  color: var(--color-primary);
  font-size: 12px;
  font-weight: 600;
}

.card__body {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.card__name {
  font-weight: 600;
}

.card__summary {
  color: var(--color-text-muted);
  font-size: 12.5px;
  line-height: 1.5;
}

.card__code {
  color: #9ca3af;
  font-size: 11.5px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}
</style>
