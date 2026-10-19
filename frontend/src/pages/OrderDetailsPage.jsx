import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import AddressDisplay from '../components/address/AddressDisplay.jsx';
import OrderSummary from '../components/cart/OrderSummary.jsx';
import ConfirmationDialog from '../components/common/ConfirmationDialog.jsx';
import EmptyState from '../components/common/EmptyState.jsx';
import ErrorMessage from '../components/common/ErrorMessage.jsx';
import LoadingSpinner from '../components/common/LoadingSpinner.jsx';
import Notice from '../components/common/Notice.jsx';
import StatusBadge from '../components/common/StatusBadge.jsx';
import PageContainer from '../components/layout/PageContainer.jsx';
import OrderItemsList from '../components/orders/OrderItemsList.jsx';
import OrderTimeline from '../components/orders/OrderTimeline.jsx';
import { useCart } from '../context/CartContext.jsx';
import { useAsyncData } from '../hooks/useAsyncData.js';
import { useDocumentTitle } from '../hooks/useDocumentTitle.js';
import { buyAgain, cancelOrder, getOrder } from '../services/orderService.js';
import { formatDate, formatDateTime, humanize } from '../utils/format.js';

const REASON_TEXT = {
  ORDER_ALREADY_CANCELLED: 'This order has already been cancelled.',
  ORDER_NOT_CANCELLABLE_STATUS: 'This order can no longer be cancelled because it has already shipped or been delivered.',
  CANCELLATION_WINDOW_EXPIRED: 'The 48-hour cancellation window has ended.',
};
const ISSUE_TEXT = {
  BOOK_INACTIVE: 'is no longer sold',
  OUT_OF_STOCK: 'is out of stock',
  QUANTITY_REDUCED: 'was added with a lower quantity',
};

