import { chromium, type FullConfig, request } from '@playwright/test';
import { mainApi, replApi, tunnelApi } from './setup';

async function waitForHealth(api: string) {
  const ctx = await request.newContext();
  const deadline = Date.now() + 300_000;
  try {
    while (true) {
      try {
        if ((await ctx.get(api + '/management/health/readiness')).ok()) return;
      } catch {
        // Not started yet
      }
      if (Date.now() > deadline) throw new Error(`Timed out waiting for ${api}`);
      await new Promise(r => setTimeout(r, 1_000));
    }
  } finally {
    await ctx.dispose();
  }
}

async function globalSetup(config: FullConfig) {
  await Promise.all([mainApi, replApi, tunnelApi].map(waitForHealth));
  const baseURL = process.env.BASE_URL || config.projects[0]?.use?.baseURL?.toString() || 'http://localhost:8080';
  const browser = await chromium.launch();
  const page = await browser.newPage();
  await page.goto(baseURL + '/?debug=ADMIN');
  await page.waitForLoadState('networkidle', { timeout: 300_000 });
  await browser.close();
}
export default globalSetup;
