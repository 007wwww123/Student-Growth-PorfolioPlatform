<script setup lang="ts">
/**
 * 字段渲染列表（学生编辑态与辅导员只读态共用）。
 *
 * <p>字段清单、控件类型、必填、可写全部来自服务端 fieldMeta，组件本身不认识任何业务字段，
 * 因此六个板块、审核页都复用同一份实现；新增字段不需要改这里。</p>
 *
 * <p>表单值通过 {@code form} 对象就地读写，父组件只负责把接口返回的 fields 铺进去。</p>
 */
import type { RenderField } from '@/fieldMeta'

const props = withDefaults(
  defineProps<{
    fields: RenderField[]
    form: Record<string, string>
    /** field -> 提示文案，字段级校验失败时就地展示 */
    errors?: Record<string, string>
    /** field -> 已查看的明文，用于覆盖脱敏值 */
    revealed?: Record<string, string>
    /** 是否显示「查看完整」按钮（仅辅导员或临时授权） */
    revealable?: boolean
    /** 强制只读，用于审核页，避免依赖每个字段的 editable 位 */
    readonly?: boolean
  }>(),
  { errors: () => ({}), revealed: () => ({}), revealable: false, readonly: false },
)

const emit = defineEmits<{ reveal: [key: string] }>()

function inputType(type: string): string {
  if (type === 'number') return 'number'
  if (type === 'date') return 'date'
  return 'text'
}

function disabled(field: RenderField): boolean {
  return props.readonly || !field.editable
}

function canReveal(field: RenderField): boolean {
  return props.revealable && field.masked === true && (field.sensitive ?? 0) >= 1
}
</script>

<template>
  <div class="fields">
    <div v-for="field in fields" :key="field.key" class="field">
      <label class="field__label" :for="`field-${field.key}`">
        {{ field.label }}<span v-if="field.required" class="field__required">*</span>
      </label>

      <div class="field__control-wrap">
        <select
          v-if="field.type === 'select'"
          :id="`field-${field.key}`"
          v-model="form[field.key]"
          class="field__control"
          :class="{ 'has-error': errors[field.key] }"
          :disabled="disabled(field)"
        >
          <option value="">请选择</option>
          <option v-for="option in field.options" :key="option.value" :value="option.value" :disabled="option.disabled">
            {{ option.label }}
          </option>
        </select>

        <textarea
          v-else-if="field.type === 'textarea'"
          :id="`field-${field.key}`"
          v-model="form[field.key]"
          class="field__control"
          :class="{ 'has-error': errors[field.key] }"
          rows="3"
          :disabled="disabled(field)"
        />

        <input
          v-else-if="revealed[field.key]"
          :id="`field-${field.key}`"
          :value="revealed[field.key]"
          class="field__control"
          type="text"
          readonly
        />

        <input
          v-else
          :id="`field-${field.key}`"
          v-model="form[field.key]"
          class="field__control"
          :class="{ 'has-error': errors[field.key] }"
          :type="inputType(field.type)"
          :maxlength="field.maxLength"
          :disabled="disabled(field)"
        />

        <button
          v-if="canReveal(field)"
          type="button"
          class="field__reveal"
          @click="emit('reveal', field.key)"
        >
          {{ revealed[field.key] ? '已显示' : '查看完整' }}
        </button>
      </div>

      <p v-if="errors[field.key]" class="field__error">{{ errors[field.key] }}</p>
      <p v-else-if="canReveal(field)" class="field__hint">
        {{ revealed[field.key] ? '明文已显示，本次查看已记录审计' : '敏感字段，查看明文会记录审计日志' }}
      </p>
      <p v-else-if="field.type === 'file'" class="field__hint">
        附件上传暂未开放，请在学校系统中维护
      </p>
      <p v-else-if="field.lockHint && !readonly" class="field__hint">{{ field.lockHint }}</p>
      <p v-else-if="field.type === 'tags'" class="field__hint">多个值用逗号分隔</p>
    </div>
  </div>
</template>

<style scoped>
.fields {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.field {
  display: grid;
  grid-template-columns: 110px 1fr;
  align-items: start;
  gap: 8px 12px;
}

.field__label {
  padding-top: 7px;
  color: var(--color-text-muted);
  font-size: 13px;
}

.field__required {
  margin-left: 2px;
  color: var(--color-danger);
}

.field__control-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
}

.field__control {
  width: 100%;
  padding: 7px 10px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: #fff;
  outline: none;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.field__control:focus {
  border-color: #93c5fd;
  box-shadow: 0 0 0 3px rgb(37 99 235 / 10%);
}

.field__control:disabled {
  background: #f9fafb;
  color: var(--color-text-muted);
}

.field__control.has-error {
  border-color: #fca5a5;
}

.field__reveal {
  flex: none;
  padding: 5px 10px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: #fff;
  color: var(--color-primary);
  font-size: 12px;
}

.field__error {
  grid-column: 2;
  margin: 0;
  color: var(--color-danger);
  font-size: 12px;
}

.field__hint {
  grid-column: 2;
  margin: 0;
  color: #9ca3af;
  font-size: 12px;
}
</style>
