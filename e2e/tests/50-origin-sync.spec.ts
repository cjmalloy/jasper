import { type APIRequestContext, expect, test } from '@playwright/test';
import {
  createOrigin,
  createRef,
  findRef,
  getRef,
  installOriginPlugins,
  mainApi,
  mainHeaders,
  ok,
  type Ref,
  remoteHeaders,
  replApi,
  replApiProxy,
  runId,
  updateRef,
  userRun,
} from '../setup';

/**
 * Replicate origins that already contain a lot of data, and keep writing to them between syncs.
 * Pull and push use the latest modified date as a cursor and page through it in small batches,
 * so any entity that is skipped or stops the cursor shows up as missing data here.
 */

const batchSize = 7;

/**
 * Same as the jasper-ui delete notice: clear the Ref and only keep user, locked and public tags.
 */
function deleteNotice(ref: Ref): Ref {
  return {
    url: ref.url,
    origin: ref.origin,
    published: ref.published,
    modified: ref.modified,
    tags: ['plugin/delete', 'internal', ...(ref.tags || []).filter(t => /^[_+]?(user|locked|public)(\/|$)/.test(t))],
  };
}

async function count(request: APIRequestContext, api: string, headers: Record<string, string>, query: string) {
  const resp = await ok(request.get(api + '/api/v1/ref/count', { headers, params: { query } }), 'Ref Count');
  return Number(await resp.text());
}

async function edit(request: APIRequestContext, api: string, headers: Record<string, string>, url: string, origin: string, fn: (ref: Ref) => Ref) {
  const ref = await getRef(request, api, headers, url, origin);
  await ok(updateRef(request, api, headers, fn(ref)), 'Edit Ref');
}

async function findTag(request: APIRequestContext, api: string, headers: Record<string, string>, type: 'ext' | 'user', tag: string) {
  const resp = await request.get(`${api}/api/v1/${type}`, { headers, params: { tag } });
  if (resp.status() === 404) return null;
  await ok(resp, 'Find ' + type);
  return await resp.json();
}

/**
 * Tracks what a replicated origin should look like.
 */
class Expected {
  titles = new Map<string, string>();
  deleted = new Set<string>();
  n = 0;

  constructor(readonly prefix: string) {}

  next() {
    return `${this.prefix}/${this.n++}`;
  }

  live() {
    return [...this.titles.keys()].filter(url => !this.deleted.has(url));
  }
}

