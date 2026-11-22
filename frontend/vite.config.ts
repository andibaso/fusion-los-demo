import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

// Port 3003 is the only entry point. The browser calls /api and /temporal on the same origin;
// Vite forwards them to local services. LOS_PUBLIC_ORIGIN (in ../.env) names the public URL,
// for example https://loans.example.com, when the app sits behind a reverse proxy.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '..', 'LOS_')
  const publicHost = env.LOS_PUBLIC_ORIGIN ? new URL(env.LOS_PUBLIC_ORIGIN).hostname : undefined

  const server = {
    port: 3003,
    strictPort: true,
    host: true,
    allowedHosts: publicHost ? [publicHost] : [],
    proxy: {
      '/api': { target: 'http://127.0.0.1:8080' },
      '/temporal': { target: 'http://127.0.0.1:8233', ws: true },
    },
  }

  return { plugins: [react()], server, preview: server }
})
