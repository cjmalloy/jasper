import { expect, test } from '@playwright/test';
import { createRef, deleteRef, findRef, getRef, mainApi, mainHeaders, ok, runId, updateRef } from '../setup';

test.describe.serial('Ref', () => {
  const id = runId();
  const url = `https://e2e.jasper/ref/${id}`;
  const uiUrl = `https://e2e.jasper/ref/ui/${id}`;
  const headers = mainHeaders();

  test('creates a ref', async ({ request }) => {
    const ref = await createRef(request, mainApi, headers, {
      url,
      title: 'Ref Test',
      comment: 'Created by e2e',
      tags: ['public', 'e2e', '+user/debug'],
    });
    expect(ref.title).toBe('Ref Test');
    expect(ref.tags).toEqual(expect.arrayContaining(['public', 'e2e', '+user/debug']));
  });

  test('rejects duplicate refs', async ({ request }) => {
    const resp = await request.post(mainApi + '/api/v1/ref', { headers, data: { url, title: 'Duplicate' } });
    expect(resp.status()).toBe(409);
  });

  test('updates a ref', async ({ request }) => {
    const ref = await getRef(request, mainApi, headers, url);
    ref.title = 'Ref Test Updated';
    await ok(updateRef(request, mainApi, headers, ref));
    expect((await getRef(request, mainApi, headers, url)).title).toBe('Ref Test Updated');
  });

  test('rejects stale updates', async ({ request }) => {
    const ref = await getRef(request, mainApi, headers, url);
    ref.title = 'First';
    await ok(updateRef(request, mainApi, headers, ref));
    ref.title = 'Stale';
    const resp = await updateRef(request, mainApi, headers, ref);
    expect(resp.status()).toBe(409);
  });

  test('patches a ref', async ({ request }) => {
    const ref = await getRef(request, mainApi, headers, url);
    await ok(request.patch(mainApi + '/api/v1/ref', {
      headers: { ...headers, 'Content-Type': 'application/json-patch+json' },
      params: { url, origin: '', cursor: ref.modified! },
      data: JSON.stringify([{ op: 'replace', path: '/title', value: 'Patched' }]),
    }));
    expect((await getRef(request, mainApi, headers, url)).title).toBe('Patched');
  });

  test('anonymous users cannot edit', async ({ request }) => {
    const ref = await getRef(request, mainApi, headers, url);
    ref.title = 'Anonymous';
    const resp = await updateRef(request, mainApi, { 'Cookie': 'XSRF-TOKEN=e2e', 'X-XSRF-TOKEN': 'e2e' }, ref);
    expect(resp.status()).toBe(403);
  });

  test('deletes a ref', async ({ request }) => {
    await deleteRef(request, mainApi, headers, url);
    expect(await findRef(request, mainApi, headers, url)).toBeNull();
  });

  test('edits a ref in the client', async ({ page, request }) => {
    await createRef(request, mainApi, headers, {
      url: uiUrl,
      title: 'UI Edit',
      tags: ['public', '+user/debug'],
    });
    await page.goto(`/ref/e/${encodeURIComponent(uiUrl)}?debug=ADMIN`, { waitUntil: 'networkidle' });
    await page.locator('.full-page.ref .actions .fake-link', { hasText: 'edit' }).first().click();
    await page.locator('.full-page.ref form [name=title]').fill('UI Edit Saved');
    const savePromise = page.waitForResponse(resp => resp.url().includes('/api/v1/ref') && resp.request().method() === 'PUT');
    await page.locator('.full-page.ref form.form button', { hasText: 'save' }).click();
    expect((await savePromise).status()).toBe(200);
    await expect(page.locator('.full-page.ref .link a')).toHaveText('UI Edit Saved');
    expect((await getRef(request, mainApi, headers, uiUrl)).title).toBe('UI Edit Saved');
  });
});
