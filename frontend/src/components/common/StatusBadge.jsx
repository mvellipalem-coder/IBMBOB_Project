import { humanize } from '../../utils/format.js';

const TONES = {
  PLACED: 'info',
  CONFIRMED: 'info',
  PACKED: 'info',
  SHIPPED: 'info',
  DELIVERED: 'success',
  CANCELLED: 'danger',
  PENDING: 'warning',
  SUCCESS: 'success',
  FAILED: 'danger',
  REFUNDED: 'neutral',
};

/** Small colored label for order and payment statuses. Text is always shown, never color alone. */
export default function StatusBadge({ status }) {
  return <span className={`badge badge--${TONES[status] ?? 'neutral'}`}>{humanize(status)}</span>;
}
