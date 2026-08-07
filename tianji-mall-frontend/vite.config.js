import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: { '@': path.resolve(__dirname, 'src') }
  },
  server: {
    host: '127.0.0.1',
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://192.168.150.11:8080',
        changeOrigin: true
      },
      // 上传图片静态资源（后端返回 /uploads/x.jpg，经网关映射到 mall-goods-order）
      '/uploads': {
        target: 'http://192.168.150.11:8080',
        changeOrigin: true
      }
    }
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return
          if (id.includes('element-plus') || id.includes('@element-plus')) return 'element-plus'
          if (id.includes('/vue/') || id.includes('vue-router') || id.includes('pinia') || id.includes('axios')) return 'vendor'
        }
      }
    }
  }
})
