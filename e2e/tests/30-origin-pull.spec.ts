import { expect, test } from '@playwright/test';
import {
  createOrigin,
  disable,
  createRef,
  findRef,
  installOriginPlugins,
  mainApi,
  mainHeaders,
  remoteHeaders,
  removeTag,
  replApi,
  replApiProxy,
  runId,
  userRun,
} from '../setup';

test.describe.serial('Origin Pull', () => {
  const id = runId();
  const query = `e2e/pull/${id}`;
  const headers = mainHeaders();
  const manualOrigin = `https://e2e.jasper/origin/manual-pull/${id}`;
  const streamOrigin = `https://e2e.jasper/origin/stream-pull/${id}`;
  const manualUrl = `https://e2e.jasper/pull/manual/${id}`;
  const streamUrl = `https://e2e.jasper/pull/stream/${id}`;

  async function expectPulled(request: any, url: string, title: string) {
    await expect.poll(async () => (await findRef(request, mainApi, headers, url, '@repl'))?.title, {
      timeout: 60_000,
    }).toBe(title);
  }

  test.beforeAll(async ({ request }) => {
    await installOriginPlugins(request);
  });

  test.afterAll(async ({ request }) => {
    await disable(request, streamOrigin);
  });

  test('pulls manually', async ({ request }) => {
    await createRef(request, replApi, remoteHeaders(), {
      url: manualUrl,
      origin: '@repl',
      title: 'Manual pull',
      tags: ['public', query],
    });
    await createOrigin(request, {
      url: manualOrigin,
      title: 'Manual pull @repl',
      local: '@repl',
      remote: '@repl',
      proxy: replApiProxy,
      pull: { query },
    });
    await userRun(request, mainApi, headers, manualOrigin);
    await expectPulled(request, manualUrl, 'Manual pull');
  });

  test('pulls continuously when enabled', async ({ request }) => {
    await createOrigin(request, {
      url: streamOrigin,
      title: 'Streaming pull @repl',
      // Pull origins are monitored per local origin
      local: '@repl.stream',
      remote: '@repl',
      proxy: replApiProxy,
      pull: { query, websocket: true },
      enabled: true,
    });
    await expect.poll(async () => {
      // Keep creating refs until the websocket is connected
      const url = `${streamUrl}/${Date.now()}`;
      await createRef(request, replApi, remoteHeaders(), {
        url,
        origin: '@repl',
        title: 'Streaming pull',
        tags: ['public', query],
      });
      await new Promise(r => setTimeout(r, 2_000));
      return (await findRef(request, mainApi, headers, url, '@repl.stream'))?.title;
    }, { timeout: 90_000, intervals: [1_000] }).toBe('Streaming pull');
  });
});
