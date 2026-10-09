import { expect, test } from '@playwright/test';
import { createOrigin, installOriginPlugins, mainApi, mainHeaders, ok, remoteHeaders, replApi, replApiProxy, runId, userRun } from '../setup';

test('scratch cache pull', async ({ request }) => {
  test.setTimeout(300_000);
  const id = runId();
  await installOriginPlugins(request);
  const cachePlugin = { tag: '_plugin/cache', name: 'Cache', schema: { optionalProperties: { id: { type: 'string' }, mimeType: { type: 'string' }, contentLength: { type: 'uint32' }, ban: { type: 'boolean' }, noStore: { type: 'boolean' }, thumbnail: { type: 'boolean' } } } };
  for (const [api, h, origin] of [[replApi, remoteHeaders(), '@repl'], [mainApi, mainHeaders(), '']] as const) {
    const r = await request.post(api + '/api/v1/plugin', { headers: h, data: { ...cachePlugin, origin } });
    console.log('plugin', r.status());
  }
  const urls: string[] = [];
  for (let i = 0; i < 30; i++) {
    const size = i % 5 === 0 ? 3_000_000 : 50_000;
    const resp = await ok(request.post(replApi + '/api/v1/proxy', { headers: { ...remoteHeaders(), 'Content-Type': 'application/octet-stream' }, params: { origin: '@repl', title: `file ${i}` }, data: Buffer.alloc(size, i) }), 'upload');
    const ref = await resp.json();
    urls.push(ref.url);
    console.log('uploaded', ref.url, JSON.stringify(ref.plugins?.['_plugin/cache']), ref.tags);
  }
  const originUrl = `https://e2e.jasper/origin/scratch/${id}`;
  await createOrigin(request, { url: originUrl, title: 'scratch', local: '@repl.scratch', remote: '@repl', proxy: replApiProxy, pull: { batchSize: 7 } });
  await userRun(request, mainApi, mainHeaders(), originUrl);
  await new Promise(r => setTimeout(r, 30000));
  const c = await (await request.get(mainApi + '/api/v1/ref/count', { headers: mainHeaders(), params: { query: '@repl.scratch' } })).text();
  console.log('count', c);
  const o = await (await request.get(mainApi + '/api/v1/ref', { headers: mainHeaders(), params: { url: originUrl, origin: '' } })).json();
  console.log('origin', JSON.stringify(o.tags), JSON.stringify(o.metadata));
});

test('scratch cache push', async ({ request }) => {
  test.setTimeout(300_000);
  const id = runId();
  await installOriginPlugins(request);
  for (let i = 0; i < 20; i++) {
    const size = i % 5 === 0 ? 3_000_000 : 50_000;
    const resp = await ok(request.post(mainApi + '/api/v1/proxy', { headers: { ...mainHeaders(), 'Content-Type': 'application/octet-stream' }, params: { origin: '', title: `pfile ${i}` }, data: Buffer.alloc(size, i) }), 'upload');
    const ref = await resp.json();
    console.log('uploaded', ref.url, ref.tags);
  }
  const originUrl = `https://e2e.jasper/origin/scratchpush/${id}`;
  await createOrigin(request, { url: originUrl, title: 'scratch push', remote: '@repl', proxy: replApiProxy, push: { cache: true, batchSize: 7, query: 'plugin/file' } });
  await userRun(request, mainApi, mainHeaders(), originUrl);
  await new Promise(r => setTimeout(r, 30000));
  const c = await (await request.get(replApi + '/api/v1/ref/count', { headers: remoteHeaders(), params: { query: 'plugin/file@repl' } })).text();
  console.log('count', c);
  const o = await (await request.get(mainApi + '/api/v1/ref', { headers: mainHeaders(), params: { url: originUrl, origin: '' } })).json();
  console.log('origin', JSON.stringify(o.tags), JSON.stringify(o.metadata));
});

test('scratch fetch pulled', async ({ request }) => {
  const list = await (await request.get(mainApi + '/api/v1/ref/page', { headers: mainHeaders(), params: { query: 'plugin/file@repl.scratch', size: 50 } })).json();
  for (const r of list.content) {
    const resp = await request.get(mainApi + '/api/v1/proxy', { headers: mainHeaders(), params: { url: r.url, origin: r.origin } });
    console.log('fetch', r.url, resp.status(), (await resp.body()).length, r.tags);
  }
});
