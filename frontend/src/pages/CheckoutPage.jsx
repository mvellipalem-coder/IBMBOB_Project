import { useRef, useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import AddressCard from '../components/address/AddressCard.jsx';
import AddressForm from '../components/address/AddressForm.jsx';
import BookCover from '../components/books/BookCover.jsx';
import OrderSummary from '../components/cart/OrderSummary.jsx';
import EmptyState from '../components/common/EmptyState.jsx';
import ErrorMessage from '../components/common/ErrorMessage.jsx';
import FormField from '../components/common/FormField.jsx';
import LoadingSpinner from '../components/common/LoadingSpinner.jsx';
import PageContainer from '../components/layout/PageContainer.jsx';
import PaymentMethodSelector from '../components/payment/PaymentMethodSelector.jsx';
import { useCart } from '../context/CartContext.jsx';
import { useAsyncData } from '../hooks/useAsyncData.js';
import { useDocumentTitle } from '../hooks/useDocumentTitle.js';
import { createAddress, listAddresses } from '../services/addressService.js';
import { placeOrder } from '../services/checkoutService.js';
import { getGiftPointBalance } from '../services/giftPointService.js';
import { formatAuthors, formatMoney } from '../utils/format.js';
import { newIdempotencyKey } from '../utils/idempotency.js';

const CHECKOUT_CRUMBS = [
  { label: 'Home', to: '/' },
  { label: 'Shopping Cart', to: '/cart' },
  { label: 'Checkout' },
];

export default function CheckoutPage() {
  useDocumentTitle('Checkout');
  const navigate = useNavigate();
  const { cart, loading: cartLoading, refresh: refreshCart, replaceCart } = useCart();
  const addresses = useAsyncData(({ signal }) => listAddresses({ signal }), []);
  const points = useAsyncData(({ signal }) => getGiftPointBalance({ signal }), []);

  const [chosenAddressId, setChosenAddressId] = useState(null);
  const [addingAddress, setAddingAddress] = useState(false);
  const [paymentMethod, setPaymentMethod] = useState('');
  const [pointsText, setPointsText] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [submitError, setSubmitError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  // One Idempotency-Key per distinct request. Retrying the same request (for example after a timeout)
  // reuses the key, so the server can never create two orders. Changing the request creates a new key.
  const keyRef = useRef({ signature: '', key: '' });

  const savedAddresses = addresses.data ?? [];
  const defaultAddress = savedAddresses.find((address) => address.isDefault) ?? savedAddresses[0];
  const addressId = chosenAddressId ?? defaultAddress?.id ?? null;

  if (cartLoading && cart.items.length === 0) {
    return <PageContainer title="Checkout" breadcrumbs={CHECKOUT_CRUMBS}><LoadingSpinner label="Loading your cart..." /></PageContainer>;
  }
  if (cart.items.length === 0 && !submitting) {
    return (
      <PageContainer title="Checkout" breadcrumbs={CHECKOUT_CRUMBS}>
        <EmptyState title="Your cart is empty" message="Add books to your cart before checking out.">
          <Link to="/catalogue" className="btn btn--primary">Browse books</Link>
        </EmptyState>
      </PageContainer>
    );
  }
  if (cart.items.some((item) => item.quantity > item.maxQuantity)) {
    return <Navigate to="/cart" replace />;
  }

  const balance = points.data?.balance ?? 0;
  const maxPercent = points.data?.maxRedeemPercentOfSubtotal ?? 0;
  // Only a hint for the input. The server enforces the real limits and returns the final totals.
  const pointsHint = points.data
    ? `You have ${balance} points. At most ${maxPercent}% of the subtotal can be paid with points.`
    : undefined;

  const handleAddAddress = async (values) => {
    const created = await createAddress(values);
    await addresses.reload();
    setChosenAddressId(created.id);
    setAddingAddress(false);
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    const found = {};
    if (!addressId) found.address = 'Choose or add a delivery address.';
    if (!paymentMethod) found.paymentMethod = 'Choose a payment method.';
    const trimmedPoints = pointsText.trim();
    if (trimmedPoints !== '' && !/^\d{1,7}$/.test(trimmedPoints)) {
      found.points = 'Enter a whole number of points.';
    }
    setFieldErrors(found);
    setSubmitError(null);
    if (Object.keys(found).length > 0) return;

    const pointsToRedeem = trimmedPoints === '' ? 0 : Number(trimmedPoints);
    const signature = `${addressId}|${paymentMethod}|${pointsToRedeem}`;
    if (keyRef.current.signature !== signature) {
      keyRef.current = { signature, key: newIdempotencyKey() };
    }

    setSubmitting(true);
    try {
      const order = await placeOrder({ addressId, paymentMethod, pointsToRedeem }, keyRef.current.key);
      // The server emptied the cart when it created the order.
      replaceCart(null);
      refreshCart();
      // CARD and UPI must still be paid. COD needs no payment step.
      navigate(paymentMethod === 'COD' ? `/orders/${order.id}/confirmation` : `/orders/${order.id}/payment`, {
        replace: true,
      });
    } catch (error) {
      if (error.code === 'INVALID_POINTS_REDEMPTION') setFieldErrors({ points: error.message });
      else if (error.code === 'ADDRESS_NOT_FOUND') setFieldErrors({ address: error.message });
      else setSubmitError(error);
      if (error.code === 'INSUFFICIENT_STOCK' || error.code === 'EMPTY_CART') refreshCart();
      setSubmitting(false);
    }
  };

  return (
    <PageContainer title="Checkout" breadcrumbs={CHECKOUT_CRUMBS}>
      <form onSubmit={handleSubmit} noValidate>
        <div className="two-column">
          <div className="checkout-steps">
            {submitError && <ErrorMessage error={submitError} title="We could not place your order" />}

            <section className="card checkout-items" aria-labelledby="checkout-items-title">
              <h2 id="checkout-items-title">Your books</h2>
              <ul className="checkout-items__list" aria-label="Books in this order">
                {cart.items.map((item) => (
                  <li key={item.itemId} className="checkout-items__line">
                    <BookCover book={item.book} size="small" />
                    <div>
                      <p className="checkout-items__title">{item.book.title}</p>
                      <p className="checkout-items__meta">by {formatAuthors(item.book.authors)}</p>
                      <p className="checkout-items__meta">
                        {item.quantity} &times; {formatMoney(item.book.price)}
                      </p>
                    </div>
                  </li>
                ))}
              </ul>
              <Link to="/cart" className="btn btn--link">Edit cart</Link>
            </section>

            <section className="card" aria-labelledby="checkout-address-title">
              <h2 id="checkout-address-title">1. Delivery address</h2>
              {addresses.loading && <LoadingSpinner label="Loading addresses..." />}
              {addresses.error && (
                <ErrorMessage error={addresses.error} title="Could not load addresses" onRetry={addresses.reload} />
              )}
              {addresses.data && savedAddresses.length === 0 && !addingAddress && (
                <p className="muted">You have no saved addresses yet.</p>
              )}
              {savedAddresses.length > 0 && (
                <fieldset className="address-options">
                  <legend className="visually-hidden">Choose a delivery address</legend>
                  {savedAddresses.map((address) => (
                    <AddressCard
                      key={address.id}
                      address={address}
                      name="checkout-address"
                      selectable
                      selected={address.id === addressId}
                      onSelect={(selected) => setChosenAddressId(selected.id)}
                    />
                  ))}
                </fieldset>
              )}
              {fieldErrors.address && <p className="form-field__error" role="alert">{fieldErrors.address}</p>}
              {addingAddress ? (
                <AddressForm
                  onSubmit={handleAddAddress}
                  onCancel={() => setAddingAddress(false)}
                  submitLabel="Save and use this address"
                />
              ) : (
                addresses.data && (
                  <button
                    type="button"
                    className="btn btn--secondary checkout-add-address"
                    onClick={() => setAddingAddress(true)}
                  >
                    Add a new address
                  </button>
                )
              )}
            </section>

            <section className="card" aria-labelledby="checkout-payment-title">
              <h2 id="checkout-payment-title">2. Payment method</h2>
              <PaymentMethodSelector
                value={paymentMethod}
                onChange={setPaymentMethod}
                disabled={submitting}
                error={fieldErrors.paymentMethod}
              />
            </section>

            <section className="card" aria-labelledby="checkout-points-title">
              <h2 id="checkout-points-title">3. Gift points (optional)</h2>
              {points.loading && <LoadingSpinner label="Loading gift points..." />}
              {points.error && (
                <ErrorMessage
                  error={points.error}
                  title="Could not load your gift points"
                  onRetry={points.reload}
                />
              )}
              {points.data && (
                <FormField
                  id="points-to-redeem"
                  label="Points to redeem"
                  type="text"
                  inputMode="numeric"
                  value={pointsText}
                  onChange={(event) => setPointsText(event.target.value)}
                  error={fieldErrors.points}
                  hint={pointsHint}
                  disabled={balance === 0 || submitting}
                />
              )}
            </section>
          </div>

          <OrderSummary
            title="Grand Total"
            itemCount={cart.totalQuantity}
            subtotal={cart.subtotal}
            total={cart.subtotal}
            note={
              pointsText.trim() && Number(pointsText) > 0
                ? 'Your gift-point discount and final total are calculated and confirmed by the server when you place the order.'
                : 'Shipping and the final total are confirmed by the server when you place the order.'
            }
          >
            <button type="submit" className="btn btn--primary btn--block" disabled={submitting}>
              {submitting ? 'Placing order...' : 'Place order'}
            </button>
            <Link to="/cart" className="btn btn--secondary btn--block">Back to cart</Link>
          </OrderSummary>
        </div>
      </form>
    </PageContainer>
  );
}
