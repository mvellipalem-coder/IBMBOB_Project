import { formatDateTime, humanize } from '../../utils/format.js';

/** Tracking timeline built from the order's `statusHistory` (oldest first). */
export default function OrderTimeline({ history }) {
  if (!history || history.length === 0) return null;
  return (
    <ol className="timeline" aria-label="Order tracking">
      {history.map((event, index) => (
        <li key={`${event.status}-${event.changedAt}-${index}`} className="timeline__item">
          <span className="timeline__status">{humanize(event.status)}</span>
          <time className="timeline__time" dateTime={event.changedAt}>
            {formatDateTime(event.changedAt)}
          </time>
          {event.note && <span className="timeline__note">{event.note}</span>}
        </li>
      ))}
    </ol>
  );
}
