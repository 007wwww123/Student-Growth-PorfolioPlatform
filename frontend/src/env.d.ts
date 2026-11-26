/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<Record<string, never>, Record<string, never>, unknown>
  export default component
}

interface ImportMetaEnv {
  /** 接口前缀，默认 /api（与后端 context-path 一致） */
  readonly VITE_API_BASE_URL?: string
  /** 是否启用 MSW 本地 mock，'true' 时开启 */
  readonly VITE_USE_MOCK?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
