import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

const apiTarget = process.env.VITE_ADMIN_API_TARGET || 'http://127.0.0.1:8080';

export default defineConfig({
  // 与生产 Nginx 的 /admin-m/ 部署路径保持一致
  base: '/admin-m/',
  plugins: [vue()],
  server: {
    port: 5175,
    host: '0.0.0.0',
    proxy: {
      '^/admin/': { target: apiTarget, changeOrigin: true, secure: false },
      '^/files/': { target: apiTarget, changeOrigin: true, secure: false }
    }
  },
  build: {
    outDir: 'dist',
    chunkSizeWarningLimit: 1500
  },
  preview: {
    port: 4175,
    proxy: {
      '^/admin/': { target: apiTarget, changeOrigin: true, secure: false },
      '^/files/': { target: apiTarget, changeOrigin: true, secure: false }
    }
  }
});
