import { setupWorker } from 'msw/browser'
import { handlers } from './handlers'

/**
 * 浏览器端 mock。
 *
 * 需要在 public/ 下存在 mockServiceWorker.js（由 pnpm/npm 执行
 * `npx msw init public/ --save` 生成，已随仓库提交）。
 */
export const worker = setupWorker(...handlers)

export async function enableMocking(): Promise<void> {
  await worker.start({
    // 未覆盖的请求（静态资源、HMR 等）直接放行
    onUnhandledRequest: 'bypass',
    serviceWorker: {
      url: `${import.meta.env.BASE_URL}mockServiceWorker.js`,
    },
  })
}
