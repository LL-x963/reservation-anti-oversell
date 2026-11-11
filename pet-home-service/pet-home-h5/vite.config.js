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
    // 0.0.0.0 便于同一局域网内用手机真机访问调试（http://<电脑IP>:5174）
    host: '0.0.0.0',
    port: 5174,
    // 启动后自动打开浏览器
    open: true,
    proxy: {
      // C 端接口统一 /api 前缀，开发环境代理到本地后端
      '/api': {
        target: BACKEND_TARGET,
        changeOrigin: true
      },
      // 后端上传的图片走静态资源路径，一并代理，避免开发环境图片 404
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

  // 移动端适配说明：
  // 1. 视口适配在 index.html 的 viewport 中完成（含 viewport-fit=cover 以支持刘海屏安全区域）；
  // 2. 安全区域通过 src/styles/index.css 里的 env(safe-area-inset-*) 变量统一处理；
  // 3. 如需把设计稿的 px 自动换算成 vw，可自行安装 postcss-px-to-viewport-8-plugin 并在
  //    此处追加 css.postcss.plugins 配置（本项目未内置该依赖，保持依赖最小）。
})
