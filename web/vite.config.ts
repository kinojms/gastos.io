import { svelte } from '@sveltejs/vite-plugin-svelte'
import { defineConfig } from 'vitest/config'
import { VitePWA } from 'vite-plugin-pwa'
import { existsSync, readFileSync } from 'node:fs'
import pkg from './package.json' with { type: 'json' }

/** Feedback relay URL from the one shared file (env FEEDBACK_URL overrides it, for e2e); blank hides the feature. */
function feedbackUrl(): string {
  const file = new URL('../config/feedback-url.txt', import.meta.url)
  const url = (process.env.FEEDBACK_URL ?? (existsSync(file) ? readFileSync(file, 'utf8') : '')).trim()
  if (url && !url.startsWith('https://')) throw new Error('config/feedback-url.txt must be blank or an https:// URL')
  return url
}

export default defineConfig({
  base: '/gastos.io/',
  define: { __APP_VERSION__: JSON.stringify(pkg.version), __FEEDBACK_URL__: JSON.stringify(feedbackUrl()) },
  plugins: [
    svelte(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['icons/apple-touch-icon.png', 'icons/favicon-32.png'],
      manifest: {
        name: 'Gastos.io',
        short_name: 'Gastos',
        start_url: './',
        scope: './',
        display: 'standalone',
        orientation: 'portrait',
        background_color: '#0B1016',
        theme_color: '#0B1016',
        icons: [
          { src: 'icons/icon-192.png', sizes: '192x192', type: 'image/png' },
          { src: 'icons/icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'any' },
          { src: 'icons/icon-maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
      workbox: { globPatterns: ['**/*.{js,css,html,png,svg,woff2}'] },
    }),
  ],
  test: {
    environment: 'jsdom',
    setupFiles: ['src/test/setup.ts'],
    include: ['src/**/*.test.ts'],
  },
})
