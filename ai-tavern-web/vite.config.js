import { fileURLToPath, URL } from 'node:url'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [
    vue(),
    tailwindcss(),
    AutoImport({
      resolvers: [ElementPlusResolver()],
    }),
    Components({
      resolvers: [ElementPlusResolver()],
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    watch: {
      // 某些编辑器/工具用「写临时文件再替换」的方式保存，
      // 临时目录被监听时会触发 EBUSY 把 dev server 直接搞崩
      ignored: ['**/.*tmpdir/**', '**/*.tmp', '**/.git/**'],
    },
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
        // SSE 流式：禁止代理缓冲，否则前端会整段才收到
        configure: (proxy) => {
          proxy.on('proxyRes', (proxyRes, _req, res) => {
            const ct = String(proxyRes.headers['content-type'] || '')
            if (ct.includes('text/event-stream')) {
              res.setHeader('Cache-Control', 'no-cache, no-transform')
              res.setHeader('Connection', 'keep-alive')
              res.setHeader('X-Accel-Buffering', 'no')
              // 压缩会攒包，流式时去掉
              delete proxyRes.headers['content-encoding']
              delete proxyRes.headers['content-length']
            }
          })
        },
      },
    },
  },
})
