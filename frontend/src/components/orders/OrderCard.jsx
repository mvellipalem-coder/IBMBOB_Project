import { Link } from 'react-router-dom';
import { useBuyAgain } from '../../hooks/useBuyAgain.js';
import { formatDate, formatMoney, humanize } from '../../utils/format.js';
import ErrorMessage from '../common/ErrorMessage.jsx';
import Notice from '../common/Notice.jsx';
import StatusBadge from '../common/StatusBadge.jsx';

const ISSUE_TEXT = {
  BOOK_INACTIVE: 'is no longer sold',
  OUT_OF_STOCK: 'is out of stock',
  QUANTITY_REDUCED: 'was added with a lower quantity',
};
const CANCELLABLE_STATUSES = ['PLACED', 'CONFIRMED', 'PACKED'];
const CANCEL_WINDOW_MS = 48 * 60 * 60 * 1000;

/** One row of the order history list. `order` is an OrderSummary from GET /api/orders. */
export default function OrderCard({ order }) {
  const again = useBuyAgain();
  // Only a hint: the server decides when the cancel button on the details page is offered.
  const maybeCancellable =
    CANCELLABLE_STATUSES.includes(order.status) &&
    Date.now() - new Date(order.createdAt).getTime() < CANCEL_WINDOW_MS;

  return (
    <article className="order-card">
      <div className="order-card__header">
        <h2 className="order-card__number">
          <Link to={`/orders/${order.id}`}>Order {order.orderNumber}</Link>
        </h2>
        <StatusBadge status={order.status} />
      </div>
      <dl className="order-card__details">
        <div>
          <dt>Placed</dt>
          <dd>{formatDate(order.createdAt)}</dd>
        </div>
        <div>
          <dt>{order.status === 'DELIVERED' ? 'Delivered' : 'Expected delivery'}</dt>
          <dd>{order.status === 'CANCELLED' ? '-' : formatDate(order.expectedDeliveryDate)}</dd>
        </div>
        <div>
          <dt>Items</dt>
          <dd>{order.itemCount}</dd>
        </div>
        <div>
          <dt>Total</dt>
          <dd>{formatMoney(order.totalAmount)}</dd>
        </div>
        <div>
          <dt>Payment</dt>
          <dd>
            {order.paymentMethod} <StatusBadge status={order.paymentStatus} />
          </dd>
        </div>
      </dl>

      {again.error && <ErrorMessage error={again.error} title="Could not add these books to your cart" />}
      {again.issues && (
        <Notice type="warning" onDismiss={again.clearIssues}>
          <p>Some books could not be fully added to your cart:</p>
          <ul>
            {again.issues.map((issue) => (
              <li key={issue.bookId}>
                {issue.title} {ISSUE_TEXT[issue.reason] ?? humanize(issue.reason)} (requested {issue.requestedQuantity}, added {issue.addedQuantity}).
              </li>
            ))}
          </ul>
          <Link to="/cart">Go to cart</Link>
        </Notice>
      )}

      <div className="order-card__actions">
        <Link to={`/orders/${order.id}`} className="btn btn--secondary btn--small">
          View details
        </Link>
        <button
          type="button"
          className="btn btn--primary btn--small"
          onClick={() => again.run(order.id)}
          disabled={again.working}
          aria-label={`Buy again: order ${order.orderNumber}`}
        >
          Buy again
        </button>
        {maybeCancellable && (
          <Link to={`/orders/${order.id}`} className="btn btn--danger btn--small">
            Cancel order
          </Link>
        )}
      </div>
    </article>
  );
}
