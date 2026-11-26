import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import './assets/main.css'

/**
 * 启动前先初始化 MSW（若开启），保证首屏请求也能被拦截。
 * 关闭 mock 只需把 .env.development 里的 VITE_USE_MOCK 改为 false。
 */
async function bootstrap() {
  if (import.meta.env.VITE_USE_MOCK === 'true') {
    const { enableMocking } = await import('./mocks/browser')
    await enableMocking()
  }

  createApp(App).use(router).mount('#app')
}

void bootstrap()
