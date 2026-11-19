import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import { resolve } from 'node:path';

const apiTarget = process.env.VITE_API_TARGET || 'http://127.0.0.1:8081';

export default defineConfig({
  // 支付 bridge 页走独立静态目录（/pay-bridge/），其余资源仍以 /h5/ 为基址。
  // bridge 页脚本全部内联，产物只有一份 html，无需静态资源基址。
  base: '/h5/',
  plugins: [vue()],
  resolve: {
    alias: {
      // 绕开 rollup 4.68 解析 @vue/devtools-api 的 bug，生产构建不需要 devtools
      '@vue/devtools-api': resolve(__dirname, 'devtools-stub.mjs')
    }
  },
  build: {
    outDir: process.env.BUILD_BRIDGE === '1' ? 'dist/pay-bridge' : 'dist',
    emptyOutDir: process.env.BUILD_BRIDGE !== '1',
    rollupOptions: {
      input: resolve(
        __dirname,
        process.env.BUILD_BRIDGE === '1' ? 'pay-bridge/index.html' : 'index.html'
      ),
      // bridge 页脚本内联，改名为 index.html 后由 Nginx 以 /pay-bridge/ 目录方式托管
      output:
        process.env.BUILD_BRIDGE === '1'
          ? { entryFileNames: 'index-[hash].js', assetFileNames: 'assets/[name]-[hash][extname]' }
          : {}
    }
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
    proxy: {
      '/api': { target: apiTarget, changeOrigin: true },
      '/files': { target: apiTarget, changeOrigin: true }
    }
  }
});