export default function OrderDetailsPage() {
  const { orderId } = useParams();
  const navigate = useNavigate();
  const { replaceCart } = useCart();
  const order = useAsyncData(({ signal }) => getOrder(orderId, { signal }), [orderId]);
  useDocumentTitle(order.data ? `Order ${order.data.orderNumber}` : 'Order details');

  const [confirmingCancel, setConfirmingCancel] = useState(false);
  const [cancelReason, setCancelReason] = useState('');
  const [working, setWorking] = useState(false);
  const [actionError, setActionError] = useState(null);
  const [notice, setNotice] = useState(null);
  const [issues, setIssues] = useState(null);

  if (order.loading) return <PageContainer title="Order details"><LoadingSpinner label="Loading order..." /></PageContainer>;
  if (order.error) {
    const notFound = order.error.code === 'ORDER_NOT_FOUND' || order.error.status === 404;
    return (
      <PageContainer title="Order details">
        {notFound ? (
          <EmptyState title="Order not found" message="We could not find this order in your account.">
            <Link to="/orders" className="btn btn--primary">Back to my orders</Link>
          </EmptyState>
        ) : (
          <ErrorMessage error={order.error} title="Could not load this order" onRetry={order.reload} />
        )}
      </PageContainer>
    );
  }

  const data = order.data;
  const { cancellation, payment } = data;

  const handleCancel = async () => {
    setWorking(true);
    setActionError(null);
    try {
      // The page updates only after the server confirms the cancellation.
      await cancelOrder(data.id, cancelReason.trim() || undefined);
      setNotice('Your order was cancelled. Stock and any gift points were returned.');
      order.reload();
    } catch (error) {
      setActionError(error);
      order.reload(); // the order may have changed since the page loaded
    } finally {
      setWorking(false);
      setConfirmingCancel(false);
      setCancelReason('');
    }
  };

  const handleBuyAgain = async () => {
    setWorking(true);
    setActionError(null);
    setIssues(null);
    try {
      const result = await buyAgain(data.id);
      replaceCart(result.cart);
      if (result.issues.length === 0) {
        navigate('/cart');
      } else {
        setIssues(result.issues);
      }
    } catch (error) {
      setActionError(error);
    } finally {
      setWorking(false);
    }
  };

  return (
    <PageContainer
      title={`Order ${data.orderNumber}`}
      subtitle={`Placed ${formatDateTime(data.createdAt)}`}
      actions={<StatusBadge status={data.status} />}
    >
      {notice && <Notice type="success" onDismiss={() => setNotice(null)}>{notice}</Notice>}
      {actionError && <ErrorMessage error={actionError} title="That did not work" />}
      {issues && (
        <Notice type="warning" onDismiss={() => setIssues(null)}>
          <p>Some books could not be fully added to your cart:</p>
          <ul>
            {issues.map((issue) => (
              <li key={issue.bookId}>
                {issue.title} {ISSUE_TEXT[issue.reason] ?? humanize(issue.reason)} (requested {issue.requestedQuantity}, added {issue.addedQuantity}).
              </li>
            ))}
          </ul>
          <Link to="/cart">Go to cart</Link>
        </Notice>
      )}
      {payment.method !== 'COD' && payment.status === 'PENDING' && data.status !== 'CANCELLED' && (
        <Notice type="warning">
          Payment has not been completed. <Link to={`/orders/${data.id}/payment`}>Complete payment</Link>
        </Notice>
      )}

      <div className="two-column">
        <div>
          <section className="card" aria-labelledby="od-tracking">
            <h2 id="od-tracking">Tracking</h2>
            {data.status === 'CANCELLED' ? (
              <p>Cancelled {formatDateTime(data.cancelledAt)}{data.cancelReason ? ` - ${data.cancelReason}` : ''}</p>
            ) : data.status === 'DELIVERED' ? (
              <p>Delivered {formatDateTime(data.deliveredAt)}</p>
            ) : (
              <p>Expected delivery: <strong>{formatDate(data.expectedDeliveryDate)}</strong></p>
            )}
            <OrderTimeline history={data.statusHistory} />
          </section>

          <section className="card" aria-labelledby="od-items">
            <h2 id="od-items">Items</h2>
            <OrderItemsList items={data.items} />
          </section>

          <section className="card" aria-labelledby="od-address">
            <h2 id="od-address">Delivery address</h2>
            <AddressDisplay address={data.shippingAddress} />
          </section>
        </div>

        <div>
          <OrderSummary
            subtotal={data.subtotal}
            pointsDiscount={data.pointsDiscount}
            pointsRedeemed={data.pointsRedeemed}
            total={data.totalAmount}
          >
            <p>Payment ({payment.method}): <StatusBadge status={payment.status} /></p>
            {payment.transactionRef && <p className="muted">Reference: {payment.transactionRef}</p>}
            {payment.failureReason && <p className="muted">{payment.failureReason}</p>}
            {data.pointsEarned > 0 && (
              <p className="muted">
                {data.status === 'DELIVERED' ? 'Points earned' : 'Points you will earn on delivery'}: {data.pointsEarned}
              </p>
            )}
            <button type="button" className="btn btn--secondary btn--block" onClick={handleBuyAgain} disabled={working}>
              Buy again
            </button>
            {cancellation.cancellable ? (
              <button
                type="button"
                className="btn btn--danger btn--block"
                onClick={() => setConfirmingCancel(true)}
                disabled={working}
              >
                Cancel order
              </button>
            ) : (
              <p className="muted">{REASON_TEXT[cancellation.reason] ?? 'This order cannot be cancelled.'}</p>
            )}
            {cancellation.cancellable && (
              <p className="muted">You can cancel until {formatDateTime(cancellation.cancellableUntil)}.</p>
            )}
          </OrderSummary>
        </div>
      </div>

      <ConfirmationDialog
        open={confirmingCancel}
        title="Cancel this order?"
        message="Books go back into stock, gift points are returned and any payment is refunded."
        confirmLabel="Yes, cancel order"
        cancelLabel="Keep order"
        danger
        busy={working}
        onConfirm={handleCancel}
        onCancel={() => setConfirmingCancel(false)}
      >
        <div className="form-field">
          <label htmlFor="cancel-reason">Reason (optional)</label>
          <input
            id="cancel-reason"
            type="text"
            maxLength={255}
            value={cancelReason}
            onChange={(event) => setCancelReason(event.target.value)}
          />
        </div>
      </ConfirmationDialog>
    </PageContainer>
  );
}
