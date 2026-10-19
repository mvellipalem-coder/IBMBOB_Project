import { request } from './httpClient.js';

/** GET /api/books - browse with filters, sorting and paging. */
export function listBooks(params, options) {
  return request('/books', { ...options, params });
}

/** GET /api/books/search - same filters plus the `q` keyword (2 to 100 characters). */
export function searchBooks(params, options) {
  return request('/books/search', { ...options, params });
}

/** GET /api/books/{id} */
export function getBook(id, options) {
  return request(`/books/${encodeURIComponent(id)}`, options);
}

/** GET /api/books/{id}/related */
export function getRelatedBooks(id, limit = 6, options) {
  return request(`/books/${encodeURIComponent(id)}/related`, { ...options, params: { limit } });
}

/** GET /api/categories */
export function getCategories(options) {
  return request('/categories', options);
}

/** GET /api/publishers */
export function getPublishers(options) {
  return request('/publishers', options);
}
