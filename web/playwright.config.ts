import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: 'e2e',
  fullyParallel: false,
  workers: 1,
  use: { baseURL: 'http://localhost:4173/gastos.io/', trace: 'retain-on-failure' },
  projects: [{ name: 'webkit', use: { ...devices['iPhone 13'] } }],
  webServer: {
    command: 'npm run build && npm run preview -- --port 4173 --strictPort',
    // e2e never talks to the real relay; tests mock this URL.
    env: { FEEDBACK_URL: 'https://relay.test/' },
    url: 'http://localhost:4173/gastos.io/',
    reuseExistingServer: false,
    timeout: 120_000,
  },
})
