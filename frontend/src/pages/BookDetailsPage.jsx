import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import AddToCartFeedback from '../components/books/AddToCartFeedback.jsx';
import AvailabilityBadge from '../components/books/AvailabilityBadge.jsx';
import BookCover from '../components/books/BookCover.jsx';
import BookGrid from '../components/books/BookGrid.jsx';
import RatingDisplay from '../components/books/RatingDisplay.jsx';
import EmptyState from '../components/common/EmptyState.jsx';
import ErrorMessage from '../components/common/ErrorMessage.jsx';
import LoadingSpinner from '../components/common/LoadingSpinner.jsx';
import PageContainer from '../components/layout/PageContainer.jsx';
import { useAddToCart } from '../hooks/useAddToCart.js';
import { useAsyncData } from '../hooks/useAsyncData.js';
import { useDocumentTitle } from '../hooks/useDocumentTitle.js';
import { getBook, getRelatedBooks } from '../services/bookService.js';
import { formatAuthors, formatMoney } from '../utils/format.js';

export default function BookDetailsPage() {
  const { id } = useParams();
  const book = useAsyncData(({ signal }) => getBook(id, { signal }), [id]);
  const related = useAsyncData(({ signal }) => getRelatedBooks(id, 6, { signal }), [id]);
  const { add, pendingId, feedback, clearFeedback } = useAddToCart();
  const [quantity, setQuantity] = useState(1);
  useDocumentTitle(book.data?.title ?? 'Book details');

  if (book.loading) return <PageContainer><LoadingSpinner label="Loading book..." /></PageContainer>;
  if (book.error) {
    const notFound = book.error.code === 'BOOK_NOT_FOUND' || book.error.status === 404;
    return (
      <PageContainer>
        {notFound ? (
          <EmptyState title="Book not found" message="This book does not exist or is no longer available.">
            <Link to="/catalogue" className="btn btn--primary">Back to catalogue</Link>
          </EmptyState>
        ) : (
          <ErrorMessage error={book.error} title="Could not load this book" onRetry={book.reload} />
        )}
      </PageContainer>
    );
  }

  const detail = book.data;
  // maxOrderableQuantity comes from the server (stock and per-line limit). The server re-checks on add.
  const maxQuantity = detail.maxOrderableQuantity;
  const selectedQuantity = Math.min(quantity, Math.max(maxQuantity, 1));
  const options = Array.from({ length: maxQuantity }, (_, index) => index + 1);

  return (
    <PageContainer
      breadcrumbs={[
        { label: 'Home', to: '/' },
        { label: 'Catalogue', to: '/catalogue' },
        { label: detail.categoryName, to: `/catalogue?categoryId=${detail.categoryId}` },
        { label: detail.title },
      ]}
    >
      <div className="details-layout">
        <div>
          <article className="book-details">
            <div className="book-details__cover">
              <BookCover book={detail} size="large" />
            </div>
            <div className="book-details__info">
              <h1 className="book-details__title">{detail.title}</h1>
              <p className="book-details__author">by {formatAuthors(detail.authors)}</p>
              <p>{detail.description || 'No description available.'}</p>
              <p>
                Published by:{' '}
                <Link to={`/catalogue?publisherId=${detail.publisherId}`}>{detail.publisherName}</Link>
              </p>
              <p>
                <Link to={`/catalogue?categoryId=${detail.categoryId}`}>{detail.categoryName}</Link>
              </p>

              <p className="price price--large">{formatMoney(detail.price)}</p>
              <p>
                <AvailabilityBadge inStock={detail.inStock} />
              </p>

              {detail.inStock ? (
                <div className="book-details__buy">
                  <div className="filter">
                    <label htmlFor="quantity">Quantity</label>
                    <select
                      id="quantity"
                      value={selectedQuantity}
                      onChange={(event) => setQuantity(Number(event.target.value))}
                    >
                      {options.map((value) => (
                        <option key={value} value={value}>{value}</option>
                      ))}
                    </select>
                  </div>
                  <button
                    type="button"
                    className="btn btn--primary"
                    onClick={() => add(detail, selectedQuantity)}
                    disabled={pendingId === detail.id}
                  >
                    {pendingId === detail.id ? 'Adding...' : 'Add to cart'}
                  </button>
                </div>
              ) : (
                <p className="muted">This book is currently out of stock.</p>
              )}
              <AddToCartFeedback feedback={feedback} onDismiss={clearFeedback} />

              <dl className="book-details__facts">
                {detail.ratingCount > 0 && (
                  <div>
                    <dt>Rating</dt>
                    <dd><RatingDisplay average={detail.ratingAvg} count={detail.ratingCount} /></dd>
                  </div>
                )}
                {detail.publishedYear && (
                  <div>
                    <dt>Published</dt>
                    <dd>{detail.publishedYear}</dd>
                  </div>
                )}
                <div>
                  <dt>ISBN</dt>
                  <dd>{detail.isbn}</dd>
                </div>
              </dl>
            </div>
          </article>
        </div>

        <aside className="related-reads" aria-labelledby="related-title">
          <h2 id="related-title" className="section__title">Related Reads</h2>
          {related.loading && <LoadingSpinner label="Loading related books..." />}
          {related.error && <ErrorMessage error={related.error} onRetry={related.reload} />}
          {related.data && related.data.length === 0 && <p className="muted">No related books found.</p>}
          {related.data && (
            <BookGrid
              books={related.data}
              onAddToCart={add}
              addingBookId={pendingId}
              label="Related books"
              layout="list"
            />
          )}
        </aside>
      </div>
    </PageContainer>
  );
}
