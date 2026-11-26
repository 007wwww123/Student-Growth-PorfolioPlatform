<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { COMMENT_MAX_LENGTH, REASON_MIN_LENGTH } from '@/api/review'

/**
 * 审核结论弹窗。
 *
 * <p>通过时意见可空；退回时理由必填且不少于 10 字（BR-S04），前端先拦一次，
 * 避免白跑一趟再收 EC-42203，但服务端仍是最终判定方。</p>
 */
const props = defineProps<{
  open: boolean
  mode: 'approve' | 'reject'
  submitting?: boolean
  /** 服务端返回的错误文案，展示在弹窗内而不是关掉弹窗 */
  serverError?: string
}>()

const emit = defineEmits<{
  close: []
  submit: [payload: { comment?: string; reason?: string }]
}>()

const text = ref('')
const localError = ref('')

const title = computed(() => (props.mode === 'approve' ? '审核通过' : '退回修改'))
const trimmed = computed(() => text.value.trim())

watch(
  () => props.open,
  (open) => {
    if (open) {
      text.value = ''
      localError.value = ''
    }
  },
)

function onSubmit(): void {
  localError.value = ''
  if (props.mode === 'reject') {
    if (trimmed.value.length < REASON_MIN_LENGTH) {
      localError.value = `退回理由不少于 ${REASON_MIN_LENGTH} 字，当前 ${trimmed.value.length} 字`
      return
    }
    emit('submit', { reason: trimmed.value })
    return
  }
  if (trimmed.value.length > COMMENT_MAX_LENGTH) {
    localError.value = `审核意见不能超过 ${COMMENT_MAX_LENGTH} 字`
    return
  }
  emit('submit', trimmed.value ? { comment: trimmed.value } : {})
}
</script>

<template>
  <div v-if="open" class="dialog" role="dialog" aria-modal="true">
    <div class="dialog__mask" @click="emit('close')" />
    <div class="dialog__panel">
      <h3 class="dialog__title">{{ title }}</h3>
      <p class="dialog__desc">
        {{
          mode === 'approve'
            ? '通过后该板块状态变为「已通过」，学生不可再修改。'
            : '退回后该板块回到可编辑状态，理由会展示给学生。'
        }}
      </p>

      <label class="dialog__label" for="decision-text">
        {{ mode === 'approve' ? '审核意见（可选）' : '退回理由（必填）' }}
        <span v-if="mode === 'reject'" class="dialog__required">*</span>
      </label>
      <textarea
        id="decision-text"
        v-model="text"
        class="dialog__textarea"
        :class="{ 'has-error': localError }"
        rows="4"
        :maxlength="COMMENT_MAX_LENGTH"
        :placeholder="
          mode === 'approve' ? '可填写鼓励或补充说明' : '说明需要修改的具体内容，不少于 10 字'
        "
      />
      <p class="dialog__counter">
        {{ trimmed.length }} / {{ COMMENT_MAX_LENGTH }}
        <span v-if="mode === 'reject'">（至少 {{ REASON_MIN_LENGTH }} 字）</span>
      </p>

      <p v-if="localError" class="dialog__error">{{ localError }}</p>
      <p v-if="serverError" class="dialog__error">{{ serverError }}</p>

      <div class="dialog__actions">
        <button type="button" class="btn" :disabled="submitting" @click="emit('close')">取消</button>
        <button
          type="button"
          class="btn"
          :class="mode === 'approve' ? 'btn--primary' : 'btn--danger'"
          :disabled="submitting"
          @click="onSubmit"
        >
          {{ submitting ? '提交中…' : mode === 'approve' ? '确认通过' : '确认退回' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dialog {
  position: fixed;
  inset: 0;
  z-index: 50;
  display: flex;
  align-items: center;
  justify-content: center;
}

.dialog__mask {
  position: absolute;
  inset: 0;
  background: rgb(15 23 42 / 45%);
}

.dialog__panel {
  position: relative;
  width: min(480px, calc(100vw - 40px));
  padding: 20px;
  border-radius: var(--radius);
  background: var(--color-surface);
  box-shadow: 0 12px 32px rgb(15 23 42 / 18%);
}

.dialog__title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.dialog__desc {
  margin: 6px 0 14px;
  color: var(--color-text-muted);
  font-size: 13px;
}

.dialog__label {
  display: block;
  margin-bottom: 6px;
  font-size: 13px;
}

.dialog__required {
  color: var(--color-danger);
}

.dialog__textarea {
  width: 100%;
  padding: 8px 10px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  outline: none;
  resize: vertical;
}

.dialog__textarea:focus {
  border-color: #93c5fd;
  box-shadow: 0 0 0 3px rgb(37 99 235 / 10%);
}

.dialog__textarea.has-error {
  border-color: #fca5a5;
}

.dialog__counter {
  margin: 4px 0 0;
  color: var(--color-text-muted);
  font-size: 12px;
  text-align: right;
}

.dialog__error {
  margin: 8px 0 0;
  color: var(--color-danger);
  font-size: 12px;
}

.dialog__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
}

.btn {
  padding: 7px 14px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: #fff;
  color: var(--color-text);
}

.btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.btn--primary {
  border-color: var(--color-primary);
  background: var(--color-primary);
  color: #fff;
}

.btn--danger {
  border-color: var(--color-danger);
  background: var(--color-danger);
  color: #fff;
}
</style>
