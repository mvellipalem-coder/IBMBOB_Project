// Sample API payloads that follow backend/src/main/resources/openapi.yaml field for field.

export const user = {
  id: 7,
  fullName: 'Asha Rao',
  email: 'asha.rao@example.com',
  phone: null,
  role: 'CUSTOMER',
  createdAt: '2026-10-01T08:15:00Z',
};

export const authResponse = {
  accessToken: 'test.jwt.token',
  tokenType: 'Bearer',
  expiresIn: 1800,
  user,
};

export const book = {
  id: 9,
  title: 'Clean Code',
  authors: ['Robert C. Martin'],
  isbn: '9780000000097',
  price: 899.0,
  imageUrl: null,
  ratingAvg: 4.5,
  ratingCount: 12,
  inStock: true,
  categoryId: 4,
  categoryName: 'Programming',
  publisherId: 6,
  publisherName: 'Pearson',
  publishedYear: 2008,
};

export const outOfStockBook = { ...book, id: 10, title: 'Effective Java', inStock: false };

export const cartItem = { itemId: 31, book, quantity: 2, lineTotal: 1798.0, maxQuantity: 10 };
export const cart = { items: [cartItem], totalQuantity: 2, subtotal: 1798.0 };
export const emptyCart = { items: [], totalQuantity: 0, subtotal: 0 };

export const address = {
  id: 3,
  recipientName: 'Asha Rao',
  phone: '+91 98765 43210',
  line1: '12 MG Road',
  line2: 'Flat 4B',
  city: 'Pune',
  state: 'Maharashtra',
  postalCode: '411001',
  countryCode: 'IN',
  isDefault: true,
};

export const shippingAddress = { ...address };
delete shippingAddress.id;
delete shippingAddress.isDefault;

export function makeOrder(overrides = {}) {
  const { payment: paymentOverrides, ...rest } = overrides;
  return {
    id: 15,
    orderNumber: 'EB-20261015-0015',
    status: 'PLACED',
    createdAt: new Date().toISOString(),
    expectedDeliveryDate: '2026-10-20',
    deliveredAt: null,
    cancelledAt: null,
    cancelReason: null,
    shippingAddress,
    items: [
      { bookId: 9, title: 'Clean Code', isbn: '9780000000097', unitPrice: 899.0, quantity: 2, lineTotal: 1798.0 },
    ],
    subtotal: 1798.0,
    pointsRedeemed: 100,
    pointsDiscount: 100.0,
    totalAmount: 1698.0,
    pointsEarned: 169,
    payment: {
      orderId: 15,
      method: 'CARD',
      status: 'PENDING',
      amount: 1698.0,
      transactionRef: null,
      failureReason: null,
      paidAt: null,
      refundedAt: null,
      createdAt: new Date().toISOString(),
      ...paymentOverrides,
    },
    statusHistory: [{ status: 'PLACED', note: null, changedAt: new Date().toISOString() }],
    cancellation: {
      cancellable: true,
      cancellableUntil: new Date(Date.now() + 48 * 3600 * 1000).toISOString(),
      reason: null,
    },
    ...rest,
  };
}

export const giftPoints = { balance: 250, pointValue: 1.0, maxRedeemPercentOfSubtotal: 50, earnAmountPerPoint: 10.0 };

export function page(content, overrides = {}) {
  return { page: 0, size: 12, totalElements: content.length, totalPages: 1, first: true, last: true, content, ...overrides };
}
