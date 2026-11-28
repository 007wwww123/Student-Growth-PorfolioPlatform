<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login } from '@/api/auth'
import { isApiError } from '@/api/http'
import { saveSession } from '@/auth/session'
import { DEMO_ACCOUNTS } from '@/auth/accounts'

/**
 * 登录页（S-1-02）。
 *
 * <p>只做两件事：把账号密码交给后端，把返回的令牌存进会话。
 * 角色、数据范围、字段权限都不在这里判断——前端算出来的角色不能作为权限依据。</p>
 */
const route = useRoute()
const router = useRouter()

const username = ref('')
const password = ref('')
const submitting = ref(false)
const error = ref('')
const errorCode = ref('')

/** 登录后回到被拦截的页面；只接受站内路径，避免开放重定向。 */
const redirect = computed(() => {
  const value = route.query.redirect
  const path = Array.isArray(value) ? value[0] : value
  return typeof path === 'string' && path.startsWith('/') && !path.startsWith('//') ? path : '/'
})

async function onSubmit(): Promise<void> {
  error.value = ''
  errorCode.value = ''
  if (!username.value.trim() || !password.value) {
    error.value = '请输入登录名和密码'
    return
  }

  submitting.value = true
  try {
    const result = await login(username.value.trim(), password.value)
    saveSession(result.token, result.user)
    // 首次登录（初始密码）先走改密，改完再回原页面（BR-A04 / AC-004）
    if (result.mustChangePassword || result.user.mustChangePassword) {
      await router.replace({ name: 'change-password' })
      return
    }
    await router.replace(redirect.value)
  } catch (e) {
    if (isApiError(e)) {
      error.value = e.message
      errorCode.value = e.code
    } else {
      error.value = '登录失败，请稍后重试'
    }
  } finally {
    submitting.value = false
  }
}

function fill(account: string, pwd: string): void {
  username.value = account
  password.value = pwd
  error.value = ''
}
</script>

<template>
  <div class="login">
    <div class="login__card">
      <header class="login__head">
        <h1 class="login__title">登录学生成长档案平台</h1>
        <p class="login__subtitle">学生填写档案，辅导员审核，管理员维护配置</p>
      </header>

      <form class="login__form" @submit.prevent="onSubmit">
        <label class="login__field">
          <span class="login__label">登录名</span>
          <input
            v-model="username"
            class="login__input"
            type="text"
            autocomplete="username"
            placeholder="学生填学号，辅导员填 cou+工号"
          />
        </label>

        <label class="login__field">
          <span class="login__label">密码</span>
          <input
            v-model="password"
            class="login__input"
            type="password"
            autocomplete="current-password"
            placeholder="请输入密码"
          />
        </label>

        <p v-if="error" class="login__error">
          {{ error }}<span v-if="errorCode" class="login__code">{{ errorCode }}</span>
        </p>

        <button type="submit" class="login__submit" :disabled="submitting">
          {{ submitting ? '登录中…' : '登录' }}
        </button>
      </form>

      <details class="login__demo">
        <summary>演示账号（点击自动填入）</summary>
        <ul class="login__accounts">
          <li v-for="item in DEMO_ACCOUNTS" :key="item.username">
            <button type="button" class="login__account" @click="fill(item.username, item.password)">
              <span class="login__account-name">{{ item.label }}</span>
              <span class="login__account-user">{{ item.username }}</span>
            </button>
          </li>
        </ul>
        <p class="login__demo-tip">统一密码 {{ DEMO_ACCOUNTS[0]?.password }}，仅开发环境有效。</p>
      </details>
    </div>
  </div>
</template>

<style scoped>
.login {
  display: flex;
  justify-content: center;
  padding: 24px 0 40px;
}

.login__card {
  width: min(420px, 100%);
  padding: 26px 24px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  background: var(--color-surface);
  box-shadow: var(--shadow);
}

.login__title {
  margin: 0 0 6px;
  font-size: 19px;
  font-weight: 600;
}

.login__subtitle {
  margin: 0 0 20px;
  color: var(--color-text-muted);
  font-size: 13px;
}

.login__form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.login__field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.login__label {
  color: var(--color-text-muted);
  font-size: 13px;
}

.login__input {
  padding: 9px 11px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: #fff;
  outline: none;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.login__input:focus {
  border-color: #93c5fd;
  box-shadow: 0 0 0 3px rgb(37 99 235 / 10%);
}

.login__error {
  margin: 0;
  padding: 8px 10px;
  border: 1px solid #fecaca;
  border-radius: 6px;
  background: var(--color-danger-bg);
  color: var(--color-danger);
  font-size: 13px;
}

.login__code {
  margin-left: 6px;
  opacity: 0.7;
  font-size: 12px;
}

.login__submit {
  margin-top: 4px;
  padding: 9px 14px;
  border: 1px solid var(--color-primary);
  border-radius: 6px;
  background: var(--color-primary);
  color: #fff;
  font-size: 14px;
}

.login__submit:hover:not(:disabled) {
  border-color: var(--color-primary-dark);
  background: var(--color-primary-dark);
}

.login__submit:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.login__demo {
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px dashed var(--color-border);
  color: var(--color-text-muted);
  font-size: 12.5px;
}

.login__accounts {
  margin: 10px 0 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.login__account {
  display: flex;
  justify-content: space-between;
  width: 100%;
  padding: 6px 8px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: #f9fafb;
  color: var(--color-text);
  font-size: 12.5px;
  text-align: left;
}

.login__account:hover {
  border-color: #bfdbfe;
  background: #eff6ff;
}

.login__account-user {
  color: var(--color-text-muted);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

.login__demo-tip {
  margin: 10px 0 0;
  font-size: 12px;
}
</style>
