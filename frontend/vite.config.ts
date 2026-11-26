import { fileURLToPath, URL } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

// 后端 context-path 为 /api，开发代理把 /api 原样转发到 Spring Boot，不做 rewrite。
const BACKEND_ORIGIN = process.env.VITE_BACKEND_ORIGIN ?? 'http://127.0.0.1:8080'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: BACKEND_ORIGIN,
        changeOrigin: true,
      },
    },
  },
})
