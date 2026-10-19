import { vi } from 'vitest';
import { user } from './fixtures.js';

/**
 * Replaces global fetch with a fake backend for tests. The real httpClient and services still run,
 * so tests check the actual request URLs, headers and bodies the app sends.
 *
 * `routes` maps "METHOD /api/path" to either a response or a function (request) => response, where a
 * response is { status?, body?, networkError? }. Unmatched requests fail the test loudly.
 * Returns the fetch mock; `calls()` lists what was sent.
 */
export function mockBackend(extraRoutes) {
  // GET /api/users/me is called on every page load of a signed-in session, so it is answered by default.
  const routes = { 'GET /api/users/me': { body: user }, ...extraRoutes };
  const log = [];
  const fetchMock = vi.fn(async (url, options = {}) => {
    const parsed = new URL(url);
    const method = (options.method ?? 'GET').toUpperCase();
    const key = `${method} ${parsed.pathname}`;
    const request = {
      method,
      path: parsed.pathname,
      query: Object.fromEntries(parsed.searchParams),
      headers: options.headers ?? {},
      body: options.body ? JSON.parse(options.body) : undefined,
    };
    log.push(request);
    const route = routes[key];
    if (!route) {
      throw new Error(`Unexpected request in test: ${key}`);
    }
    const reply = typeof route === 'function' ? route(request) : route;
    if (reply.networkError) {
      throw new TypeError('Failed to fetch');
    }
    const status = reply.status ?? 200;
    const text = reply.body === undefined ? '' : JSON.stringify(reply.body);
    return { ok: status >= 200 && status < 300, status, text: async () => text };
  });
  fetchMock.calls = () => log;
  fetchMock.callsTo = (key) => log.filter((entry) => `${entry.method} ${entry.path}` === key);
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

export function apiError(status, code, message, extra = {}) {
  return {
    status,
    body: { timestamp: '2026-10-15T10:35:00Z', status, error: 'Error', code, message, path: '/api/test', ...extra },
  };
}
