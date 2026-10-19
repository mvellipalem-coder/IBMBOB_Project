import { Link, useSearchParams } from 'react-router-dom';
import EmptyState from '../components/common/EmptyState.jsx';
import ErrorMessage from '../components/common/ErrorMessage.jsx';
import LoadingSpinner from '../components/common/LoadingSpinner.jsx';
import Pagination from '../components/common/Pagination.jsx';
import PageContainer from '../components/layout/PageContainer.jsx';
import OrderCard from '../components/orders/OrderCard.jsx';
import { useAsyncData } from '../hooks/useAsyncData.js';
import { useDocumentTitle } from '../hooks/useDocumentTitle.js';
import { listOrders } from '../services/orderService.js';
import { humanize } from '../utils/format.js';

const STATUSES = ['PLACED', 'CONFIRMED', 'PACKED', 'SHIPPED', 'DELIVERED', 'CANCELLED'];
const PAGE_SIZE = 10;

export default function OrderHistoryPage() {
  useDocumentTitle('My orders');
  const [searchParams, setSearchParams] = useSearchParams();
  const status = STATUSES.includes(searchParams.get('status')) ? searchParams.get('status') : '';
  const page = Math.max(0, Number.parseInt(searchParams.get('page') ?? '0', 10) || 0);

  const orders = useAsyncData(
    ({ signal }) => listOrders({ status, page, size: PAGE_SIZE }, { signal }),
    [status, page],
  );

  const update = (changes) => {
    const next = new URLSearchParams(searchParams);
    Object.entries(changes).forEach(([key, value]) => (value ? next.set(key, value) : next.delete(key)));
    setSearchParams(next);
  };

  const result = orders.data;
  return (
    <PageContainer title="My orders">
      <div className="filter order-filter">
        <label htmlFor="status-filter">Show</label>
        <select
          id="status-filter"
          value={status}
          onChange={(event) => update({ status: event.target.value, page: '' })}
        >
          <option value="">All orders</option>
          {STATUSES.map((value) => (
            <option key={value} value={value}>{humanize(value)}</option>
          ))}
        </select>
      </div>

      {orders.loading && <LoadingSpinner label="Loading your orders..." />}
      {orders.error && <ErrorMessage error={orders.error} title="Could not load your orders" onRetry={orders.reload} />}
      {result && result.content.length === 0 && (
        <EmptyState
          title={status ? `No ${humanize(status).toLowerCase()} orders` : 'No orders yet'}
          message={status ? 'Try a different filter.' : 'When you place an order it will show up here.'}
        >
          {!status && <Link to="/catalogue" className="btn btn--primary">Start shopping</Link>}
        </EmptyState>
      )}
      {result && result.content.length > 0 && (
        <>
          <ul className="order-list" aria-label="Orders">
            {result.content.map((order) => (
              <li key={order.id}><OrderCard order={order} /></li>
            ))}
          </ul>
          <Pagination
            page={result.page}
            totalPages={result.totalPages}
            onPageChange={(next) => update({ page: next ? String(next) : '' })}
          />
        </>
      )}
    </PageContainer>
  );
}
