import { type APIRequestContext, expect, test } from '@playwright/test';
import {
  archiveApi,
  archiveApiProxy,
  archiveHeaders,
  createOrigin,
  createRef,
  deleteRef,
  findRef,
  getRef,
  installOriginPlugins,
  mainApi,
  mainHeaders,
  ok,
  page,
  type Ref,
  remoteHeaders,
  replApi,
  replApiProxy,
  runId,
  updateRef,
  userRun,
} from '../setup';

/**
 * The archive server (@archive) pulls @repl from repl-web into @archive.repl and keeps every version.
 */

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

async function edit(request: APIRequestContext, api: string, headers: Record<string, string>, url: string, origin: string, fn: (ref: Ref) => Ref) {
  const ref = await getRef(request, api, headers, url, origin);
  await ok(updateRef(request, api, headers, fn(ref)), 'Edit Ref');
}

async function findTag(request: APIRequestContext, api: string, headers: Record<string, string>, type: 'ext' | 'template', tag: string) {
  const resp = await request.get(`${api}/api/v1/${type}`, { headers, params: { tag } });
  if (resp.status() === 404) return null;
  await ok(resp, 'Find ' + type);
  return await resp.json();
}

async function titles(request: APIRequestContext, api: string, headers: Record<string, string>, url: string, query: string, obsolete?: boolean) {
  const params: Record<string, string | number> = { url, query, size: 100 };
  if (obsolete !== undefined) params.obsolete = String(obsolete);
  return (await page(request, api, headers, params)).map(r => r.title || '').sort();
}

