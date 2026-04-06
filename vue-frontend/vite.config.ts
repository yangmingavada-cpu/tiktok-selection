import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'node:path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  optimizeDeps: {
    include: [
      'dayjs/plugin/localizedFormat',
      'dayjs/plugin/customParseFormat',
      'dayjs/plugin/isoWeek',
      'dayjs/plugin/advancedFormat',
      'dayjs/plugin/weekOfYear',
      'dayjs/plugin/weekYear',
    ],
    exclude: ['@univerjs/engine-render', '@univerjs/engine-formula'],
  },
  server: {
    port: 3000,
    host: '0.0.0.0',
    watch: {
      usePolling: true,
    },
    proxy: {
      '/api': {
        target: 'http://java-backend:8080',
        changeOrigin: true,
        proxyTimeout: 660_000,
        timeout: 660_000,
      },
    },
  },
})
