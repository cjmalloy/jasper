import { type APIRequestContext, type APIResponse, expect } from '@playwright/test';
import { createHmac } from 'node:crypto';

export const mainApi = process.env.MAIN_API || 'http://localhost:8081';
export const replApi = process.env.REPL_API || 'http://localhost:8083';
export const tunnelApi = process.env.TUNNEL_API || 'http://localhost:8085';

// Addresses as seen from inside the docker compose network
export const replApiProxy = 'http://repl-web';
export const tunnelApiProxy = 'http://tunnel-web';
export const sshHost = 'ssh';

/**
 * Debug secret shared with jasper-ui ?debug=ADMIN tokens (DO NOT USE IN PRODUCTION).
 */
const debugSecret = Buffer.from(
  'MjY0ZWY2ZTZhYmJhMTkyMmE5MTAxMTg3Zjc2ZDlmZWUwYjk0MDgzODA0MDJiOTgyNTk4MmNjYmQ4Yjg3MmVhYjk0MmE0OGFmNzE2YTQ5ZjliMTEyN2NlMWQ4MjA5OTczYjU2NzAxYTc4YThkMzYxNzdmOTk5MTIxODZhMTkwMDM=',
  'base64',
);
const encodeJwt = (value: string | Buffer) => Buffer.from(value).toString('base64url');

/**
 * Sign a debug JWT for the main server, the same way jasper-ui does for ?debug=ADMIN&tag=user.
 */
export function debugJwt(user = 'debug', role = 'ADMIN') {
  const body = [
    encodeJwt(JSON.stringify({ alg: 'HS256', typ: 'JWT' })),
    encodeJwt(JSON.stringify({ verified_email: true, sub: user, auth: 'ROLE_' + role })),
  ].join('.');
  return `${body}.${createHmac('sha256', debugSecret).update(body).digest('base64url')}`;
}

const csrf = {
  'Cookie': 'XSRF-TOKEN=e2e',
  'X-XSRF-TOKEN': 'e2e',
};

/**
 * Headers for the main server, authenticated as +user/{user}.
 */
export function mainHeaders(user = 'debug', role = 'ADMIN'): Record<string, string> {
  return {
    ...csrf,
    'Authorization': 'Bearer ' + debugJwt(user, role),
  };
}

/**
 * Headers for the remote servers, which use JASPER_DEFAULT_ROLE=ROLE_ADMIN.
 */
export function remoteHeaders(): Record<string, string> {
  return { ...csrf };
}

export function runId() {
  return Date.now().toString(36) + Math.random().toString(36).substring(2, 6);
}

export async function ok(response: APIResponse | Promise<APIResponse>, message = '') {
  const resp = await response;
  expect(resp.ok(), `${message} ${resp.url()} -> ${resp.status()} ${await resp.text()}`).toBe(true);
  return resp;
}

export interface Ref {
  url: string;
  origin?: string;
  title?: string;
  comment?: string;
  tags?: string[];
  sources?: string[];
  plugins?: Record<string, any>;
  modified?: string;
  [key: string]: any;
}

export async function createRef(request: APIRequestContext, api: string, headers: Record<string, string>, ref: Ref) {
  await ok(request.post(api + '/api/v1/ref', { headers, data: ref }), 'Create Ref');
  return getRef(request, api, headers, ref.url, ref.origin);
}

export async function getRef(request: APIRequestContext, api: string, headers: Record<string, string>, url: string, origin = '') {
  const resp = await ok(request.get(api + '/api/v1/ref', { headers, params: { url, origin } }), 'Get Ref');
  return await resp.json() as Ref;
}

export async function findRef(request: APIRequestContext, api: string, headers: Record<string, string>, url: string, origin = '') {
  const resp = await request.get(api + '/api/v1/ref', { headers, params: { url, origin } });
  if (resp.status() === 404) return null;
  await ok(resp, 'Find Ref');
  return await resp.json() as Ref;
}

/**
 * Send a full Ref update the same way jasper-ui does when saving the edit form.
 */
export function updateRef(request: APIRequestContext, api: string, headers: Record<string, string>, ref: Ref) {
  return request.put(api + '/api/v1/ref', {
    headers,
    data: {
      url: ref.url,
      origin: ref.origin || '',
      title: ref.title,
      comment: ref.comment,
      tags: ref.tags,
      sources: ref.sources,
      alternateUrls: ref.alternateUrls,
      plugins: ref.plugins,
      published: ref.published,
      modified: ref.modified,
    },
  });
}

export async function deleteRef(request: APIRequestContext, api: string, headers: Record<string, string>, url: string, origin = '') {
  const resp = await request.delete(api + '/api/v1/ref', { headers, params: { url, origin } });
  expect([204, 404]).toContain(resp.status());
}

export async function page(request: APIRequestContext, api: string, headers: Record<string, string>, params: Record<string, string | number>) {
  const resp = await ok(request.get(api + '/api/v1/ref/page', { headers, params }), 'Ref Page');
  return (await resp.json()).content as Ref[];
}

