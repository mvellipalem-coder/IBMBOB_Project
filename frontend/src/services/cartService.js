import { request } from './httpClient.js';

/** GET /api/cart - { items, totalQuantity, subtotal } */
export function getCart(options) {
  return request('/cart', options);
}

/** POST /api/cart/items - returns the updated cart. */
export function addCartItem(bookId, quantity = 1) {
  return request('/cart/items', { method: 'POST', body: { bookId, quantity } });
}

/** PUT /api/cart/items/{itemId} - sets the quantity of one cart line. */
export function updateCartItem(itemId, quantity) {
  return request(`/cart/items/${encodeURIComponent(itemId)}`, { method: 'PUT', body: { quantity } });
}

/** DELETE /api/cart/items/{itemId} - returns the updated cart. */
export function removeCartItem(itemId) {
  return request(`/cart/items/${encodeURIComponent(itemId)}`, { method: 'DELETE' });
}
