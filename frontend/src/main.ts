import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import './assets/main.css'

/**
 * 启动前先初始化 MSW（若开启），保证首屏请求也能被拦截。
 * 开关在 .env.development（或 .env.local）里的 VITE_USE_MOCK，页面代码不需要改。
 */
async function bootstrap() {
  if (import.meta.env.VITE_USE_MOCK === 'true') {
    const { enableMocking } = await import('./mocks/browser')
    await enableMocking()
  } else {
    await unregisterStaleMockWorker()
  }

  createApp(App).use(router).mount('#app')
}

/**
 * 清理上一次 mock 会话残留的 Service Worker。
 *
 * <p>MSW 的 worker 注册在浏览器里是持久的：开过 mock 之后即使把开关关掉，
 * 旧 worker 仍留在 `navigator.serviceWorker` 中，容易与真实接口混用、让排障看起来像登录问题。
 * 关闭 mock 时主动注销，保证请求一定走 Vite 代理打到真实后端。</p>
 */
async function unregisterStaleMockWorker(): Promise<void> {
  if (!('serviceWorker' in navigator)) {
    return
  }
  try {
    const registrations = await navigator.serviceWorker.getRegistrations()
    await Promise.all(
      registrations
        .filter((item) => (item.active?.scriptURL ?? '').includes('mockServiceWorker.js'))
        .map((item) => item.unregister()),
    )
  } catch {
    // 注销失败不影响页面启动：未激活的 worker 不会接管请求
  }
}

void bootstrap()
