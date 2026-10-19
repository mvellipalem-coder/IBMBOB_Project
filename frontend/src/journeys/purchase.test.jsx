import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { address, book, cart, emptyCart, giftPoints, makeOrder } from '../test/fixtures.js';
import { apiError, mockBackend } from '../test/mockBackend.js';
import { renderApp, signIn } from '../test/renderApp.jsx';

describe('Journey: address management', () => {
  it('lists, adds and deletes addresses through the backend', async () => {
    signIn();
    let addresses = [address];
    const backend = mockBackend({
      'GET /api/cart': { body: emptyCart },
      'GET /api/addresses': () => ({ body: addresses }),
      'POST /api/addresses': (req) => {
        addresses = [...addresses, { ...req.body, id: 4, isDefault: false }];
        return { status: 201, body: addresses.at(-1) };
      },
      'DELETE /api/addresses/3': () => {
        addresses = addresses.filter((a) => a.id !== 3);
        return { status: 204 };
      },
    });
    renderApp('/addresses');
    expect(await screen.findByText(/12 MG Road/)).toBeInTheDocument();
    expect(screen.getByText('Default')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Add new address' }));
    await userEvent.type(screen.getByLabelText(/Recipient name/), 'Ravi Kumar');
    await userEvent.type(screen.getByLabelText(/^Phone/), '9876543210');
    await userEvent.type(screen.getByLabelText(/Address line 1/), '5 Park Street');
    await userEvent.type(screen.getByLabelText(/^City/), 'Kolkata');
    await userEvent.type(screen.getByLabelText(/^State/), 'West Bengal');
    await userEvent.type(screen.getByLabelText(/Postal code/), '700016');
    await userEvent.click(screen.getByRole('button', { name: 'Add address' }));

    expect(await screen.findByText('Address added.')).toBeInTheDocument();
    expect(await screen.findByText(/5 Park Street/)).toBeInTheDocument();
    expect(backend.callsTo('POST /api/addresses')[0].body).toMatchObject({
      recipientName: 'Ravi Kumar',
      postalCode: '700016',
      countryCode: 'IN',
    });

    await userEvent.click(screen.getByRole('button', { name: /Delete address for Asha Rao/ }));
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Delete' }));
    await waitFor(() => expect(screen.queryByText(/12 MG Road/)).not.toBeInTheDocument());
  });

  it('shows an empty state when there are no addresses', async () => {
    signIn();
    mockBackend({ 'GET /api/cart': { body: emptyCart }, 'GET /api/addresses': { body: [] } });
    renderApp('/addresses');
    expect(await screen.findByText('No saved addresses')).toBeInTheDocument();
  });
});

describe('Journey: checkout, payment and confirmation', () => {
  const setupCheckout = (extra = {}) =>
    mockBackend({
      'GET /api/cart': { body: cart },
      'GET /api/addresses': { body: [address] },
      'GET /api/gift-points/balance': { body: giftPoints },
      ...extra,
    });

  it('requires a payment method before placing an order', async () => {
    signIn();
    const backend = setupCheckout();
    renderApp('/checkout');
    await userEvent.click(await screen.findByRole('button', { name: 'Place order' }));
    expect(await screen.findByText('Choose a payment method.')).toBeInTheDocument();
    expect(backend.callsTo('POST /api/checkout')).toHaveLength(0);
  });

  it('places a CARD order with an Idempotency-Key, pays, and only then shows confirmation', async () => {
    signIn();
    let order = makeOrder();
    const backend = setupCheckout({
      'POST /api/checkout': { status: 201, body: order },
      'GET /api/orders/15': () => ({ body: order }),
      'POST /api/payments/15/process': () => {
        order = makeOrder({ payment: { status: 'SUCCESS', transactionRef: 'SIM-8F3A2C91D4B7', paidAt: new Date().toISOString() } });
        return { body: order.payment };
      },
    });
    renderApp('/checkout');

    await userEvent.click(await screen.findByRole('radio', { name: /Credit \/ debit card/ }));
    await userEvent.type(screen.getByLabelText('Points to redeem'), '100');
    await userEvent.click(screen.getByRole('button', { name: 'Place order' }));

    // Payment page: the order exists but is NOT confirmed yet.
    expect(await screen.findByRole('heading', { name: 'Complete your payment' })).toBeInTheDocument();
    expect(screen.queryByText(/order is confirmed/i)).not.toBeInTheDocument();

    const checkout = backend.callsTo('POST /api/checkout')[0];
    expect(checkout.body).toEqual({ addressId: 3, paymentMethod: 'CARD', pointsToRedeem: 100 });
    expect(checkout.headers['Idempotency-Key']).toMatch(/^[A-Za-z0-9_-]{16,64}$/);
    // The browser never sends prices or totals.
    expect(Object.keys(checkout.body)).not.toEqual(expect.arrayContaining(['totalAmount', 'subtotal', 'price']));
    // Totals shown come from the order the server returned.
    expect(screen.getAllByText(/1,698\.00/).length).toBeGreaterThan(0);

    await userEvent.click(screen.getByRole('button', { name: 'Pay now' }));
    expect(await screen.findByRole('heading', { name: 'Thank you, your order is confirmed' })).toBeInTheDocument();
    expect(backend.callsTo('POST /api/payments/15/process')[0].headers['Idempotency-Key']).toMatch(/^[A-Za-z0-9_-]{16,64}$/);
  });

  it('never shows success when the payment is declined, and explains the order was cancelled', async () => {
    signIn();
    const order = makeOrder();
    setupCheckout({
      'POST /api/checkout': { status: 201, body: order },
      'GET /api/orders/15': { body: order },
      'POST /api/payments/15/process': {
        body: { ...order.payment, status: 'FAILED', failureReason: 'Card declined by the simulated gateway.' },
      },
    });
    renderApp('/checkout');
    await userEvent.click(await screen.findByRole('radio', { name: /UPI/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Place order' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Pay now' }));

    expect(await screen.findByText('Your payment was not accepted')).toBeInTheDocument();
    expect(screen.getByText(/Card declined by the simulated gateway/)).toBeInTheDocument();
    expect(screen.queryByText(/confirmed|thank you/i)).not.toBeInTheDocument();
  });

  it('reuses the same Idempotency-Key when a payment attempt is retried after a network failure', async () => {
    signIn();
    const order = makeOrder();
    let attempts = 0;
    const backend = setupCheckout({
      'POST /api/checkout': { status: 201, body: order },
      'GET /api/orders/15': { body: order },
      'POST /api/payments/15/process': () => {
        attempts += 1;
        return attempts === 1 ? { networkError: true } : { body: { ...order.payment, status: 'SUCCESS' } };
      },
    });
    renderApp('/checkout');
    await userEvent.click(await screen.findByRole('radio', { name: /UPI/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Place order' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Pay now' }));
    expect(await screen.findByText(/Unable to reach the server/)).toBeInTheDocument();
    expect(screen.getByText(/will not be charged twice/)).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Pay now' }));
    await waitFor(() => expect(backend.callsTo('POST /api/payments/15/process')).toHaveLength(2));
    const [first, second] = backend.callsTo('POST /api/payments/15/process');
    expect(second.headers['Idempotency-Key']).toBe(first.headers['Idempotency-Key']);
  });

  it('places a cash-on-delivery order without a payment step', async () => {
    signIn();
    const order = makeOrder({ payment: { method: 'COD', status: 'PENDING' } });
    const backend = setupCheckout({
      'POST /api/checkout': { status: 201, body: order },
      'GET /api/orders/15': { body: order },
    });
    renderApp('/checkout');
    await userEvent.click(await screen.findByRole('radio', { name: /Cash on delivery/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Place order' }));

    expect(await screen.findByRole('heading', { name: 'Thank you, your order is placed' })).toBeInTheDocument();
    expect(backend.callsTo('POST /api/checkout')[0].body).toEqual({ addressId: 3, paymentMethod: 'COD' });
    expect(backend.callsTo('POST /api/payments/15/process')).toHaveLength(0);
  });

  it('does not claim success when checkout is rejected, and shows the server reason', async () => {
    signIn();
    setupCheckout({
      'POST /api/checkout': apiError(409, 'INSUFFICIENT_STOCK', 'Only 1 copy of "Clean Code" is available.'),
    });
    renderApp('/checkout');
    await userEvent.click(await screen.findByRole('radio', { name: /Cash on delivery/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Place order' }));
    expect(await screen.findByText('Only 1 copy of "Clean Code" is available.')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Checkout' })).toBeInTheDocument();
    expect(screen.queryByText(/thank you/i)).not.toBeInTheDocument();
  });

  it('shows the backend message next to the points field when redemption is invalid', async () => {
    signIn();
    setupCheckout({
      'POST /api/checkout': apiError(422, 'INVALID_POINTS_REDEMPTION', 'You can pay at most 50% of the subtotal with points.'),
    });
    renderApp('/checkout');
    await userEvent.click(await screen.findByRole('radio', { name: /Cash on delivery/ }));
    await userEvent.type(screen.getByLabelText('Points to redeem'), '999');
    await userEvent.click(screen.getByRole('button', { name: 'Place order' }));
    expect(await screen.findByText('You can pay at most 50% of the subtotal with points.')).toBeInTheDocument();
    expect(screen.getByLabelText('Points to redeem')).toHaveAttribute('aria-invalid', 'true');
  });

  it('tells the user when the cart is empty instead of showing a checkout form', async () => {
    signIn();
    mockBackend({ 'GET /api/cart': { body: emptyCart } });
    renderApp('/checkout');
    expect(await screen.findByText('Your cart is empty')).toBeInTheDocument();
  });

  it('shows a pending-payment warning instead of success when an unpaid card order is opened', async () => {
    signIn();
    mockBackend({
      'GET /api/cart': { body: emptyCart },
      'GET /api/orders/15': { body: makeOrder() },
    });
    renderApp('/orders/15/confirmation');
    expect(await screen.findByRole('heading', { name: 'Payment still needed' })).toBeInTheDocument();
    expect(screen.queryByText(/order is confirmed/i)).not.toBeInTheDocument();
  });
});

describe('Journey: order history, details, cancel and buy again', () => {
  const summary = {
    id: 15,
    orderNumber: 'EB-20261015-0015',
    status: 'PLACED',
    createdAt: '2026-10-15T10:30:00Z',
    expectedDeliveryDate: '2026-10-20',
    totalAmount: 1698.0,
    itemCount: 2,
    paymentMethod: 'CARD',
    paymentStatus: 'SUCCESS',
  };
  const ordersPage = (content) => ({ page: 0, size: 10, totalElements: content.length, totalPages: 1, first: true, last: true, content });

  it('lists orders and filters by status through the backend', async () => {
    signIn();
    const backend = mockBackend({
      'GET /api/cart': { body: emptyCart },
      'GET /api/orders': (req) => ({ body: ordersPage(req.query.status === 'DELIVERED' ? [] : [summary]) }),
    });
    renderApp('/orders');
    expect(await screen.findByRole('link', { name: 'Order EB-20261015-0015' })).toBeInTheDocument();
    await userEvent.selectOptions(screen.getByLabelText('Show'), 'DELIVERED');
    expect(await screen.findByText('No delivered orders')).toBeInTheDocument();
    expect(backend.callsTo('GET /api/orders').at(-1).query.status).toBe('DELIVERED');
  });

  it('shows an empty state when there are no orders', async () => {
    signIn();
    mockBackend({ 'GET /api/cart': { body: emptyCart }, 'GET /api/orders': { body: ordersPage([]) } });
    renderApp('/orders');
    expect(await screen.findByText('No orders yet')).toBeInTheDocument();
  });

  it('buy again from the history list fills the cart and opens it', async () => {
    signIn();
    const backend = mockBackend({
      'GET /api/cart': { body: emptyCart },
      'GET /api/orders': { body: ordersPage([summary]) },
      'POST /api/orders/15/buy-again': { body: { cart, issues: [] } },
    });
    renderApp('/orders');
    await userEvent.click(await screen.findByRole('button', { name: 'Buy again: order EB-20261015-0015' }));
    expect(await screen.findByRole('heading', { name: 'Shopping Cart' })).toBeInTheDocument();
    expect(backend.callsTo('POST /api/orders/15/buy-again')).toHaveLength(1);
  });

  it('buy again from the history list reports books that could not be added', async () => {
    signIn();
    mockBackend({
      'GET /api/cart': { body: emptyCart },
      'GET /api/orders': { body: ordersPage([summary]) },
      'POST /api/orders/15/buy-again': {
        body: {
          cart: emptyCart,
          issues: [{ bookId: 9, title: 'Clean Code', reason: 'OUT_OF_STOCK', requestedQuantity: 2, addedQuantity: 0 }],
        },
      },
    });
    renderApp('/orders');
    await userEvent.click(await screen.findByRole('button', { name: 'Buy again: order EB-20261015-0015' }));
    expect(await screen.findByText(/Clean Code is out of stock/)).toBeInTheDocument();
  });

  it('only hints at cancelling recent orders in a cancellable status', async () => {
    signIn();
    mockBackend({
      'GET /api/cart': { body: emptyCart },
      'GET /api/orders': {
        body: ordersPage([
          { ...summary, id: 16, orderNumber: 'EB-NEW', createdAt: new Date().toISOString() },
          { ...summary, id: 17, orderNumber: 'EB-OLD', createdAt: '2026-01-01T10:00:00Z' },
          { ...summary, id: 18, orderNumber: 'EB-SHIPPED', status: 'SHIPPED', createdAt: new Date().toISOString() },
        ]),
      },
    });
    renderApp('/orders');
    await screen.findByRole('link', { name: 'Order EB-NEW' });
    expect(screen.getAllByRole('link', { name: 'Cancel order' })).toHaveLength(1);
  });

  it('shows order details with tracking and server totals', async () => {
    signIn();
    mockBackend({
      'GET /api/cart': { body: emptyCart },
      'GET /api/orders/15': { body: makeOrder({ payment: { status: 'SUCCESS' } }) },
    });
    renderApp('/orders/15');
    expect(await screen.findByRole('heading', { name: 'Order EB-20261015-0015' })).toBeInTheDocument();
    expect(screen.getByRole('list', { name: 'Order tracking' })).toBeInTheDocument();
    expect(screen.getAllByText(/1,698\.00/).length).toBeGreaterThan(0);
  });

  it('shows not-found for an order that is not in the account', async () => {
    signIn();
    mockBackend({
      'GET /api/cart': { body: emptyCart },
      'GET /api/orders/99': apiError(404, 'ORDER_NOT_FOUND', 'Order not found.'),
    });
    renderApp('/orders/99');
    expect(await screen.findByText('Order not found')).toBeInTheDocument();
  });

  it('cancels an order only after confirmation and shows the new state from the server', async () => {
    signIn();
    let order = makeOrder({ payment: { status: 'SUCCESS' } });
    const backend = mockBackend({
      'GET /api/cart': { body: emptyCart },
      'GET /api/orders/15': () => ({ body: order }),
      'POST /api/orders/15/cancel': () => {
        order = makeOrder({
          status: 'CANCELLED',
          cancelledAt: new Date().toISOString(),
          cancelReason: 'Ordered by mistake',
          payment: { status: 'REFUNDED' },
          cancellation: { cancellable: false, cancellableUntil: new Date().toISOString(), reason: 'ORDER_ALREADY_CANCELLED' },
        });
        return { body: order };
      },
    });
    renderApp('/orders/15');
    await userEvent.click(await screen.findByRole('button', { name: 'Cancel order' }));
    const dialog = screen.getByRole('dialog', { name: 'Cancel this order?' });
    expect(backend.callsTo('POST /api/orders/15/cancel')).toHaveLength(0);
    await userEvent.type(within(dialog).getByLabelText(/Reason/), 'Ordered by mistake');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Yes, cancel order' }));

    expect(await screen.findByText(/Your order was cancelled/)).toBeInTheDocument();
    expect(backend.callsTo('POST /api/orders/15/cancel')[0].body).toEqual({ reason: 'Ordered by mistake' });
    expect(await screen.findByText('This order has already been cancelled.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Cancel order' })).not.toBeInTheDocument();
  });

  it('explains why an order cannot be cancelled', async () => {
    signIn();
    mockBackend({
      'GET /api/cart': { body: emptyCart },
      'GET /api/orders/15': {
        body: makeOrder({
          status: 'SHIPPED',
          cancellation: { cancellable: false, cancellableUntil: new Date().toISOString(), reason: 'ORDER_NOT_CANCELLABLE_STATUS' },
        }),
      },
    });
    renderApp('/orders/15');
    expect(await screen.findByText(/can no longer be cancelled/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Cancel order' })).not.toBeInTheDocument();
  });

  it('buy again fills the cart and goes there when everything was added', async () => {
    signIn();
    mockBackend({
      'GET /api/cart': { body: emptyCart },
      'GET /api/orders/15': { body: makeOrder({ payment: { status: 'SUCCESS' } }) },
      'POST /api/orders/15/buy-again': { body: { cart, issues: [] } },
    });
    renderApp('/orders/15');
    await userEvent.click(await screen.findByRole('button', { name: 'Buy again' }));
    expect(await screen.findByRole('heading', { name: 'Shopping Cart' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Clean Code' })).toBeInTheDocument();
  });

  it('buy again lists books that could not be added and stays on the order', async () => {
    signIn();
    mockBackend({
      'GET /api/cart': { body: emptyCart },
      'GET /api/orders/15': { body: makeOrder({ payment: { status: 'SUCCESS' } }) },
      'POST /api/orders/15/buy-again': {
        body: {
          cart: emptyCart,
          issues: [{ bookId: 9, title: 'Clean Code', reason: 'OUT_OF_STOCK', requestedQuantity: 2, addedQuantity: 0 }],
        },
      },
    });
    renderApp('/orders/15');
    await userEvent.click(await screen.findByRole('button', { name: 'Buy again' }));
    expect(await screen.findByText(/could not be fully added/)).toBeInTheDocument();
    expect(screen.getByText(/Clean Code is out of stock/)).toBeInTheDocument();
  });
});

describe('Journey: recommendations', () => {
  it('shows recommended books and explains the strategy', async () => {
    signIn();
    const backend = mockBackend({
      'GET /api/cart': { body: emptyCart },
      'GET /api/recommendations': { body: { strategy: 'PURCHASE_HISTORY', books: [book] } },
    });
    renderApp('/recommendations');
    expect(await screen.findByRole('link', { name: 'Clean Code' })).toBeInTheDocument();
    expect(screen.getByText(/books you have bought before/)).toBeInTheDocument();
    expect(backend.callsTo('GET /api/recommendations')[0].query.limit).toBe('12');
  });

  it('shows an empty state when there is nothing to recommend', async () => {
    signIn();
    mockBackend({
      'GET /api/cart': { body: emptyCart },
      'GET /api/recommendations': { body: { strategy: 'POPULAR', books: [] } },
    });
    renderApp('/recommendations');
    expect(await screen.findByText('No recommendations yet')).toBeInTheDocument();
  });
});
