import { type APIRequestContext, expect, test } from '@playwright/test';
import {
  createOrigin,
  disable,
  createRef,
  findRef,
  getRef,
  installOriginPlugins,
  mainApi,
  mainHeaders,
  ok,
  page as refPage,
  remoteHeaders,
  removeTag,
  responses,
  runId,
  sshHost,
  tunnelApi,
  tunnelApiProxy,
  updateRef,
  userRun,
} from '../setup';

/**
 * Push to a remote server through jasper-ssh.
 * On the remote server @open has web access, @tunnel only has SSH access.
 */
test.describe.serial('Origin Tunnel', () => {
  const id = runId();
  const user = 'tunnel';
  const query = `e2e/tunnel/${id}`;
  const headers = mainHeaders(user);
  const openOrigin = `https://e2e.jasper/origin/tunnel-open/${id}`;
  const deniedOrigin = `https://e2e.jasper/origin/tunnel-denied/${id}`;
  const openUrl = `https://e2e.jasper/tunnel/open/${id}`;
  const deniedUrl = `https://e2e.jasper/tunnel/denied/${id}`;
  const deniedUiUrl = `https://e2e.jasper/tunnel/denied/ui/${id}`;

  /**
   * Set the remote server web origins, the same as editing the server config template.
   */
  async function setWebOrigins(request: APIRequestContext, webOrigins: string[]) {
    const tag = '_config/server';
    const template = await (await ok(request.get(tunnelApi + '/api/v1/template', { headers: remoteHeaders(), params: { tag } }))).json();
    await ok(request.patch(tunnelApi + '/api/v1/template', {
      headers: { ...remoteHeaders(), 'Content-Type': 'application/merge-patch+json' },
      params: { tag, cursor: template.modified },
      data: JSON.stringify({ config: { webOrigins } }),
    }), 'Set web origins');
  }

  async function upsertRemoteUser(request: APIRequestContext, origin: string, authorizedKeys: string) {
    const tag = `+user/${user}${origin}`;
    const headers = { ...remoteHeaders(), 'Local-Origin': origin };
    await expect.poll(async () => (await request.get(tunnelApi + '/api/v1/user/whoami', { headers })).status(), {
      message: `Waiting for web access to ${origin}`,
    }).toBe(200);
    const existing = await request.get(tunnelApi + '/api/v1/user', { headers, params: { tag } });
    if (existing.status() === 404) {
      await ok(request.post(tunnelApi + '/api/v1/user', {
        headers,
        data: { tag: `+user/${user}`, origin, authorizedKeys },
      }), 'Create remote user');
    } else {
      await ok(existing);
      await ok(request.patch(tunnelApi + '/api/v1/user', {
        headers: { ...headers, 'Content-Type': 'application/merge-patch+json' },
        params: { tag, cursor: (await existing.json()).modified },
        data: JSON.stringify({ authorizedKeys }),
      }), 'Update remote user');
    }
  }

  test.beforeAll(async ({ request }) => {
    await installOriginPlugins(request);
    // Generate an SSH key for +user/tunnel on the main server
    const created = await request.post(mainApi + '/api/v1/user', { headers, data: { tag: `+user/${user}` } });
    expect([201, 409], await created.text()).toContain(created.status());
    await ok(request.post(mainApi + '/api/v1/user/keygen', { headers, params: { tag: `+user/${user}` } }), 'Keygen');
    const local = await (await ok(request.get(mainApi + '/api/v1/user', { headers, params: { tag: `+user/${user}` } }))).json();
    const pubKey = Buffer.from(local.pubKey, 'base64').toString().trim();
    expect(pubKey).toMatch(/^ssh-/);
    // Authorize the key on the remote server, which writes the authorized_keys file for jasper-ssh.
    // Users can only be created from their own origin, so temporarily allow web access to @tunnel.
    await setWebOrigins(request, ['', '@open', '@tunnel']);
    await upsertRemoteUser(request, '@open', pubKey);
    await upsertRemoteUser(request, '@tunnel', pubKey);
    // Forget to open web access to @tunnel
    await setWebOrigins(request, ['', '@open']);
    await expect.poll(async () => (await request.get(tunnelApi + '/api/v1/user/whoami', {
      headers: { ...remoteHeaders(), 'Local-Origin': '@tunnel' },
    })).json().then(whoami => whoami.admin)).toBe(false);
  });

  test.afterAll(async ({ request }) => {
    await disable(request, deniedOrigin);
  });

  test('pushes through the tunnel with web access', async ({ request }) => {
    await createOrigin(request, {
      url: openOrigin,
      title: 'Tunnel @open',
      user,
      remote: '@open',
      proxy: tunnelApiProxy,
      tunnel: { sshHost, sshPort: 22 },
      push: { query },
    });
    await createRef(request, mainApi, headers, {
      url: openUrl,
      title: 'Tunnel push',
      tags: ['public', query, `+user/${user}`],
    });
    // jasper-ssh restarts to load the new authorized_keys, so retry until the tunnel is up
    await expect.poll(async () => {
      const origin = await getRef(request, mainApi, headers, openOrigin);
      if (origin.tags?.includes('+plugin/error')) await removeTag(request, mainApi, headers, '+plugin/error', openOrigin);
      await userRun(request, mainApi, headers, openOrigin);
      await new Promise(r => setTimeout(r, 3_000));
      return (await findRef(request, tunnelApi, remoteHeaders(), openUrl, '@open'))?.title;
    }, { timeout: 120_000, intervals: [1_000] }).toBe('Tunnel push');
    const pushed = await findRef(request, tunnelApi, remoteHeaders(), openUrl, '@open');
    expect(pushed?.tags).toEqual(expect.arrayContaining([`+user/${user}`]));
  });

  test('creates a remote origin with push on change without web access', async ({ request }) => {
    const origin = await createOrigin(request, {
      url: deniedOrigin,
      title: 'Tunnel @tunnel (no web access)',
      user,
      remote: '@tunnel',
      proxy: tunnelApiProxy,
      tunnel: { sshHost, sshPort: 22 },
      push: { pushOnChange: true },
      enabled: true,
    });
    expect(origin.tags).toEqual(expect.arrayContaining(['+plugin/origin/push', '+plugin/cron']));
  });

  test('saving refs succeeds while push on change is denied', async ({ request }) => {
    const admin = mainHeaders('debug', 'ADMIN');
    await createRef(request, mainApi, admin, {
      url: deniedUrl,
      title: 'Edit 0',
      tags: ['public', '+user/debug'],
    });
    for (let i = 1; i <= 5; i++) {
      const ref = await getRef(request, mainApi, admin, deniedUrl);
      ref.title = `Edit ${i}`;
      const resp = await updateRef(request, mainApi, admin, ref);
      expect(resp.status(), await resp.text()).toBe(200);
      await new Promise(r => setTimeout(r, 1_000));
    }
    expect((await getRef(request, mainApi, admin, deniedUrl)).title).toBe('Edit 5');
  });

  test('push error is logged on the remote origin', async ({ request }) => {
    await expect.poll(async () => (await responses(request, mainApi, headers, deniedOrigin, '+plugin/log'))
      .map(log => log.title)
      .join('\n'), { timeout: 60_000 }).toMatch(/error pushing/i);
    const logs = await responses(request, mainApi, headers, deniedOrigin, '+plugin/log');
    for (const log of logs) {
      expect(log.origin || '').toBe('');
      expect(log.tags).toEqual(expect.arrayContaining(['internal', '+plugin/log']));
    }
    expect(logs.map(log => log.comment).join('\n')).toContain('403');
    // Errors disable push on change until cleared
    expect((await getRef(request, mainApi, headers, deniedOrigin)).tags).toContain('+plugin/error');
    // Nothing was written on the remote
    expect(await refPage(request, tunnelApi, remoteHeaders(), { query: '@tunnel' })).toHaveLength(0);
  });

  test('editing a ref in the client succeeds while push on change is denied', async ({ page, request }) => {
    // Clear the error to re-enable push on change, so each save triggers another denied push
    await removeTag(request, mainApi, headers, '+plugin/error', deniedOrigin);
    const logCount = (await responses(request, mainApi, headers, deniedOrigin, '+plugin/log')).length;
    await createRef(request, mainApi, mainHeaders('debug', 'ADMIN'), {
      url: deniedUiUrl,
      title: 'UI Edit',
      tags: ['public', '+user/debug'],
    });
    for (let i = 1; i <= 3; i++) {
      await page.goto(`/ref/e/${encodeURIComponent(deniedUiUrl)}?debug=ADMIN`, { waitUntil: 'networkidle' });
      await page.locator('.full-page.ref .actions .fake-link', { hasText: 'edit' }).first().click();
      await page.locator('.full-page.ref form [name=title]').fill(`UI Edit ${i}`);
      const savePromise = page.waitForResponse(resp => resp.url().includes('/api/v1/ref') && resp.request().method() === 'PUT');
      await page.locator('.full-page.ref form.form button', { hasText: 'save' }).click();
      const save = await savePromise;
      expect(save.status(), await save.text()).toBe(200);
      await expect(page.locator('.full-page.ref .link a')).toHaveText(`UI Edit ${i}`);
      await expect(page.getByText(/access denied/i)).toHaveCount(0);
    }
    await expect.poll(async () => (await responses(request, mainApi, headers, deniedOrigin, '+plugin/log')).length, {
      timeout: 60_000,
    }).toBeGreaterThan(logCount);
  });
});
