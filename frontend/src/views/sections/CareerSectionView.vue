<script setup lang="ts">
/**
 * 板块五 职业规划。
 *
 * <p>表单与其余板块一致，由 {@code SectionShell} 按后端 fieldMeta 渲染。
 * 本页只补一条口径说明：本板块记录的是「学生自述的规划意图」，
 * 毕业去向的最终值以就业系统为准，不用于任何统计与考核（需求 4.6）。</p>
 *
 * <p>这条说明放在表单上方而不是塞进外壳：它只属于本板块，
 * 而外壳要服务六个板块，塞进去就会变成一堆 `if (code === 'career')`。</p>
 */
import { computed } from 'vue'
import SectionShell from '@/views/SectionShell.vue'
import { getUser } from '@/auth/session'

const isStudent = computed(() => getUser()?.role === 'STU')
</script>

<template>
  <div class="career">
    <p class="career__note">
      <strong>口径说明</strong>
      <span v-if="isStudent">
        本板块是学生自述的规划意图，供辅导员在谈话与推荐时参考；毕业去向的最终值以就业系统为准，
        不用作统计与考核。意向、行业、岗位可随时在草稿状态下调整。
      </span>
      <span v-else>
        本板块是学生自述内容，未经核实；导出与统计不得引用本板块，毕业去向以就业系统为准。
      </span>
    </p>
    <SectionShell code="career" />
  </div>
</template>

<style scoped>
.career {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.career__note {
  display: flex;
  gap: 8px;
  margin: 0;
  padding: 10px 12px;
  border: 1px solid #e2e8f0;
  border-left: 3px solid var(--color-primary);
  border-radius: 6px;
  background: var(--color-surface);
  color: var(--color-text-muted);
  font-size: 12.5px;
  line-height: 1.7;
}

.career__note strong {
  flex: none;
  color: var(--color-text);
  font-weight: 600;
}
</style>
