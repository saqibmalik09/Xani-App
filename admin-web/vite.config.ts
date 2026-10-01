import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const api = env.VITE_API_BASE_URL || 'https://api.smilee.online'
  return {
    plugins: [react()],
    server: {
      port: 5173,
      proxy: {
        // Same-origin in dev so the browser never hits CORS on the identity API.
        '/v1': { target: api, changeOrigin: true, secure: true },
      },
    },
  }
})
