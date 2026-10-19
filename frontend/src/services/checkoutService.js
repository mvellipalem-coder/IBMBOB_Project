import { request } from './httpClient.js';

/**
 * POST /api/checkout - turns the server-side cart into an order.
 * Only the address id, payment method and optional points are sent; the server computes every price and total.
 * `idempotencyKey` must be reused when retrying the same attempt so that no second order is created.
 */
export function placeOrder({ addressId, paymentMethod, pointsToRedeem }, idempotencyKey) {
  const body = { addressId, paymentMethod };
  if (pointsToRedeem > 0) body.pointsToRedeem = pointsToRedeem;
  return request('/checkout', {
    method: 'POST',
    body,
    headers: { 'Idempotency-Key': idempotencyKey },
  });
}
