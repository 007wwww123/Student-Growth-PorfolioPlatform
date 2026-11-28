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
    /**
     * 未覆盖的请求放行（静态资源、HMR 等）。
     *
     * <p>{@code /api} 下的接口一旦没写 mock，就会带着 mock 令牌打到真实后端并被 401 拒绝；
     * 这里显式告警，避免「页面能登录、却莫名其妙被踢回登录页」时无从排查。</p>
     */
    onUnhandledRequest(request, print) {
      const { pathname } = new URL(request.url)
      if (pathname.startsWith('/api/')) {
        print.warning()
      }
      // 其余请求（html/js/css/字体、Vite HMR）静默放行
    },
    serviceWorker: {
      url: `${import.meta.env.BASE_URL}mockServiceWorker.js`,
    },
  })
}
