import { Link } from 'react-router-dom';
import { formatAuthors, formatMoney } from '../../utils/format.js';
import BookCover from '../books/BookCover.jsx';

/**
 * One cart line with a quantity stepper (- value +) and a remove button.
 * `item` has the shape returned by GET /api/cart: { itemId, book, quantity, lineTotal, maxQuantity }.
 * The + button stops at `maxQuantity` (stock and the per-line limit), so the user cannot ask for too many.
 * Every change goes to the server, which returns the new totals.
 */
export default function CartItem({ item, onQuantityChange, onRemove, busy = false }) {
  const { book, quantity, lineTotal, maxQuantity, itemId } = item;
  const overStock = quantity > maxQuantity;
  const valueId = `quantity-${itemId}`;
  // When stock dropped below the cart quantity, "-" lets the customer step down to what is available.
  const canDecrease = !busy && quantity > 1;
  const canIncrease = !busy && quantity < maxQuantity;

  return (
    <li className="cart-item">
      <Link to={`/books/${book.id}`} className="cart-item__cover" aria-hidden="true" tabIndex={-1}>
        <BookCover book={book} size="medium" />
      </Link>
      <div className="cart-item__details">
        <h3 className="cart-item__title">
          <Link to={`/books/${book.id}`}>{book.title}</Link>
        </h3>
        <p className="cart-item__author">by {formatAuthors(book.authors)}</p>
        <p className="cart-item__price">{formatMoney(book.price)} each</p>
        {overStock && (
          <p className="form-field__error" role="alert">
            {maxQuantity === 0
              ? 'This book is now out of stock. Remove it to continue.'
              : `Only ${maxQuantity} available. Lower the quantity to continue.`}
          </p>
        )}
        <div className="cart-item__row">
          <div className="stepper" role="group" aria-label={`Quantity of ${book.title}`}>
            <button
              type="button"
              className="stepper__btn"
              onClick={() => onQuantityChange(item, quantity - 1)}
              disabled={!canDecrease}
              aria-label={`Decrease quantity of ${book.title}`}
            >
              &minus;
            </button>
            <output id={valueId} className="stepper__value" aria-label="Qty">
              {quantity}
            </output>
            <button
              type="button"
              className="stepper__btn"
              onClick={() => onQuantityChange(item, quantity + 1)}
              disabled={!canIncrease}
              aria-label={`Increase quantity of ${book.title}`}
            >
              +
            </button>
          </div>
          <div className="cart-item__actions">
            <p className="cart-item__total">{formatMoney(lineTotal)}</p>
            <button
              type="button"
              className="btn btn--link btn--link-danger"
              onClick={() => onRemove(item)}
              disabled={busy}
              aria-label={`Remove ${book.title} from cart`}
            >
              Remove
            </button>
          </div>
        </div>
      </div>
    </li>
  );
}
