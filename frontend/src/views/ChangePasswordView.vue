<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { changePassword, logout } from '@/api/auth'
import { isApiError } from '@/api/http'
import { clearSession, getUser, saveSession } from '@/auth/session'

/**
 * 修改本人密码（F-01 / BR-A02 / BR-A04）。
 *
 * <p>两个入口共用本页：首次登录被强制跳转（{@code mustChangePassword=true}），
 * 以及顶栏「修改密码」。区别只在文案与是否显示「退出登录」——强制改密时用户
 * 还没证明自己会改，需要一个退路。</p>
 *
 * <p>规则前端只做即时提示，最终以服务端为准：长度 8–20、四类字符至少三类、
 * 新旧不能相同、两次输入一致。改密成功后后端让旧令牌失效并返回新令牌，
 * 这里必须用返回的会话覆盖本地，否则下一个请求就会 401。</p>
 */
const router = useRouter()

const user = getUser()

const forced = computed(() => user?.mustChangePassword === true)

const oldPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const submitting = ref(false)
const error = ref('')
const errorCode = ref('')

/** 与后端 PasswordPolicy 一致：大写、小写、数字、符号四类命中数。 */
const kinds = computed(() => {
  const value = newPassword.value
  let count = 0
  if (/[A-Z]/.test(value)) count += 1
  if (/[a-z]/.test(value)) count += 1
  if (/[0-9]/.test(value)) count += 1
  if (/[^A-Za-z0-9]/.test(value)) count += 1
  return count
})

const lengthOk = computed(() => newPassword.value.length >= 8 && newPassword.value.length <= 20)
const kindsOk = computed(() => kinds.value >= 3)
const sameAsOld = computed(() => newPassword.value !== '' && newPassword.value === oldPassword.value)
const confirmOk = computed(
  () => confirmPassword.value !== '' && confirmPassword.value === newPassword.value,
)
const canSubmit = computed(
  () =>
    oldPassword.value !== '' &&
    lengthOk.value &&
    kindsOk.value &&
    confirmOk.value &&
    !sameAsOld.value &&
    !submitting.value,
)

async function onSubmit(): Promise<void> {
  error.value = ''
  errorCode.value = ''
  if (!canSubmit.value) {
    error.value = '请按提示检查新密码'
    return
  }
  submitting.value = true
  try {
    const result = await changePassword(oldPassword.value, newPassword.value)
    saveSession(result.token, result.user)
    await router.replace('/')
  } catch (e) {
    if (isApiError(e)) {
      error.value = e.message
      errorCode.value = e.code
    } else {
      error.value = '修改失败，请稍后重试'
    }
  } finally {
    submitting.value = false
  }
}

async function onLogout(): Promise<void> {
  try {
    await logout()
  } catch {
    // 令牌可能已失效，登出失败不影响本地清理
  } finally {
    clearSession()
    await router.replace({ name: 'login' })
  }
}
</script>

<template>
  <section class="password">
    <header class="password__head">
      <h1 class="password__title">修改密码</h1>
      <p class="password__subtitle">
        {{
          forced
            ? '当前使用的是初始密码，请先修改后再使用平台。'
            : '建议定期更换密码，且不要与其它系统共用。'
        }}
      </p>
    </header>

    <div v-if="forced" class="notice">
      <strong>首次登录必须改密</strong>
      <span>未修改前，除本页与退出登录外，其它接口都会被拒绝（EC-40305）。</span>
    </div>

    <form class="password__form" @submit.prevent="onSubmit">
      <label class="field">
        <span class="field__label">当前密码</span>
        <input
          v-model="oldPassword"
          class="field__input"
          type="password"
          autocomplete="current-password"
          placeholder="请输入当前登录密码"
        />
      </label>

      <label class="field">
        <span class="field__label">新密码</span>
        <input
          v-model="newPassword"
          class="field__input"
          type="password"
          autocomplete="new-password"
          placeholder="8–20 位，三类以上字符"
        />
      </label>

      <label class="field">
        <span class="field__label">确认新密码</span>
        <input
          v-model="confirmPassword"
          class="field__input"
          type="password"
          autocomplete="new-password"
          placeholder="再次输入新密码"
        />
      </label>

      <ul class="rules">
        <li class="rules__item" :class="{ 'is-ok': lengthOk }">
          {{ lengthOk ? '✓' : '○' }} 长度 8–20 位
        </li>
        <li class="rules__item" :class="{ 'is-ok': kindsOk }">
          {{ kindsOk ? '✓' : '○' }} 大写、小写、数字、符号中至少三类（当前 {{ kinds }} 类）
        </li>
        <li class="rules__item" :class="{ 'is-ok': confirmOk }">
          {{ confirmOk ? '✓' : '○' }} 两次输入一致
        </li>
        <li v-if="sameAsOld" class="rules__item is-bad">✗ 新密码不能与当前密码相同</li>
      </ul>

      <p v-if="error" class="password__error">
        {{ error }}<span v-if="errorCode" class="password__code">{{ errorCode }}</span>
      </p>

      <div class="password__actions">
        <button type="submit" class="password__submit" :disabled="!canSubmit">
          {{ submitting ? '提交中…' : '确认修改' }}
        </button>
        <button type="button" class="password__link" @click="onLogout">退出登录</button>
      </div>
    </form>
  </section>
</template>

<style scoped>
.password {
  display: flex;
  flex-direction: column;
  gap: 14px;
  max-width: 460px;
}

.password__head {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.password__title {
  margin: 0;
  font-size: 19px;
  font-weight: 600;
}

.password__subtitle {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 13px;
}

.notice {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 12px;
  border: 1px solid #fde68a;
  border-radius: 6px;
  background: #fffbeb;
  color: #92400e;
  font-size: 13px;
}

.password__form {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 20px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  background: var(--color-surface);
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field__label {
  color: var(--color-text-muted);
  font-size: 13px;
}

.field__input {
  padding: 9px 11px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: #fff;
  outline: none;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.field__input:focus {
  border-color: #93c5fd;
  box-shadow: 0 0 0 3px rgb(37 99 235 / 10%);
}

.rules {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin: 0;
  padding: 0;
  list-style: none;
  color: var(--color-text-muted);
  font-size: 12.5px;
}

.rules__item.is-ok {
  color: #047857;
}

.rules__item.is-bad {
  color: var(--color-danger);
}

.password__error {
  margin: 0;
  padding: 8px 10px;
  border: 1px solid #fecaca;
  border-radius: 6px;
  background: var(--color-danger-bg);
  color: var(--color-danger);
  font-size: 13px;
}

.password__code {
  margin-left: 6px;
  opacity: 0.7;
  font-size: 12px;
}

.password__actions {
  display: flex;
  align-items: center;
  gap: 14px;
}

.password__submit {
  padding: 9px 16px;
  border: 1px solid var(--color-primary);
  border-radius: 6px;
  background: var(--color-primary);
  color: #fff;
  font-size: 14px;
}

.password__submit:hover:not(:disabled) {
  border-color: var(--color-primary-dark);
  background: var(--color-primary-dark);
}

.password__submit:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.password__link {
  border: none;
  background: transparent;
  color: var(--color-text-muted);
  font-size: 13px;
  text-decoration: underline;
}
</style>
