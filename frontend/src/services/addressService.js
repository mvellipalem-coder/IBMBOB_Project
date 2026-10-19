import { request } from './httpClient.js';

/** GET /api/addresses */
export function listAddresses(options) {
  return request('/addresses', options);
}

/** POST /api/addresses */
export function createAddress(address) {
  return request('/addresses', { method: 'POST', body: address });
}

/** PUT /api/addresses/{id} */
export function updateAddress(id, address) {
  return request(`/addresses/${encodeURIComponent(id)}`, { method: 'PUT', body: address });
}

/** DELETE /api/addresses/{id} */
export function deleteAddress(id) {
  return request(`/addresses/${encodeURIComponent(id)}`, { method: 'DELETE' });
}
