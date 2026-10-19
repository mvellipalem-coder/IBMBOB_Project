import { request } from './httpClient.js';

/** GET /api/orders - the signed-in user's orders, newest first. */
export function listOrders(params, options) {
  return request('/orders', { ...options, params });
}

/** GET /api/orders/{id} */
export function getOrder(id, options) {
  return request(`/orders/${encodeURIComponent(id)}`, options);
}

/** POST /api/orders/{id}/cancel - the reason is optional. */
export function cancelOrder(id, reason) {
  const body = reason ? { reason } : undefined;
  return request(`/orders/${encodeURIComponent(id)}/cancel`, { method: 'POST', body });
}

/** POST /api/orders/{id}/buy-again - returns { cart, issues }. */
export function buyAgain(id) {
  return request(`/orders/${encodeURIComponent(id)}/buy-again`, { method: 'POST' });
}
