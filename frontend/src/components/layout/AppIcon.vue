<script setup lang="ts">
import type { NavIconName } from '@/navMenu'

/**
 * 菜单图标：统一 24×24 画布、描边取 currentColor，随菜单项颜色变化。
 * 全部用 path 描述，避免在模板里混用 circle/rect 造成属性类型分叉。
 */
withDefaults(defineProps<{ name: NavIconName; size?: number }>(), { size: 18 })

const PATHS: Record<NavIconName, string[]> = {
  overview: ['M4 4h6v6H4z', 'M14 4h6v6h-6z', 'M4 14h6v6H4z', 'M14 14h6v6h-6z'],
  profile: [
    'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8z',
    'M5 20c0-3.3 3.1-5.6 7-5.6s7 2.3 7 5.6',
  ],
  book: ['M4 5a2 2 0 0 1 2-2h13v16H6a2 2 0 0 0-2 2z', 'M8 7.5h7'],
  star: ['M12 3.6l2.6 5.2 5.8.9-4.2 4.1 1 5.8-5.2-2.8-5.2 2.8 1-5.8L3.6 9.7l5.8-.9z'],
  heart: ['M12 20.2S4.8 15.8 4.8 10.9A4.2 4.2 0 0 1 12 8a4.2 4.2 0 0 1 7.2 2.9c0 4.9-7.2 9.3-7.2 9.3z'],
  briefcase: ['M4 8.5h16V19H4z', 'M9 8.5V6.6a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v1.9', 'M4 13.5h16'],
  chat: ['M5 5.5h14v9.5H9.5L5 19z'],
  review: [
    'M12 3.2l7 2.8v6c0 4.2-3 7.2-7 8.9-4-1.7-7-4.7-7-8.9v-6z',
    'M9.2 11.8l2 2 3.6-3.8',
  ],
  settings: [
    'M4 7h9',
    'M17 7h3',
    'M16 5v4',
    'M4 12h3',
    'M11 12h9',
    'M10 10v4',
    'M4 17h10',
    'M18 17h2',
    'M17 15v4',
  ],
}
</script>

<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.6"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    focusable="false"
  >
    <path v-for="(d, index) in PATHS[name]" :key="index" :d="d" />
  </svg>
</template>