test.describe.serial('Archive', () => {
  test.skip(!archiveApi, 'The archive profile is Postgres only');
  test.describe.configure({ timeout: 300_000 });

  const id = runId();
  const query = `e2e/archive/${id}`;
  const headers = archiveHeaders();
  const local = '@archive.repl';
  const originUrl = `https://e2e.jasper/origin/archive/${id}`;
  const historyUrl = `https://e2e.jasper/archive/history/${id}`;
  const pruneUrl = `https://e2e.jasper/archive/prune/${id}`;
  const extTag = `e2e/archive/ext/${id}`;
  const templateTag = `e2e/archive/template/${id}`;
  const mainOriginUrl = `https://e2e.jasper/origin/from-archive/${id}`;

  /**
   * Run the pull until the check passes. Pulls are idempotent, so a run that was skipped
   * because the previous one was still in progress is retried.
   */
  async function pullUntil<T>(request: APIRequestContext, check: () => Promise<T>, expected: T) {
    await expect.poll(async () => {
      await userRun(request, archiveApi, headers, originUrl);
      await new Promise(r => setTimeout(r, 2_000));
      return check();
    }, { timeout: 90_000, intervals: [1_000] }).toEqual(expected);
  }

  async function title(request: APIRequestContext, url: string) {
    return (await findRef(request, archiveApi, headers, url, local))?.title ?? null;
  }

  test.beforeAll(async ({ request }) => {
    await installOriginPlugins(request, archiveApi, headers, '@archive');
    await createOrigin(request, {
      api: archiveApi,
      headers,
      origin: '@archive',
      url: originUrl,
      title: 'Archive pull @repl',
      local: '@repl',
      remote: '@repl',
      proxy: replApiProxy,
      // Tombstones lose the query tag, so also pull delete notices
      pull: { query: `${query}|plugin/delete` },
    });
  });

  test('keeps history', async ({ request }) => {
    await createRef(request, replApi, remoteHeaders(), {
      url: historyUrl,
      origin: '@repl',
      title: 'Version 1',
      tags: ['public', query],
    });
    await pullUntil(request, () => title(request, historyUrl), 'Version 1');
    await edit(request, replApi, remoteHeaders(), historyUrl, '@repl', ref => ({ ...ref, title: 'Version 2' }));
    await pullUntil(request, () => title(request, historyUrl), 'Version 2');
    await edit(request, replApi, remoteHeaders(), historyUrl, '@repl', ref => ({ ...ref, title: 'Version 3' }));
    await pullUntil(request, () => title(request, historyUrl), 'Version 3');

    expect(await titles(request, archiveApi, headers, historyUrl, local, true)).toEqual(['Version 1', 'Version 2']);
    expect(await titles(request, archiveApi, headers, historyUrl, local, false)).toEqual(['Version 3']);
  });

  test('replicated delete becomes a tombstone', async ({ request }) => {
    await edit(request, replApi, remoteHeaders(), historyUrl, '@repl', deleteNotice);
    await pullUntil(request, async () => (await findRef(request, archiveApi, headers, historyUrl, local)) === null, true);

    expect(await titles(request, archiveApi, headers, historyUrl, local, true)).toEqual(['Version 1', 'Version 2', 'Version 3']);
  });

  test('re-created on the source', async ({ request }) => {
    await deleteRef(request, replApi, remoteHeaders(), historyUrl, '@repl');
    await createRef(request, replApi, remoteHeaders(), {
      url: historyUrl,
      origin: '@repl',
      title: 'Version 4',
      tags: ['public', query],
    });
    await pullUntil(request, () => title(request, historyUrl), 'Version 4');

    expect(await titles(request, archiveApi, headers, historyUrl, local, true)).toEqual(['', 'Version 1', 'Version 2', 'Version 3']);
  });

  test('keeps Ext and Template history', async ({ request }) => {
    const extName = async () => (await findTag(request, archiveApi, headers, 'ext', extTag + local))?.name ?? null;
    const templateName = async () => (await findTag(request, archiveApi, headers, 'template', templateTag + local))?.name ?? null;
    await ok(request.post(replApi + '/api/v1/ext', { headers: remoteHeaders(), data: { tag: extTag, origin: '@repl', name: 'Ext 1' } }), 'Create Ext');
    await ok(request.post(replApi + '/api/v1/template', { headers: remoteHeaders(), data: { tag: templateTag, origin: '@repl', name: 'Template 1' } }), 'Create Template');
    await pullUntil(request, async () => [await extName(), await templateName()], ['Ext 1', 'Template 1']);

    const ext = await findTag(request, replApi, remoteHeaders(), 'ext', extTag + '@repl');
    await ok(request.put(replApi + '/api/v1/ext', { headers: remoteHeaders(), data: { ...ext, name: 'Ext 2' } }), 'Update Ext');
    const template = await findTag(request, replApi, remoteHeaders(), 'template', templateTag + '@repl');
    await ok(request.put(replApi + '/api/v1/template', { headers: remoteHeaders(), data: { ...template, name: 'Template 2' } }), 'Update Template');
    await pullUntil(request, async () => [await extName(), await templateName()], ['Ext 2', 'Template 2']);

    // A regular server hard deletes tags and only replicates a deletor tag, which does not hide the tag on an archive.
    // Send the blank version an archive replicates for a deleted tag instead.
    const ext2 = await findTag(request, replApi, remoteHeaders(), 'ext', extTag + '@repl');
    await ok(request.put(replApi + '/api/v1/ext', { headers: remoteHeaders(), data: { tag: extTag, origin: '@repl', modified: ext2.modified } }), 'Blank Ext');
    const template2 = await findTag(request, replApi, remoteHeaders(), 'template', templateTag + '@repl');
    await ok(request.put(replApi + '/api/v1/template', { headers: remoteHeaders(), data: { tag: templateTag, origin: '@repl', modified: template2.modified } }), 'Blank Template');
    await pullUntil(request, async () => [await extName(), await templateName()], [null, null]);
  });

  test('prunes a tombstone', async ({ request }) => {
    await createRef(request, replApi, remoteHeaders(), {
      url: pruneUrl,
      origin: '@repl',
      title: 'Prune me',
      tags: ['public', query],
    });
    await pullUntil(request, () => title(request, pruneUrl), 'Prune me');
    await edit(request, replApi, remoteHeaders(), pruneUrl, '@repl', deleteNotice);
    await pullUntil(request, async () => (await findRef(request, archiveApi, headers, pruneUrl, local)) === null, true);
    expect(await titles(request, archiveApi, headers, pruneUrl, local)).toEqual(['', 'Prune me']);
    const source = await getRef(request, replApi, remoteHeaders(), pruneUrl, '@repl');

    await deleteRef(request, archiveApi, headers, pruneUrl, local);

    expect(await titles(request, archiveApi, headers, pruneUrl, local)).toEqual([]);
    // Pruning is local, no delete notice is sent
    const after = await getRef(request, replApi, remoteHeaders(), pruneUrl, '@repl');
    expect(after.modified).toBe(source.modified);
    expect(after.tags).toContain('plugin/delete');
  });

  test('regular server pulls from the archive', async ({ request }) => {
    await installOriginPlugins(request);
    await createOrigin(request, {
      url: mainOriginUrl,
      title: 'Pull @archive.repl',
      local: `@archive${id}`,
      remote: local,
      proxy: archiveApiProxy,
      pull: { query: `${query}|plugin/delete` },
    });
    const deletedUrl = `${historyUrl}/deleted`;
    await createRef(request, replApi, remoteHeaders(), {
      url: deletedUrl,
      origin: '@repl',
      title: 'Deleted',
      tags: ['public', query],
    });
    await pullUntil(request, () => title(request, deletedUrl), 'Deleted');
    await edit(request, replApi, remoteHeaders(), deletedUrl, '@repl', deleteNotice);
    await pullUntil(request, async () => (await findRef(request, archiveApi, headers, deletedUrl, local)) === null, true);

    await userRun(request, mainApi, mainHeaders(), mainOriginUrl);
    await expect.poll(async () => (await findRef(request, mainApi, mainHeaders(), historyUrl, `@archive${id}`))?.title, {
      timeout: 60_000,
    }).toBe('Version 4');
    // Deleted at the source: a regular server stores the archive tombstone as a normal, empty item
    await expect.poll(async () => (await findRef(request, mainApi, mainHeaders(), deletedUrl, `@archive${id}`))?.tags, {
      timeout: 60_000,
    }).toContain('plugin/delete');
    expect((await getRef(request, mainApi, mainHeaders(), deletedUrl, `@archive${id}`)).title).toBeFalsy();
  });
});
