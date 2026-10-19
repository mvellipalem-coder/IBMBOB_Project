// Single place that reads build-time environment variables (see frontend/.env.example).
// Only variables prefixed with VITE_ are exposed to the browser, so never put secrets here.

const DEFAULT_API_BASE_URL = 'http://localhost:8080/api';

function resolveApiBaseUrl() {
  const url = (import.meta.env.VITE_API_BASE_URL || DEFAULT_API_BASE_URL).replace(/\/+$/, '');
  const isLocalHttp = /^http:\/\/(localhost|127\.0\.0\.1)(:\d+)?(\/|$)/.test(url);
  // Security policy: all non-local API traffic must use TLS.
  if (import.meta.env.PROD && !url.startsWith('https://') && !isLocalHttp) {
    throw new Error('VITE_API_BASE_URL must use HTTPS in production builds.');
  }
  return url;
}

export const API_BASE_URL = resolveApiBaseUrl();
export const REQUEST_TIMEOUT_MS = Number(import.meta.env.VITE_REQUEST_TIMEOUT_MS) || 15000;
export const CURRENCY = import.meta.env.VITE_CURRENCY || 'INR';
export const LOCALE = import.meta.env.VITE_LOCALE || 'en-IN';
