import { expect, test } from '@playwright/test';
import { mainApi, mainHeaders, ok, remoteHeaders, replApi, tunnelApi } from '../setup';

test.describe('Smoke Tests', () => {
  for (const [name, api] of [['main', mainApi], ['repl', replApi], ['tunnel', tunnelApi]]) {
    test(`${name} server is healthy`, async ({ request }) => {
      const resp = await ok(request.get(api + '/management/health'));
      expect((await resp.json()).status).toBe('UP');
    });
  }

  test('main server authenticates debug admin JWT', async ({ request }) => {
    const resp = await ok(request.get(mainApi + '/api/v1/user/whoami', { headers: mainHeaders('debug', 'ADMIN') }));
    const whoami = await resp.json();
    expect(whoami.tag).toBe('+user/debug');
    expect(whoami.admin).toBe(true);
  });

  test('main server is anonymous without a token', async ({ request }) => {
    const resp = await ok(request.get(mainApi + '/api/v1/user/whoami'));
    const whoami = await resp.json();
    expect(whoami.tag || '').toBe('');
    expect(whoami.admin).toBeFalsy();
  });

  test('remote servers use default admin role', async ({ request }) => {
    for (const api of [replApi, tunnelApi]) {
      const resp = await ok(request.get(api + '/api/v1/user/whoami', { headers: remoteHeaders() }));
      expect((await resp.json()).admin).toBe(true);
    }
  });

  test('loads the client', async ({ page }) => {
    await page.goto('/?debug=USER', { waitUntil: 'networkidle' });
    await expect(page.getByText('Powered by Jasper')).toBeVisible();
  });
});
