import { request } from './httpClient.js';

/**
 * POST /api/payments/{orderId}/process - asks the simulated gateway to settle a CARD or UPI order.
 * No card or UPI data is sent: the server decides the outcome. Check `status` in the response.
 */
export function processPayment(orderId, idempotencyKey) {
  return request(`/payments/${encodeURIComponent(orderId)}/process`, {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey },
  });
}

/** GET /api/payments/{orderId} */
export function getPayment(orderId, options) {
  return request(`/payments/${encodeURIComponent(orderId)}`, options);
}
