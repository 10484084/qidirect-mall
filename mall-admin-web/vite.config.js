import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

const apiTarget = process.env.VITE_ADMIN_API_TARGET || 'http://127.0.0.1:8080';

// 开发服务器把 /admin-web/ 前缀重写为 index.html，与生产 Nginx 的部署路径保持一致
const rewriteAdminWeb = () => {
  let applied = false;
  return {
    name: 'rewrite-admin-web-index',
    configureServer(server) {
      if (applied) return;
      applied = true;
      return () => {
        server.middlewares.use((req, _res, next) => {
          const path = (req.url || '').split('?')[0];
          if (path === '/admin-web/' || path === '/admin-web/index.html') req.url = '/index.html';
          next();
        });
      };
    }
  };
};

export default defineConfig({
  base: '/admin-web/',
  plugins: [vue(), rewriteAdminWeb()],
  server: {
    port: 5174,
    proxy: {
      // 用正则限定边界，避免 /admin-web/ 被误代理到后端
      '^/admin/': { target: apiTarget, changeOrigin: true },
      '^/files/': { target: apiTarget, changeOrigin: true }
    }
  },
  build: {
    outDir: 'dist',
    chunkSizeWarningLimit: 1500
  }
});
