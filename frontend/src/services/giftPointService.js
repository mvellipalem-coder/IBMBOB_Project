import { request } from './httpClient.js';

/** GET /api/gift-points/balance - { balance, pointValue, maxRedeemPercentOfSubtotal, earnAmountPerPoint } */
export function getGiftPointBalance(options) {
  return request('/gift-points/balance', options);
}
