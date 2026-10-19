import { API_BASE_URL, REQUEST_TIMEOUT_MS } from '../config/env.js';
import { getAccessToken } from './authStorage.js';

/** Error thrown for every failed API call. Switch on `code`, not on `message`. */
export class ApiError extends Error {
  constructor({
    status = 0,
    code = 'UNKNOWN_ERROR',
    message = 'Something went wrong. Please try again.',
    fieldErrors = [],
  } = {}) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
  }
}

let unauthorizedHandler = null;

/** Registers a callback that runs when the server rejects a token we sent (expired or invalid session). */
export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = handler;
}

function fallbackMessage(status) {
  if (status === 401) return 'Please log in to continue.';
  if (status === 403) return 'You do not have permission to do that.';
  if (status === 404) return 'We could not find what you were looking for.';
  if (status === 429) return 'Too many requests. Please wait a moment and try again.';
  if (status >= 500) return 'The server ran into a problem. Please try again later.';
  return 'The request could not be completed.';
}

function buildQueryString(params) {
  const query = new URLSearchParams();
  Object.entries(params ?? {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      query.append(key, String(value));
    }
  });
  const text = query.toString();
  return text ? `?${text}` : '';
}

/**
 * Sends a JSON request to the backend.
 * - Adds the Bearer token when a session exists (unless `auth: false`).
 * - Applies a timeout and supports cancellation through `signal`.
 * - Returns the parsed JSON body (or null when there is none).
 */
export async function request(path, { method = 'GET', body, params, headers, auth = true, signal } = {}) {
  const token = auth ? getAccessToken() : null;
  const controller = new AbortController();
  let timedOut = false;
  const timer = setTimeout(() => {
    timedOut = true;
    controller.abort();
  }, REQUEST_TIMEOUT_MS);
  const abortFromCaller = () => controller.abort();
  if (signal) {
    if (signal.aborted) controller.abort();
    else signal.addEventListener('abort', abortFromCaller, { once: true });
  }

  const requestHeaders = { Accept: 'application/json', ...headers };
  if (body !== undefined) requestHeaders['Content-Type'] = 'application/json';
  if (token) requestHeaders.Authorization = `Bearer ${token}`;

  try {
    let response;
    try {
      response = await fetch(`${API_BASE_URL}${path}${buildQueryString(params)}`, {
        method,
        headers: requestHeaders,
        body: body !== undefined ? JSON.stringify(body) : undefined,
        signal: controller.signal,
      });
    } catch (error) {
      if (timedOut) {
        throw new ApiError({
          code: 'TIMEOUT',
          message: 'The server took too long to respond. Please try again.',
        });
      }
      if (error?.name === 'AbortError') throw error; // cancelled by the caller
      throw new ApiError({
        code: 'NETWORK_ERROR',
        message: 'Unable to reach the server. Check your connection and try again.',
      });
    }

    const text = await response.text();
    let data = null;
    if (text) {
      try {
        data = JSON.parse(text);
      } catch {
        data = null;
      }
    }

    if (!response.ok) {
      if (response.status === 401 && token && unauthorizedHandler) {
        unauthorizedHandler();
      }
      throw new ApiError({
        status: response.status,
        code: data?.code ?? undefined,
        message: data?.message || fallbackMessage(response.status),
        fieldErrors: Array.isArray(data?.fieldErrors) ? data.fieldErrors : [],
      });
    }
    return data;
  } finally {
    clearTimeout(timer);
    signal?.removeEventListener('abort', abortFromCaller);
  }
}
