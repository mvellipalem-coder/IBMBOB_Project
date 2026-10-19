import { Link } from 'react-router-dom';
import { formatAuthors, formatMoney } from '../../utils/format.js';
import AvailabilityBadge from './AvailabilityBadge.jsx';
import BookCover from './BookCover.jsx';
import RatingDisplay from './RatingDisplay.jsx';

/**
 * One book as a horizontal card (cover on the left, details on the right), as in the wireframes:
 * title, author, category and publisher links, price, availability and an add-to-cart button.
 * `onAddToCart(book)` and `adding` are supplied by the page (see useAddToCart).
 */
export default function BookCard({ book, onAddToCart, adding = false }) {
  return (
    <article className="book-card">
      <Link to={`/books/${book.id}`} className="book-card__cover-link" aria-hidden="true" tabIndex={-1}>
        <BookCover book={book} />
      </Link>
      <div className="book-card__body">
        <h3 className="book-card__title">
          <Link to={`/books/${book.id}`}>{book.title}</Link>
        </h3>
        <p className="book-card__author">by {formatAuthors(book.authors)}</p>
        {book.ratingCount > 0 && <RatingDisplay average={book.ratingAvg} count={book.ratingCount} />}
        <p className="book-card__tags">
          <Link to={`/catalogue?categoryId=${book.categoryId}`}>{book.categoryName}</Link>
          {book.publisherName && (
            <>
              {', '}
              <Link to={`/catalogue?publisherId=${book.publisherId}`}>{book.publisherName}</Link>
            </>
          )}
        </p>
        <div className="book-card__price-row">
          <span className="price">{formatMoney(book.price)}</span>
          <AvailabilityBadge inStock={book.inStock} />
        </div>
        {onAddToCart && (
          <button
            type="button"
            className="btn btn--primary btn--small book-card__add"
            onClick={() => onAddToCart(book)}
            disabled={!book.inStock || adding}
            aria-label={`Add ${book.title} to cart`}
          >
            {adding ? 'Adding...' : book.inStock ? 'Add to cart' : 'Unavailable'}
          </button>
        )}
      </div>
    </article>
  );
}
