import BookCard from './BookCard.jsx';

/**
 * Responsive grid of BookCards (several columns on wide screens, one on phones).
 * `layout="list"` forces a single column, used for the "Related reads" side panel.
 * Renders nothing when the list is empty: the page shows its own empty state.
 */
export default function BookGrid({ books, onAddToCart, addingBookId, label = 'Books', layout = 'grid' }) {
  if (!books || books.length === 0) return null;
  return (
    <ul className={`book-grid${layout === 'list' ? ' book-grid--list' : ''}`} aria-label={label}>
      {books.map((book) => (
        <li key={book.id}>
          <BookCard book={book} onAddToCart={onAddToCart} adding={addingBookId === book.id} />
        </li>
      ))}
    </ul>
  );
}
