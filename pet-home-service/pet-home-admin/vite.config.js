import { fileURLToPath, URL } from 'node:url'

import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

// 后端服务地址（java-backend 默认 8080，可用环境变量 VITE_BACKEND_TARGET 覆盖）
const BACKEND_TARGET = process.env.VITE_BACKEND_TARGET || 'http://localhost:8080'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      // 用 @ 指向 src，避免层层相对路径
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
    // 启动后自动打开浏览器
    open: true,
    proxy: {
      // 管理端接口统一走 /api 前缀
      '/api': {
        target: BACKEND_TARGET,
        changeOrigin: true
      },
      // 后端上传的图片通过静态资源路径直接访问，这里一并代理，避免开发环境图片 404
      '/upload': {
        target: BACKEND_TARGET,
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    chunkSizeWarningLimit: 1500
  }
})
