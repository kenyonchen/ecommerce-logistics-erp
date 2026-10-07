import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // 卖家业务端固定端口，与后端 SecurityConfig 的 CORS 白名单一致
    port: 5120,
    strictPort: true
  }
})