test.describe.serial('Origin Sync', () => {
  const id = runId();
  const headers = mainHeaders();

  test.beforeAll(async ({ request }) => {
    await installOriginPlugins(request);
  });

  test.describe.serial('Pull', () => {
    const tag = `e2e/sync/pull/${id}`;
    const local = '@repl.sync';
    const originUrl = `https://e2e.jasper/origin/sync-pull/${id}`;
    const expected = new Expected(`https://e2e.jasper/sync/pull/${id}`);
    const exts = new Map<string, string>();
    const users = new Map<string, string>();

    async function create(request: APIRequestContext, title: string) {
      const url = expected.next();
      const prev = expected.n > 1 ? `${expected.prefix}/${expected.n - 2}` : undefined;
      await createRef(request, replApi, remoteHeaders(), {
        url,
        origin: '@repl',
        title,
        comment: `Comment for ${title}`,
        tags: ['public', tag],
        sources: prev ? [prev] : undefined,
      });
      expected.titles.set(url, title);
    }

    async function retitle(request: APIRequestContext, url: string, title: string) {
      await edit(request, replApi, remoteHeaders(), url, '@repl', ref => ({ ...ref, title }));
      expected.titles.set(url, title);
    }

    async function remove(request: APIRequestContext, url: string) {
      await edit(request, replApi, remoteHeaders(), url, '@repl', deleteNotice);
      expected.deleted.add(url);
    }

    async function createExt(request: APIRequestContext, name: string) {
      const extTag = `${tag}/${exts.size}`;
      await ok(request.post(replApi + '/api/v1/ext', { headers: remoteHeaders(), data: { tag: extTag, origin: '@repl', name } }), 'Create Ext');
      exts.set(extTag, name);
    }

    async function createUser(request: APIRequestContext, name: string) {
      const userTag = `+user/e2e/sync/${id}/${users.size}`;
      await ok(request.post(replApi + '/api/v1/user', { headers: remoteHeaders(), data: { tag: userTag, origin: '@repl', name } }), 'Create User');
      users.set(userTag, name);
    }

    async function pullAndVerify(request: APIRequestContext) {
      await userRun(request, mainApi, headers, originUrl);
      await expect.poll(() => count(request, mainApi, headers, `${tag}${local}`), {
        message: 'Live Refs pulled',
        timeout: 90_000,
      }).toBe(expected.live().length);
      for (const url of expected.live()) {
        expect((await findRef(request, mainApi, headers, url, local))?.title, url).toBe(expected.titles.get(url));
      }
      for (const url of expected.deleted) {
        expect((await findRef(request, mainApi, headers, url, local))?.tags, url).toContain('plugin/delete');
      }
      for (const [extTag, name] of exts) {
        await expect.poll(async () => (await findTag(request, mainApi, headers, 'ext', extTag + local))?.name, extTag).toBe(name);
      }
      for (const [userTag, name] of users) {
        await expect.poll(async () => (await findTag(request, mainApi, headers, 'user', userTag + local))?.name, userTag).toBe(name);
      }
    }

    test('pulls an origin that already has a lot of data', async ({ request }) => {
      for (let i = 0; i < 40; i++) await create(request, `Seed ${i}`);
      for (let i = 0; i < 10; i++) await retitle(request, `${expected.prefix}/${i * 3}`, `Seed ${i * 3} edited`);
      for (let i = 0; i < 5; i++) await remove(request, `${expected.prefix}/${i * 7 + 1}`);
      for (let i = 0; i < 3; i++) await createExt(request, `Ext ${i}`);
      for (let i = 0; i < 3; i++) await createUser(request, `User ${i}`);

      await createOrigin(request, {
        url: originUrl,
        title: 'Sync pull @repl',
        local,
        remote: '@repl',
        proxy: replApiProxy,
        // Tombstones lose the query tag, so also pull delete notices
        pull: { query: `${tag}|plugin/delete`, batchSize },
      });
      await pullAndVerify(request);
    });

    test('keeps pulling new data written after the first pull', async ({ request }) => {
      for (let round = 0; round < 3; round++) {
        for (let i = 0; i < 12; i++) await create(request, `Round ${round} new ${i}`);
        const live = expected.live();
        for (let i = 0; i < 4; i++) await retitle(request, live[(round * 5 + i * 3) % live.length], `Round ${round} edit ${i}`);
        for (let i = 0; i < 3; i++) await remove(request, expected.live()[(round * 2 + i * 5) % expected.live().length]);
        await createExt(request, `Round ${round} ext`);
        await createUser(request, `Round ${round} user`);
        await pullAndVerify(request);
      }
    });

    test('does not skip data written while pulling', async ({ request }) => {
      const writes = (async () => {
        for (let i = 0; i < 30; i++) {
          await Promise.all([
            create(request, `Concurrent ${i} a`),
            create(request, `Concurrent ${i} b`),
            retitle(request, expected.live()[i % expected.live().length], `Concurrent edit ${i}`),
          ]);
        }
      })();
      const pulls = (async () => {
        for (let i = 0; i < 10; i++) {
          await userRun(request, mainApi, headers, originUrl);
          await new Promise(r => setTimeout(r, 500));
        }
      })();
      await Promise.all([writes, pulls]);
      await pullAndVerify(request);
    });
  });

  test.describe.serial('Push', () => {
    const tag = `e2e/sync/push/${id}`;
    const originUrl = `https://e2e.jasper/origin/sync-push/${id}`;
    const expected = new Expected(`https://e2e.jasper/sync/push/${id}`);

    async function create(request: APIRequestContext, title: string) {
      const url = expected.next();
      await createRef(request, mainApi, headers, {
        url,
        title,
        comment: `Comment for ${title}`,
        tags: ['public', tag, '+user/debug'],
      });
      expected.titles.set(url, title);
    }

    async function retitle(request: APIRequestContext, url: string, title: string) {
      await edit(request, mainApi, headers, url, '', ref => ({ ...ref, title }));
      expected.titles.set(url, title);
    }

    async function remove(request: APIRequestContext, url: string) {
      await edit(request, mainApi, headers, url, '', deleteNotice);
      expected.deleted.add(url);
    }

    async function pushAndVerify(request: APIRequestContext) {
      await userRun(request, mainApi, headers, originUrl);
      await expect.poll(() => count(request, replApi, remoteHeaders(), `${tag}@repl`), {
        message: 'Live Refs pushed',
        timeout: 90_000,
      }).toBe(expected.live().length);
      for (const url of expected.live()) {
        expect((await findRef(request, replApi, remoteHeaders(), url, '@repl'))?.title, url).toBe(expected.titles.get(url));
      }
      for (const url of expected.deleted) {
        expect((await findRef(request, replApi, remoteHeaders(), url, '@repl'))?.tags, url).toContain('plugin/delete');
      }
    }

    test('pushes an origin that already has a lot of data', async ({ request }) => {
      for (let i = 0; i < 40; i++) await create(request, `Seed ${i}`);
      for (let i = 0; i < 10; i++) await retitle(request, `${expected.prefix}/${i * 3}`, `Seed ${i * 3} edited`);
      for (let i = 0; i < 5; i++) await remove(request, `${expected.prefix}/${i * 7 + 1}`);

      await createOrigin(request, {
        url: originUrl,
        title: 'Sync push @repl',
        remote: '@repl',
        proxy: replApiProxy,
        // Tombstones lose the query tag, so also push delete notices
        push: { query: `${tag}|plugin/delete:+user/debug`, batchSize },
      });
      await pushAndVerify(request);
    });

    test('keeps pushing new data written after the first push', async ({ request }) => {
      for (let round = 0; round < 3; round++) {
        for (let i = 0; i < 12; i++) await create(request, `Round ${round} new ${i}`);
        const live = expected.live();
        for (let i = 0; i < 4; i++) await retitle(request, live[(round * 5 + i * 3) % live.length], `Round ${round} edit ${i}`);
        for (let i = 0; i < 3; i++) await remove(request, expected.live()[(round * 2 + i * 5) % expected.live().length]);
        await pushAndVerify(request);
      }
    });
  });
});