/**
 * Responses to a Ref (Refs with the url as a source) in the given origin.
 */
export async function responses(request: APIRequestContext, api: string, headers: Record<string, string>, url: string, query: string) {
  return page(request, api, headers, { responses: url, query, size: 100 });
}

export async function addTag(request: APIRequestContext, api: string, headers: Record<string, string>, tag: string, url: string, origin = '') {
  await ok(request.post(api + '/api/v1/tags', { headers, params: { tag, url, origin } }), 'Add Tag');
}

export async function removeTag(request: APIRequestContext, api: string, headers: Record<string, string>, tag: string, url: string, origin = '') {
  await ok(request.delete(api + '/api/v1/tags', { headers, params: { tag, url, origin } }), 'Remove Tag');
}

/**
 * Disable a remote origin, ignoring missing Refs.
 */
export async function disable(request: APIRequestContext, url: string) {
  await request.delete(mainApi + '/api/v1/tags', { headers: mainHeaders(), params: { tag: '+plugin/cron', url, origin: '' } });
}

/**
 * Manually run the cron plugins on a Ref (push or pull for origins), like the jasper-ui push / pull actions.
 */
export async function userRun(request: APIRequestContext, api: string, headers: Record<string, string>, url: string) {
  // Clear any response left over from a failed run
  await ok(request.delete(api + '/api/v1/tags/response', { headers, params: { tag: '+plugin/user/run', url } }), 'Clear Run');
  await ok(request.post(api + '/api/v1/tags/response', { headers, params: { tag: '+plugin/user/run', url } }), 'Run');
}

const originPlugins = [{
  tag: '+plugin/origin',
  name: '🏛️ Remote Origin',
  schema: {
    optionalProperties: {
      local: { type: 'string' },
      remote: { type: 'string' },
      proxy: { type: 'string' },
    },
  },
}, {
  tag: '+plugin/origin/pull',
  name: '📥️ Pull',
  schema: {
    optionalProperties: {
      cachePrefetch: { type: 'boolean' },
      cacheProxy: { type: 'boolean' },
      cacheProxyPrefetch: { type: 'boolean' },
      websocket: { type: 'boolean' },
      query: { type: 'string' },
      batchSize: { type: 'int32' },
      validatePlugins: { type: 'boolean' },
      stripInvalidPlugins: { type: 'boolean' },
      validateTemplates: { type: 'boolean' },
      stripInvalidTemplates: { type: 'boolean' },
      originFromTag: { type: 'string' },
      addTags: { elements: { type: 'string' } },
      removeTags: { elements: { type: 'string' } },
    },
  },
}, {
  tag: '+plugin/origin/push',
  name: '📤️ Push',
  schema: {
    optionalProperties: {
      pushOnChange: { type: 'boolean' },
      cache: { type: 'boolean' },
      query: { type: 'string' },
      batchSize: { type: 'int32' },
    },
  },
}, {
  tag: '+plugin/origin/tunnel',
  name: '🚇️ SSH Tunnel',
  schema: {
    optionalProperties: {
      hostFingerprint: { type: 'string' },
      remoteUser: { type: 'string' },
      sshHost: { type: 'string' },
      sshPort: { type: 'uint32' },
    },
  },
}];

/**
 * Install the origin plugins (same schemas as the jasper-ui origin mod).
 */
export async function installOriginPlugins(request: APIRequestContext, api = mainApi, headers = mainHeaders()) {
  for (const plugin of originPlugins) {
    const resp = await request.post(api + '/api/v1/plugin', { headers, data: plugin });
    expect([201, 409], `Install ${plugin.tag}: ${await resp.text()}`).toContain(resp.status());
  }
}

export interface OriginOptions {
  url: string;
  title: string;
  remote: string;
  local?: string;
  proxy?: string;
  user?: string;
  push?: Record<string, any>;
  pull?: Record<string, any>;
  tunnel?: Record<string, any>;
  enabled?: boolean;
}

/**
 * Create a remote origin Ref on the main server signed by +user/{user}.
 */
export async function createOrigin(request: APIRequestContext, opts: OriginOptions) {
  const user = opts.user || 'debug';
  const tags = ['+plugin/origin', '+user/' + user];
  const plugins: Record<string, any> = {
    '+plugin/origin': { local: opts.local || '', remote: opts.remote, proxy: opts.proxy },
  };
  if (opts.push) {
    tags.push('+plugin/origin/push');
    plugins['+plugin/origin/push'] = opts.push;
  }
  if (opts.pull) {
    tags.push('+plugin/origin/pull');
    plugins['+plugin/origin/pull'] = opts.pull;
  }
  if (opts.tunnel) {
    tags.push('+plugin/origin/tunnel');
    plugins['+plugin/origin/tunnel'] = opts.tunnel;
  }
  if (opts.enabled) tags.push('+plugin/cron');
  return createRef(request, mainApi, mainHeaders(user), { url: opts.url, title: opts.title, tags, plugins });
}
