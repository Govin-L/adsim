import path from "path"
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { execSync } from 'child_process'

const commitHash = execSync('git rev-parse --short HEAD').toString().trim()
const commitFull = execSync('git rev-parse HEAD').toString().trim()

export default defineConfig({
  plugins: [react(), tailwindcss()],
  define: {
    __COMMIT_SHORT__: JSON.stringify(commitHash),
    __COMMIT_FULL__: JSON.stringify(commitFull),
  },
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8180',
        changeOrigin: true,
      },
    },
  },
})
