// vite.config.js
import { defineConfig } from "file:///Users/songminghui/WorkBuddy/2026-11-19-16-09-43/qidirect-mall/mall-h5/node_modules/vite/dist/node/index.js";
import vue from "file:///Users/songminghui/WorkBuddy/2026-11-19-16-09-43/qidirect-mall/mall-h5/node_modules/@vitejs/plugin-vue/dist/index.mjs";
import { resolve } from "node:path";
var __vite_injected_original_dirname = "/Users/songminghui/WorkBuddy/2026-11-19-16-09-43/qidirect-mall/mall-h5";
var apiTarget = process.env.VITE_API_TARGET || "http://127.0.0.1:8081";
var vite_config_default = defineConfig({
  // 支付 bridge 页走独立静态目录（/pay-bridge/），其余资源仍以 /h5/ 为基址。
  // bridge 页脚本全部内联，产物只有一份 html，无需静态资源基址。
  base: "/h5/",
  plugins: [vue()],
  resolve: {
    alias: {
      // 绕开 rollup 4.68 解析 @vue/devtools-api 的 bug，生产构建不需要 devtools
      "@vue/devtools-api": resolve(__vite_injected_original_dirname, "devtools-stub.mjs")
    }
  },
  build: {
    outDir: process.env.BUILD_BRIDGE === "1" ? "dist/pay-bridge" : "dist",
    emptyOutDir: process.env.BUILD_BRIDGE !== "1",
    rollupOptions: {
      input: resolve(
        __vite_injected_original_dirname,
        process.env.BUILD_BRIDGE === "1" ? "pay-bridge/index.html" : "index.html"
      ),
      // bridge 页脚本内联，改名为 index.html 后由 Nginx 以 /pay-bridge/ 目录方式托管
      output: process.env.BUILD_BRIDGE === "1" ? { entryFileNames: "index-[hash].js", assetFileNames: "assets/[name]-[hash][extname]" } : {}
    }
  },
  server: {
    host: "0.0.0.0",
    port: 5173,
    proxy: {
      "/api": { target: apiTarget, changeOrigin: true },
      "/files": { target: apiTarget, changeOrigin: true }
    }
  }
});
export {
  vite_config_default as default
};
//# sourceMappingURL=data:application/json;base64,ewogICJ2ZXJzaW9uIjogMywKICAic291cmNlcyI6IFsidml0ZS5jb25maWcuanMiXSwKICAic291cmNlc0NvbnRlbnQiOiBbImNvbnN0IF9fdml0ZV9pbmplY3RlZF9vcmlnaW5hbF9kaXJuYW1lID0gXCIvVXNlcnMvc29uZ21pbmdodWkvV29ya0J1ZGR5LzIwMjYtMTEtMTktMTYtMDktNDMvcWlkaXJlY3QtbWFsbC9tYWxsLWg1XCI7Y29uc3QgX192aXRlX2luamVjdGVkX29yaWdpbmFsX2ZpbGVuYW1lID0gXCIvVXNlcnMvc29uZ21pbmdodWkvV29ya0J1ZGR5LzIwMjYtMTEtMTktMTYtMDktNDMvcWlkaXJlY3QtbWFsbC9tYWxsLWg1L3ZpdGUuY29uZmlnLmpzXCI7Y29uc3QgX192aXRlX2luamVjdGVkX29yaWdpbmFsX2ltcG9ydF9tZXRhX3VybCA9IFwiZmlsZTovLy9Vc2Vycy9zb25nbWluZ2h1aS9Xb3JrQnVkZHkvMjAyNi0xMS0xOS0xNi0wOS00My9xaWRpcmVjdC1tYWxsL21hbGwtaDUvdml0ZS5jb25maWcuanNcIjtpbXBvcnQgeyBkZWZpbmVDb25maWcgfSBmcm9tICd2aXRlJztcbmltcG9ydCB2dWUgZnJvbSAnQHZpdGVqcy9wbHVnaW4tdnVlJztcbmltcG9ydCB7IHJlc29sdmUgfSBmcm9tICdub2RlOnBhdGgnO1xuXG5jb25zdCBhcGlUYXJnZXQgPSBwcm9jZXNzLmVudi5WSVRFX0FQSV9UQVJHRVQgfHwgJ2h0dHA6Ly8xMjcuMC4wLjE6ODA4MSc7XG5cbmV4cG9ydCBkZWZhdWx0IGRlZmluZUNvbmZpZyh7XG4gIC8vIFx1NjUyRlx1NEVEOCBicmlkZ2UgXHU5ODc1XHU4RDcwXHU3MkVDXHU3QUNCXHU5NzU5XHU2MDAxXHU3NkVFXHU1RjU1XHVGRjA4L3BheS1icmlkZ2UvXHVGRjA5XHVGRjBDXHU1MTc2XHU0RjU5XHU4RDQ0XHU2RTkwXHU0RUNEXHU0RUU1IC9oNS8gXHU0RTNBXHU1N0ZBXHU1NzQwXHUzMDAyXG4gIC8vIGJyaWRnZSBcdTk4NzVcdTgxMUFcdTY3MkNcdTUxNjhcdTkwRThcdTUxODVcdTgwNTRcdUZGMENcdTRFQTdcdTcyNjlcdTUzRUFcdTY3MDlcdTRFMDBcdTRFRkQgaHRtbFx1RkYwQ1x1NjVFMFx1OTcwMFx1OTc1OVx1NjAwMVx1OEQ0NFx1NkU5MFx1NTdGQVx1NTc0MFx1MzAwMlxuICBiYXNlOiAnL2g1LycsXG4gIHBsdWdpbnM6IFt2dWUoKV0sXG4gIHJlc29sdmU6IHtcbiAgICBhbGlhczoge1xuICAgICAgLy8gXHU3RUQ1XHU1RjAwIHJvbGx1cCA0LjY4IFx1ODlFM1x1Njc5MCBAdnVlL2RldnRvb2xzLWFwaSBcdTc2ODQgYnVnXHVGRjBDXHU3NTFGXHU0RUE3XHU2Nzg0XHU1RUZBXHU0RTBEXHU5NzAwXHU4OTgxIGRldnRvb2xzXG4gICAgICAnQHZ1ZS9kZXZ0b29scy1hcGknOiByZXNvbHZlKF9fZGlybmFtZSwgJ2RldnRvb2xzLXN0dWIubWpzJylcbiAgICB9XG4gIH0sXG4gIGJ1aWxkOiB7XG4gICAgb3V0RGlyOiBwcm9jZXNzLmVudi5CVUlMRF9CUklER0UgPT09ICcxJyA/ICdkaXN0L3BheS1icmlkZ2UnIDogJ2Rpc3QnLFxuICAgIGVtcHR5T3V0RGlyOiBwcm9jZXNzLmVudi5CVUlMRF9CUklER0UgIT09ICcxJyxcbiAgICByb2xsdXBPcHRpb25zOiB7XG4gICAgICBpbnB1dDogcmVzb2x2ZShcbiAgICAgICAgX19kaXJuYW1lLFxuICAgICAgICBwcm9jZXNzLmVudi5CVUlMRF9CUklER0UgPT09ICcxJyA/ICdwYXktYnJpZGdlL2luZGV4Lmh0bWwnIDogJ2luZGV4Lmh0bWwnXG4gICAgICApLFxuICAgICAgLy8gYnJpZGdlIFx1OTg3NVx1ODExQVx1NjcyQ1x1NTE4NVx1ODA1NFx1RkYwQ1x1NjUzOVx1NTQwRFx1NEUzQSBpbmRleC5odG1sIFx1NTQwRVx1NzUzMSBOZ2lueCBcdTRFRTUgL3BheS1icmlkZ2UvIFx1NzZFRVx1NUY1NVx1NjVCOVx1NUYwRlx1NjI1OFx1N0JBMVxuICAgICAgb3V0cHV0OlxuICAgICAgICBwcm9jZXNzLmVudi5CVUlMRF9CUklER0UgPT09ICcxJ1xuICAgICAgICAgID8geyBlbnRyeUZpbGVOYW1lczogJ2luZGV4LVtoYXNoXS5qcycsIGFzc2V0RmlsZU5hbWVzOiAnYXNzZXRzL1tuYW1lXS1baGFzaF1bZXh0bmFtZV0nIH1cbiAgICAgICAgICA6IHt9XG4gICAgfVxuICB9LFxuICBzZXJ2ZXI6IHtcbiAgICBob3N0OiAnMC4wLjAuMCcsXG4gICAgcG9ydDogNTE3MyxcbiAgICBwcm94eToge1xuICAgICAgJy9hcGknOiB7IHRhcmdldDogYXBpVGFyZ2V0LCBjaGFuZ2VPcmlnaW46IHRydWUgfSxcbiAgICAgICcvZmlsZXMnOiB7IHRhcmdldDogYXBpVGFyZ2V0LCBjaGFuZ2VPcmlnaW46IHRydWUgfVxuICAgIH1cbiAgfVxufSk7XG4iXSwKICAibWFwcGluZ3MiOiAiO0FBQW9ZLFNBQVMsb0JBQW9CO0FBQ2phLE9BQU8sU0FBUztBQUNoQixTQUFTLGVBQWU7QUFGeEIsSUFBTSxtQ0FBbUM7QUFJekMsSUFBTSxZQUFZLFFBQVEsSUFBSSxtQkFBbUI7QUFFakQsSUFBTyxzQkFBUSxhQUFhO0FBQUE7QUFBQTtBQUFBLEVBRzFCLE1BQU07QUFBQSxFQUNOLFNBQVMsQ0FBQyxJQUFJLENBQUM7QUFBQSxFQUNmLFNBQVM7QUFBQSxJQUNQLE9BQU87QUFBQTtBQUFBLE1BRUwscUJBQXFCLFFBQVEsa0NBQVcsbUJBQW1CO0FBQUEsSUFDN0Q7QUFBQSxFQUNGO0FBQUEsRUFDQSxPQUFPO0FBQUEsSUFDTCxRQUFRLFFBQVEsSUFBSSxpQkFBaUIsTUFBTSxvQkFBb0I7QUFBQSxJQUMvRCxhQUFhLFFBQVEsSUFBSSxpQkFBaUI7QUFBQSxJQUMxQyxlQUFlO0FBQUEsTUFDYixPQUFPO0FBQUEsUUFDTDtBQUFBLFFBQ0EsUUFBUSxJQUFJLGlCQUFpQixNQUFNLDBCQUEwQjtBQUFBLE1BQy9EO0FBQUE7QUFBQSxNQUVBLFFBQ0UsUUFBUSxJQUFJLGlCQUFpQixNQUN6QixFQUFFLGdCQUFnQixtQkFBbUIsZ0JBQWdCLGdDQUFnQyxJQUNyRixDQUFDO0FBQUEsSUFDVDtBQUFBLEVBQ0Y7QUFBQSxFQUNBLFFBQVE7QUFBQSxJQUNOLE1BQU07QUFBQSxJQUNOLE1BQU07QUFBQSxJQUNOLE9BQU87QUFBQSxNQUNMLFFBQVEsRUFBRSxRQUFRLFdBQVcsY0FBYyxLQUFLO0FBQUEsTUFDaEQsVUFBVSxFQUFFLFFBQVEsV0FBVyxjQUFjLEtBQUs7QUFBQSxJQUNwRDtBQUFBLEVBQ0Y7QUFDRixDQUFDOyIsCiAgIm5hbWVzIjogW10KfQo=
