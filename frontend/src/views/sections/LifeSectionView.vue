<script setup lang="ts">
/**
 * 板块四 生活心理。
 *
 * <p>表单本身与其余板块一致，交给 {@code SectionShell} 按后端 fieldMeta 渲染；
 * 本页只补两件生活心理独有的事：</p>
 * <ol>
 *   <li><b>关注标记</b>是派生值（后端由压力自评与情绪算出、不落库），不适合做成输入框，
 *       因此从表单网格移出（{@code hiddenKeys}），改用上方提示条解释成一句话；</li>
 *   <li><b>心理自评的可见范围</b>必须当面说清：学生要敢填，辅导员要知道自己看不到原始分值。
 *       这类提示只影响展示，权限仍由后端 {@code visible_roles} 判定。</li>
 * </ol>
 */
import { computed } from 'vue'
import SectionShell from '@/views/SectionShell.vue'
import { getUser } from '@/auth/session'
import type { SectionVO } from '@/api/types'

const isStudent = computed(() => getUser()?.role === 'STU')

/** 派生字段，不进表单网格。 */
const HIDDEN_KEYS = ['careFlag']

/** 关注标记的文案口径：只说结论与建议，不复述分值。 */
function careHint(section: SectionVO): string {
  return Number(section.fields?.careFlag ?? 0) === 1
    ? '系统判断：近期压力与情绪偏低，已标记为需要关注，建议主动约一次谈话。'
    : '系统判断：暂无关注标记。'
}
</script>

<template>
  <SectionShell code="life" :hidden-keys="HIDDEN_KEYS">
    <template #above="{ section }">
      <div class="life">
        <div class="life__row">
          <span class="life__label">关注标记</span>
          <span class="life__value" :class="{ 'life__value--on': Number(section.fields?.careFlag) === 1 }">
            {{ Number(section.fields?.careFlag) === 1 ? '需关注' : '无需关注' }}
          </span>
          <span class="life__hint">{{ careHint(section) }}</span>
        </div>
        <p class="life__note">
          <template v-if="isStudent">
            压力自评只有你本人能看到，辅导员端只看到上面的关注标记；关注标记不会触发任何自动通知。
            资助来源、重大事件备忘由辅导员维护，你只读。
          </template>
          <template v-else>
            心理自评的原始分值不对辅导员展示，本板块内你只能看到关注标记与压力来源；
            需要介入时请结合谈话记录判断。重大事件备忘由你填写，写入会留痕。
          </template>
        </p>
      </div>
    </template>
  </SectionShell>
</template>

<style scoped>
.life {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 14px;
  border: 1px solid #e2e8f0;
  border-radius: var(--radius);
  background: var(--color-surface);
  box-shadow: var(--shadow);
}

.life__row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.life__label {
  color: var(--color-text-muted);
  font-size: 12px;
}

.life__value {
  padding: 1px 8px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: #f8fafc;
  color: var(--color-text);
  font-size: 12px;
}

.life__value--on {
  border-color: #fde68a;
  background: #fffbeb;
  color: #92400e;
  font-weight: 600;
}

.life__hint {
  color: var(--color-text-muted);
  font-size: 12px;
}

.life__note {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 12px;
  line-height: 1.6;
}
</style>
