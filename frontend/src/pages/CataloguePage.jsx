import { useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';
import AddToCartFeedback from '../components/books/AddToCartFeedback.jsx';
import BookGrid from '../components/books/BookGrid.jsx';
import CategoryNav from '../components/books/CategoryNav.jsx';
import PriceFilter from '../components/books/PriceFilter.jsx';
import PublisherFilter from '../components/books/PublisherFilter.jsx';
import SearchBar from '../components/books/SearchBar.jsx';
import EmptyState from '../components/common/EmptyState.jsx';
import ErrorMessage from '../components/common/ErrorMessage.jsx';
import LoadingSpinner from '../components/common/LoadingSpinner.jsx';
import Pagination from '../components/common/Pagination.jsx';
import PageContainer from '../components/layout/PageContainer.jsx';
import { useAddToCart } from '../hooks/useAddToCart.js';
import { useAsyncData } from '../hooks/useAsyncData.js';
import { useDocumentTitle } from '../hooks/useDocumentTitle.js';
import { getCategories, getPublishers, listBooks, searchBooks } from '../services/bookService.js';

const PAGE_SIZE = 12;
const BROWSE_SORTS = [
  ['newest', 'Newest'],
  ['title_asc', 'Title A-Z'],
  ['title_desc', 'Title Z-A'],
  ['price_asc', 'Price: low to high'],
  ['price_desc', 'Price: high to low'],
  ['rating_desc', 'Top rated'],
];
const SEARCH_SORTS = [['relevance', 'Relevance'], ...BROWSE_SORTS];

export default function CataloguePage() {
  useDocumentTitle('Catalogue');
  const [searchParams, setSearchParams] = useSearchParams();
  const { add, pendingId, feedback, clearFeedback } = useAddToCart();

  // All filters live in the URL so results can be shared, bookmarked and used with the back button.
  const q = searchParams.get('q') ?? '';
  const categoryId = searchParams.get('categoryId') ?? '';
  const publisherId = searchParams.get('publisherId') ?? '';
  const minPrice = searchParams.get('minPrice') ?? '';
  const maxPrice = searchParams.get('maxPrice') ?? '';
  const inStock = searchParams.get('inStock') === 'true';
  const defaultSort = q ? 'relevance' : 'newest';
  const sortOptions = q ? SEARCH_SORTS : BROWSE_SORTS;
  const requestedSort = searchParams.get('sort');
  const sort = sortOptions.some(([value]) => value === requestedSort) ? requestedSort : defaultSort;
  const page = Math.max(0, Number.parseInt(searchParams.get('page') ?? '0', 10) || 0);

  const updateParams = (changes, { resetPage = true } = {}) => {
    const next = new URLSearchParams(searchParams);
    Object.entries(changes).forEach(([key, value]) => {
      if (value === '' || value === null || value === undefined || value === false) next.delete(key);
      else next.set(key, String(value));
    });
    if (resetPage) next.delete('page');
    setSearchParams(next);
  };

  const categories = useAsyncData(({ signal }) => getCategories({ signal }), []);
  const publishers = useAsyncData(({ signal }) => getPublishers({ signal }), []);

  const filterKey = useMemo(
    () => [q, categoryId, publisherId, minPrice, maxPrice, inStock, sort, page].join('|'),
    [q, categoryId, publisherId, minPrice, maxPrice, inStock, sort, page],
  );

  const books = useAsyncData(
    ({ signal }) => {
      const params = {
        categoryId,
        publisherId,
        minPrice,
        maxPrice,
        inStock: inStock ? true : undefined,
        sort,
        page,
        size: PAGE_SIZE,
      };
      return q ? searchBooks({ ...params, q }, { signal }) : listBooks(params, { signal });
    },
    [filterKey],
  );

  const hasFilters = Boolean(q || categoryId || publisherId || minPrice || maxPrice || inStock);
  const result = books.data;
  const selectedCategory = (categories.data ?? []).find((category) => String(category.id) === categoryId);

  const breadcrumbs = [{ label: 'Home', to: '/' }, { label: 'Catalogue', to: selectedCategory ? '/catalogue' : undefined }];
  if (selectedCategory) breadcrumbs.push({ label: selectedCategory.name });

  return (
    <PageContainer
      title={selectedCategory ? selectedCategory.name : 'Catalogue'}
      subtitle={result ? `${result.totalElements} ${result.totalElements === 1 ? 'book' : 'books'} found` : undefined}
      breadcrumbs={breadcrumbs}
    >
      <div className="shop-layout">
        <div>
          {categories.error && <ErrorMessage error={categories.error} onRetry={categories.reload} />}
          <CategoryNav
            categories={categories.data ?? []}
            value={categoryId}
            onSelect={(value) => updateParams({ categoryId: value })}
          />
        </div>

        <div className="shop-main">
          <section className="filter-bar" aria-label="Search and filters">
            <div className="filter-bar__search">
              <SearchBar key={q} initialValue={q} onSearch={(text) => updateParams({ q: text, sort: '' })} />
            </div>
            <div>
              {publishers.error && <ErrorMessage error={publishers.error} onRetry={publishers.reload} />}
              <PublisherFilter
                publishers={publishers.data ?? []}
                value={publisherId}
                onChange={(value) => updateParams({ publisherId: value })}
                disabled={publishers.loading}
              />
            </div>
            <PriceFilter
              key={`${minPrice}-${maxPrice}`}
              minPrice={minPrice}
              maxPrice={maxPrice}
              onApply={(range) => updateParams(range)}
            />
            <div className="filter">
              <label htmlFor="sort-select">Sort by</label>
              <select id="sort-select" value={sort} onChange={(event) => updateParams({ sort: event.target.value })}>
                {sortOptions.map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </select>
            </div>
            <div className="filter-bar__extras">
              <div className="form-check">
                <input
                  id="in-stock-filter"
                  type="checkbox"
                  checked={inStock}
                  onChange={(event) => updateParams({ inStock: event.target.checked })}
                />
                <label htmlFor="in-stock-filter">In stock only</label>
              </div>
              {hasFilters && (
                <button type="button" className="btn btn--secondary btn--small" onClick={() => setSearchParams({})}>
                  Clear all filters
                </button>
              )}
            </div>
          </section>

          <section aria-label="Search results" aria-busy={books.loading}>
            <AddToCartFeedback feedback={feedback} onDismiss={clearFeedback} />
            {books.loading && <LoadingSpinner label="Loading books..." />}
            {books.error && (
              <ErrorMessage error={books.error} title="Could not load books" onRetry={books.reload} />
            )}
            {result && result.content.length === 0 && (
              <EmptyState
                title="No books found"
                message={hasFilters ? 'Try different keywords or remove some filters.' : 'The catalogue is empty.'}
              >
                {hasFilters && (
                  <button type="button" className="btn btn--primary" onClick={() => setSearchParams({})}>
                    Clear all filters
                  </button>
                )}
              </EmptyState>
            )}
            {result && (
              <>
                <BookGrid books={result.content} onAddToCart={add} addingBookId={pendingId} label="Search results" />
                <Pagination
                  page={result.page}
                  totalPages={result.totalPages}
                  onPageChange={(next) => updateParams({ page: next || '' }, { resetPage: false })}
                />
              </>
            )}
          </section>
        </div>
      </div>
    </PageContainer>
  );
}
