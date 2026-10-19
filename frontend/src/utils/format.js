import { CURRENCY, LOCALE } from '../config/env.js';

const moneyFormatter = new Intl.NumberFormat(LOCALE, { style: 'currency', currency: CURRENCY });

/** Formats a server amount such as 899 or "899.00" as currency. */
export function formatMoney(amount) {
  const value = Number(amount);
  return Number.isFinite(value) ? moneyFormatter.format(value) : '-';
}

/** Formats an ISO date or date-time as e.g. "15 Oct 2026". */
export function formatDate(value) {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '-';
  return date.toLocaleDateString(LOCALE, { day: 'numeric', month: 'short', year: 'numeric' });
}

/** Formats an ISO date-time as e.g. "15 Oct 2026, 4:00 pm". */
export function formatDateTime(value) {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '-';
  return date.toLocaleString(LOCALE, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  });
}

/** "ORDER_NOT_PAYABLE" or "PACKED" -> "Order not payable" / "Packed". */
export function humanize(value) {
  if (!value) return '';
  const text = String(value).toLowerCase().replace(/_/g, ' ');
  return text.charAt(0).toUpperCase() + text.slice(1);
}

/** Joins a list of author names: "A, B". */
export function formatAuthors(authors) {
  return Array.isArray(authors) && authors.length > 0 ? authors.join(', ') : 'Unknown author';
}
