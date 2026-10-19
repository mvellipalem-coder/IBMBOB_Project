import { request } from './httpClient.js';

/** POST /api/auth/register - creates a CUSTOMER account. It does not log the user in. */
export function register({ fullName, email, password, phone }) {
  const body = { fullName, email, password };
  if (phone) body.phone = phone;
  return request('/auth/register', { method: 'POST', body, auth: false });
}

/** POST /api/auth/login - returns { accessToken, tokenType, expiresIn, user }. */
export function login({ email, password }) {
  return request('/auth/login', { method: 'POST', body: { email, password }, auth: false });
}

/** GET /api/users/me */
export function getProfile(options) {
  return request('/users/me', options);
}
