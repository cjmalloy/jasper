import { expect, test } from '@playwright/test';
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
  remoteHeaders,
  removeTag,
  replApi,
  replApiProxy,
  runId,
  updateRef,
  userRun,
} from '../setup';

test.describe.serial('Origin Push', () => {
  const id = runId();
  const query = `e2e/push/${id}`;
  const headers = mainHeaders();
  const pushOnChangeOrigin = `https://e2e.jasper/origin/push-on-change/${id}`;
  const manualOrigin = `https://e2e.jasper/origin/manual-push/${id}`;
  const pushOnChangeUrl = `https://e2e.jasper/push/on-change/${id}`;
  const manualUrl = `https://e2e.jasper/push/manual/${id}`;

  async function expectPushed(request: any, url: string, title: string) {
    await expect.poll(async () => (await findRef(request, replApi, remoteHeaders(), url, '@repl'))?.title, {
      timeout: 60_000,
    }).toBe(title);
  }

  test.beforeAll(async ({ request }) => {
    await installOriginPlugins(request);
  });

  test.afterAll(async ({ request }) => {
    await disable(request, pushOnChangeOrigin);
  });

  test('creates a remote origin with push on change', async ({ request }) => {
    const origin = await createOrigin(request, {
      url: pushOnChangeOrigin,
      title: 'Push on change @repl',
      remote: '@repl',
      proxy: replApiProxy,
      push: { pushOnChange: true, query },
      enabled: true,
    });
    expect(origin.tags).toEqual(expect.arrayContaining(['+plugin/origin/push', '+plugin/cron']));
  });

  test('pushes a new ref on change', async ({ request }) => {
    await createRef(request, mainApi, headers, {
      url: pushOnChangeUrl,
      title: 'Push on change',
      tags: ['public', query, '+user/debug'],
    });
    await expectPushed(request, pushOnChangeUrl, 'Push on change');
  });

  test('pushes an edited ref on change', async ({ request }) => {
    const ref = await getRef(request, mainApi, headers, pushOnChangeUrl);
    ref.title = 'Push on change edited';
    await ok(updateRef(request, mainApi, headers, ref));
    await expectPushed(request, pushOnChangeUrl, 'Push on change edited');
  });

  test('pushes manually', async ({ request }) => {
    await createOrigin(request, {
      url: manualOrigin,
      title: 'Manual push @repl',
      remote: '@repl',
      proxy: replApiProxy,
      push: { query },
    });
    await createRef(request, mainApi, headers, {
      url: manualUrl,
      title: 'Manual push',
      tags: ['public', query, '+user/debug'],
    });
    await userRun(request, mainApi, headers, manualOrigin);
    await expectPushed(request, manualUrl, 'Manual push');
  });
});
