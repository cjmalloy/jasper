import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  globalSetup: require.resolve('./global-setup.ts'),
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  timeout: 120_000,
  expect: { timeout: 15_000 },
  reporter: process.env.CI ? [
      ['list'],
      ['html', { outputFolder: 'reports/html', open: 'never' }],
      ['json', { outputFile: 'reports/results.json' }],
    ] : 'html',
  use: {
    baseURL: process.env.BASE_URL || 'http://localhost:8080',
    trace: 'on-first-retry',
    video: process.env.CI ? 'on' : 'off',
    viewport: { width: 1280, height: 720 }, // Prevent mobile layout
    actionTimeout: 10_000,
  },
});
