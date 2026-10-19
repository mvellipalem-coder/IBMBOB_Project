import { formatMoney } from '../../utils/format.js';

/**
 * Price breakdown card used on the cart, checkout, confirmation and order pages.
 * All amounts come from the server; this component only displays them.
 * `children` is the place for the page's main action button.
 */
export default function OrderSummary({
  title = 'Order summary',
  itemCount,
  subtotal,
  pointsDiscount,
  pointsRedeemed,
  total,
  note,
  children,
}) {
  const hasDiscount = Number(pointsDiscount) > 0;
  return (
    <aside className="order-summary" aria-label={title}>
      <h2 className="order-summary__title">{title}</h2>
      <dl className="order-summary__list">
        {itemCount !== undefined && (
          <div className="order-summary__row">
            <dt>Items</dt>
            <dd>{itemCount}</dd>
          </div>
        )}
        <div className="order-summary__row">
          <dt>Subtotal</dt>
          <dd>{formatMoney(subtotal)}</dd>
        </div>
        {hasDiscount && (
          <div className="order-summary__row">
            <dt>Gift points{pointsRedeemed ? ` (${pointsRedeemed})` : ''}</dt>
            <dd>-{formatMoney(pointsDiscount)}</dd>
          </div>
        )}
        <div className="order-summary__row order-summary__row--total">
          <dt>Total</dt>
          <dd>{formatMoney(total ?? subtotal)}</dd>
        </div>
      </dl>
      {note && <p className="order-summary__note">{note}</p>}
      {children && <div className="order-summary__actions">{children}</div>}
    </aside>
  );
}
