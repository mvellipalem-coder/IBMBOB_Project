import { Link, useNavigate } from 'react-router-dom';
import AddToCartFeedback from '../components/books/AddToCartFeedback.jsx';
import BookGrid from '../components/books/BookGrid.jsx';
import CategoryNav from '../components/books/CategoryNav.jsx';
import SearchBar from '../components/books/SearchBar.jsx';
import EmptyState from '../components/common/EmptyState.jsx';
import ErrorMessage from '../components/common/ErrorMessage.jsx';
import LoadingSpinner from '../components/common/LoadingSpinner.jsx';
import PageContainer from '../components/layout/PageContainer.jsx';
import { useAuth } from '../context/AuthContext.jsx';
import { useAddToCart } from '../hooks/useAddToCart.js';
import { useAsyncData } from '../hooks/useAsyncData.js';
import { useDocumentTitle } from '../hooks/useDocumentTitle.js';
import { getCategories, listBooks } from '../services/bookService.js';
import { getRecommendations } from '../services/recommendationService.js';

const SECTION_SIZE = 6;

/** One titled row of books with its own loading, error and empty states. */
function BookSection({ id, title, hint, seeAll, state, books, onAdd, addingId, emptyMessage }) {
  return (
    <section className="section" aria-labelledby={id}>
      <div className="section__header">
        <h2 id={id} className="section__title">
          {title}
        </h2>
        {seeAll && <Link to={seeAll}>See all</Link>}
      </div>
      {hint && <p className="section__hint">{hint}</p>}
      {state.loading && <LoadingSpinner label={`Loading ${title.toLowerCase()}...`} />}
      {state.error && <ErrorMessage error={state.error} onRetry={state.reload} />}
      {books && books.length === 0 && <EmptyState title="No books yet" message={emptyMessage} />}
      {books && <BookGrid books={books} onAddToCart={onAdd} addingBookId={addingId} label={title} />}
    </section>
  );
}

export default function HomePage() {
  useDocumentTitle('Home');
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();
  const { add, pendingId, feedback, clearFeedback } = useAddToCart();
  const categories = useAsyncData(({ signal }) => getCategories({ signal }), []);
  const bestsellers = useAsyncData(
    ({ signal }) => listBooks({ sort: 'rating_desc', size: SECTION_SIZE }, { signal }),
    [],
  );
  const newLaunches = useAsyncData(({ signal }) => listBooks({ sort: 'newest', size: SECTION_SIZE }, { signal }), []);
  const recommended = useAsyncData(({ signal }) => getRecommendations(SECTION_SIZE, { signal }), [isAuthenticated], {
    enabled: isAuthenticated,
  });

  const goToCatalogue = (params) => {
    const query = new URLSearchParams(params).toString();
    navigate(query ? `/catalogue?${query}` : '/catalogue');
  };

  const recommendedHint = recommended.data
    ? recommended.data.strategy === 'PURCHASE_HISTORY'
      ? 'Picked using the books you have bought before.'
      : 'Popular books while we learn what you like.'
    : undefined;

  return (
    <PageContainer title="Find your next read">
      <div className="shop-layout">
        <div>
          {categories.loading && <LoadingSpinner label="Loading categories..." />}
          {categories.error && <ErrorMessage error={categories.error} onRetry={categories.reload} />}
          {categories.data && (
            <CategoryNav
              categories={categories.data}
              value=""
              onSelect={(id) => goToCatalogue(id ? { categoryId: id } : {})}
            />
          )}
        </div>

        <div className="shop-main">
          <SearchBar onSearch={(text) => goToCatalogue(text ? { q: text } : {})} />
          <AddToCartFeedback feedback={feedback} onDismiss={clearFeedback} />

          {isAuthenticated ? (
            <BookSection
              id="recommended-title"
              title="Recommended for You"
              hint={recommendedHint}
              seeAll="/recommendations"
              state={recommended}
              books={recommended.data?.books}
              onAdd={add}
              addingId={pendingId}
              emptyMessage="Place an order and we will suggest more books you may like."
            />
          ) : (
            <section className="section" aria-labelledby="recommended-title">
              <h2 id="recommended-title" className="section__title">
                Recommended for You
              </h2>
              <p className="section__hint">
                <Link to="/login">Log in</Link> to get recommendations based on your order history.
              </p>
            </section>
          )}

          <BookSection
            id="bestsellers-title"
            title="Bestsellers this Month"
            seeAll="/catalogue?sort=rating_desc"
            state={bestsellers}
            books={bestsellers.data?.content}
            onAdd={add}
            addingId={pendingId}
            emptyMessage="The catalogue is empty right now. Please check back soon."
          />
          <BookSection
            id="new-launches-title"
            title="New Launches"
            seeAll="/catalogue?sort=newest"
            state={newLaunches}
            books={newLaunches.data?.content}
            onAdd={add}
            addingId={pendingId}
            emptyMessage="No new launches right now. Please check back soon."
          />
        </div>
      </div>
    </PageContainer>
  );
}
