import { request } from './httpClient.js';

/** GET /api/recommendations - { strategy: 'PURCHASE_HISTORY' | 'POPULAR', books } */
export function getRecommendations(limit = 12, options) {
  return request('/recommendations', { ...options, params: { limit } });
}
