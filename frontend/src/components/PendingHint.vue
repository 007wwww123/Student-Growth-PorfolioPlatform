<script setup lang="ts">
/**
 * 待确认值提示（BR-F06）。
 *
 * <p>学生修改自己提交过、需辅导员确认的字段后，新值先进入待确认区，
 * 对外仍显示旧值。学生只能看，不能自己确认；辅导员在审核页处理。</p>
 */
import type { PendingItem } from '@/fieldMeta'

withDefaults(
  defineProps<{
    items: PendingItem[]
    /** 是否显示确认 / 驳回操作，仅辅导员 */
    actionable?: boolean
    /** 正在处理的字段 key，用于禁用按钮 */
    processing?: string | null
  }>(),
  { actionable: false, processing: null },
)

const emit = defineEmits<{ confirm: [key: string]; reject: [key: string] }>()
</script>

<template>
  <div v-if="items.length > 0" class="pending">
    <div class="pending__head">
      <strong>待确认修改 · {{ items.length }} 项</strong>
      <span class="pending__tip">
        {{ actionable ? '确认后新值生效，驳回则保留原值' : '辅导员确认前，档案仍显示当前值' }}
      </span>
    </div>

    <ul class="pending__list">
      <li v-for="item in items" :key="item.key" class="pending__item">
        <div class="pending__field">{{ item.label }}</div>
        <div class="pending__values">
          <span class="pending__current">{{ item.current || '（空）' }}</span>
          <span class="pending__arrow">→</span>
          <span class="pending__next">{{ item.pending || '（空）' }}</span>
        </div>
        <div v-if="actionable" class="pending__actions">
          <button
            type="button"
            class="btn btn--small btn--primary"
            :disabled="processing === item.key"
            @click="emit('confirm', item.key)"
          >
            确认生效
          </button>
          <button
            type="button"
            class="btn btn--small"
            :disabled="processing === item.key"
            @click="emit('reject', item.key)"
          >
            驳回
          </button>
        </div>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.pending {
  padding: 14px 16px;
  border: 1px solid #fde68a;
  border-radius: var(--radius);
  background: var(--color-warning-bg);
}

.pending__head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  color: var(--color-warning);
  font-size: 13px;
}

.pending__tip {
  color: #a16207;
  font-size: 12px;
}

.pending__list {
  margin: 10px 0 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.pending__item {
  display: grid;
  grid-template-columns: 110px 1fr auto;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 6px;
  background: #fff;
  font-size: 13px;
}

.pending__field {
  color: var(--color-text-muted);
}

.pending__values {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.pending__current {
  color: var(--color-text-muted);
  text-decoration: line-through;
}

.pending__arrow {
  color: #a16207;
}

.pending__next {
  color: var(--color-warning);
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pending__actions {
  display: flex;
  gap: 6px;
}

.btn {
  padding: 6px 12px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: #fff;
  color: var(--color-text);
  font-size: 13px;
}

.btn:hover:not(:disabled) {
  border-color: #cbd5e1;
  background: #f9fafb;
}

.btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.btn--small {
  padding: 4px 10px;
  font-size: 12px;
}

.btn--primary {
  border-color: var(--color-primary);
  background: var(--color-primary);
  color: #fff;
}

.btn--primary:hover:not(:disabled) {
  border-color: var(--color-primary-dark);
  background: var(--color-primary-dark);
}
</style>
