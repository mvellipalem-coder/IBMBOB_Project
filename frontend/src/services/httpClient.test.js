import { describe, expect, it, vi } from 'vitest';
import { ApiError, request, setUnauthorizedHandler } from './httpClient.js';
import { saveSession } from './authStorage.js';
import { apiError, mockBackend } from '../test/mockBackend.js';

describe('httpClient.request', () => {
  it('sends JSON to the configured base URL and returns the parsed body', async () => {
    const fetchMock = mockBackend({ 'POST /api/auth/login': { body: { ok: true } } });
    const data = await request('/auth/login', { method: 'POST', body: { email: 'a@b.co' }, auth: false });
    expect(data).toEqual({ ok: true });
    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toBe('http://localhost:8080/api/auth/login');
    expect(options.headers['Content-Type']).toBe('application/json');
  });

  it('adds the Bearer token only when a session exists', async () => {
    const fetchMock = mockBackend({ 'GET /api/cart': { body: {} } });
    await request('/cart');
    expect(fetchMock.calls()[0].headers.Authorization).toBeUndefined();

    saveSession({ accessToken: 'abc.def', user: {}, expiresAt: Date.now() + 60000 });
    await request('/cart');
    expect(fetchMock.calls()[1].headers.Authorization).toBe('Bearer abc.def');
  });

  it('skips the token for public calls even when logged in', async () => {
    saveSession({ accessToken: 'abc.def', user: {}, expiresAt: Date.now() + 60000 });
    const fetchMock = mockBackend({ 'POST /api/auth/login': { body: {} } });
    await request('/auth/login', { method: 'POST', body: {}, auth: false });
    expect(fetchMock.calls()[0].headers.Authorization).toBeUndefined();
  });

  it('drops empty query values and encodes the rest', async () => {
    const fetchMock = mockBackend({ 'GET /api/books': { body: {} } });
    await request('/books', { params: { q: 'clean code', categoryId: '', page: 0, inStock: undefined } });
    expect(fetchMock.calls()[0].query).toEqual({ q: 'clean code', page: '0' });
  });

  it('turns an error response into an ApiError with code, status and field errors', async () => {
    mockBackend({
      'POST /api/auth/register': apiError(400, 'VALIDATION_FAILED', 'Validation failed', {
        fieldErrors: [{ field: 'email', message: 'must be a valid email' }],
      }),
    });
    const error = await request('/auth/register', { method: 'POST', body: {}, auth: false }).catch((e) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(400);
    expect(error.code).toBe('VALIDATION_FAILED');
    expect(error.fieldErrors).toEqual([{ field: 'email', message: 'must be a valid email' }]);
  });

  it('uses a friendly message when the error body is not JSON', async () => {
    vi.stubGlobal('fetch', async () => ({ ok: false, status: 502, text: async () => '<html>bad gateway</html>' }));
    const error = await request('/books').catch((e) => e);
    expect(error.message).toMatch(/server ran into a problem/i);
    expect(error.message).not.toMatch(/html/i);
  });

  it('reports a network failure with a clear message', async () => {
    mockBackend({ 'GET /api/books': { networkError: true } });
    const error = await request('/books').catch((e) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(error.code).toBe('NETWORK_ERROR');
    expect(error.message).toMatch(/unable to reach the server/i);
  });

  it('calls the unauthorized handler when a request that carried a token gets 401', async () => {
    const handler = vi.fn();
    setUnauthorizedHandler(handler);
    saveSession({ accessToken: 'old.token', user: {}, expiresAt: Date.now() + 60000 });
    mockBackend({ 'GET /api/cart': apiError(401, 'UNAUTHORIZED', 'Authentication is required.') });
    await request('/cart').catch(() => {});
    expect(handler).toHaveBeenCalledTimes(1);
    setUnauthorizedHandler(null);
  });

  it('does not treat a failed login (401, no token sent) as an expired session', async () => {
    const handler = vi.fn();
    setUnauthorizedHandler(handler);
    mockBackend({ 'POST /api/auth/login': apiError(401, 'INVALID_CREDENTIALS', 'Invalid email or password.') });
    const error = await request('/auth/login', { method: 'POST', body: {}, auth: false }).catch((e) => e);
    expect(error.code).toBe('INVALID_CREDENTIALS');
    expect(handler).not.toHaveBeenCalled();
    setUnauthorizedHandler(null);
  });
});
