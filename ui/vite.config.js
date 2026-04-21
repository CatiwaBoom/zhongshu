import { defineConfig } from 'vite'
      import vue from '@vitejs/plugin-vue'
      import { fileURLToPath, URL } from 'node:url'

      export default defineConfig({
        plugins: [vue()],
        resolve: {
          alias: {
            '@': fileURLToPath(new URL('./src', import.meta.url))
          }
        },
        server: {// 监听所有网络接口，以便局域网其他设备可以通过 http://<host-ip>:5173 访问
          // 等价于命令行 `vite --host`，也可以设置为 true
          host: '0.0.0.0',
          // 明确端口（可选，默认 5173）
          port: 5173,
          // 允许跨域请求（开发时常用）
          cors: true,
          proxy: {
            '/api': {
              target: 'http://localhost:7788',
              changeOrigin: true,
              rewrite: (path) => path.replace(/^\/api/, '')
            }
          }
        }
      })