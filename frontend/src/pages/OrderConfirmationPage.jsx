import { Link, useParams } from 'react-router-dom';
import AddressDisplay from '../components/address/AddressDisplay.jsx';
import BookCover from '../components/books/BookCover.jsx';
import OrderSummary from '../components/cart/OrderSummary.jsx';
import ErrorMessage from '../components/common/ErrorMessage.jsx';
import LoadingSpinner from '../components/common/LoadingSpinner.jsx';
import Notice from '../components/common/Notice.jsx';
import StatusBadge from '../components/common/StatusBadge.jsx';
import PageContainer from '../components/layout/PageContainer.jsx';
import { useAsyncData } from '../hooks/useAsyncData.js';
import { useDocumentTitle } from '../hooks/useDocumentTitle.js';
import { getOrder } from '../services/orderService.js';
import { formatDate, formatMoney } from '../utils/format.js';

function CheckIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" aria-hidden="true">
      <path d="M5 12.5l4.5 4.5L19 7.5" />
    </svg>
  );
}

function WarningIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" aria-hidden="true">
      <path d="M12 6v7M12 17.5v.5" />
    </svg>
  );
}

/**
 * Shows the outcome of an order using only what the backend reports now (GET /api/orders/{id}).
 * A success message appears only when the stored order and payment state justify it.
 */
export default function OrderConfirmationPage() {
  useDocumentTitle('Order confirmation');
  const { orderId } = useParams();
  const order = useAsyncData(({ signal }) => getOrder(orderId, { signal }), [orderId]);

  if (order.loading) {
    return <div className="backdrop"><PageContainer medium><LoadingSpinner label="Checking your order..." /></PageContainer></div>;
  }
  if (order.error) {
    return (
      <div className="backdrop">
        <PageContainer medium>
          <ErrorMessage error={order.error} title="We could not confirm this order" onRetry={order.reload} />
        </PageContainer>
      </div>
    );
  }

  const data = order.data;
  const { payment } = data;
  const cancelled = data.status === 'CANCELLED';
  const paid = payment.status === 'SUCCESS';
  const cashOnDelivery = payment.method === 'COD' && payment.status === 'PENDING';
  const awaitingPayment = !cancelled && payment.status === 'PENDING' && payment.method !== 'COD';
  const confirmed = !cancelled && (paid || cashOnDelivery);

  let heading = 'Order status';
  let lead;
  let banner;
  if (cancelled) {
    heading = 'Order cancelled';
    banner = (
      <Notice type="warning">
        This order was cancelled{data.cancelReason ? `: ${data.cancelReason}` : '.'} It will not be delivered.
      </Notice>
    );
  } else if (paid) {
    heading = 'Thank you, your order is confirmed';
    lead = 'Your purchase of the following reads is successful.';
    banner = <Notice type="success">Payment received. We will deliver your books by {formatDate(data.expectedDeliveryDate)}.</Notice>;
  } else if (cashOnDelivery) {
    heading = 'Thank you, your order is placed';
    lead = 'Your order for the following reads is placed.';
    banner = <Notice type="success">Pay cash on delivery. Expected delivery: {formatDate(data.expectedDeliveryDate)}.</Notice>;
  } else if (awaitingPayment) {
    heading = 'Payment still needed';
    banner = (
      <Notice type="warning">
        Your order is not confirmed yet because the payment has not been completed.{' '}
        <Link to={`/orders/${data.id}/payment`}>Complete payment</Link>
      </Notice>
    );
  } else {
    banner = <Notice type="info">Payment status: {payment.status}.</Notice>;
  }

  return (
    <div className="backdrop">
      <PageContainer medium>
        <section className="confirm-card" aria-labelledby="confirmation-title">
          <div className={`confirm-card__icon${confirmed ? '' : ' confirm-card__icon--warning'}`}>
            {confirmed ? <CheckIcon /> : <WarningIcon />}
          </div>
          <h1 id="confirmation-title">{heading}</h1>
          <p className="muted">Order {data.orderNumber}</p>
          {lead && <p className="confirm-card__lead">{lead}</p>}
          {banner}

          <ul className="purchased-list" aria-label="Books in this order">
            {data.items.map((item) => (
              <li key={item.bookId} className="purchased-list__item">
                <Link to={`/books/${item.bookId}`} aria-hidden="true" tabIndex={-1}>
                  <BookCover book={{ title: item.title }} />
                </Link>
                <div>
                  <h2 className="purchased-list__title">
                    <Link to={`/books/${item.bookId}`}>{item.title}</Link>
                  </h2>
                  <p className="purchased-list__meta">ISBN {item.isbn}</p>
                  <p className="purchased-list__meta">
                    {item.quantity} &times; {formatMoney(item.unitPrice)}
                  </p>
                  <p className="price">{formatMoney(item.lineTotal)}</p>
                  {!cancelled && (
                    <p className="purchased-list__meta">
                      Delivery by <strong>{formatDate(data.expectedDeliveryDate)}</strong>
                    </p>
                  )}
                </div>
              </li>
            ))}
          </ul>

          <div className="confirm-card__section">
            <OrderSummary
              subtotal={data.subtotal}
              pointsDiscount={data.pointsDiscount}
              pointsRedeemed={data.pointsRedeemed}
              total={data.totalAmount}
            >
              <div className="order-summary__status">
                <p>
                  Order status: <StatusBadge status={data.status} />
                </p>
                <p>
                  Payment ({payment.method}): <StatusBadge status={payment.status} />
                </p>
              </div>
            </OrderSummary>
            <section className="card" aria-labelledby="confirmation-address">
              <h2 id="confirmation-address">Delivering to</h2>
              <AddressDisplay address={data.shippingAddress} />
            </section>
          </div>

          <div className="confirm-card__actions">
            <Link to="/catalogue" className="btn btn--primary">Continue your Shopping</Link>
            <Link to={`/orders/${data.id}`} className="btn btn--secondary">View order details</Link>
          </div>
        </section>
      </PageContainer>
    </div>
  );
}
