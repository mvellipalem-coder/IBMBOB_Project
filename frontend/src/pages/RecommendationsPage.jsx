import { Link } from 'react-router-dom';
import AddToCartFeedback from '../components/books/AddToCartFeedback.jsx';
import BookGrid from '../components/books/BookGrid.jsx';
import EmptyState from '../components/common/EmptyState.jsx';
import ErrorMessage from '../components/common/ErrorMessage.jsx';
import LoadingSpinner from '../components/common/LoadingSpinner.jsx';
import PageContainer from '../components/layout/PageContainer.jsx';
import { useAddToCart } from '../hooks/useAddToCart.js';
import { useAsyncData } from '../hooks/useAsyncData.js';
import { useDocumentTitle } from '../hooks/useDocumentTitle.js';
import { getRecommendations } from '../services/recommendationService.js';

export default function RecommendationsPage() {
  useDocumentTitle('Recommended for you');
  const recommendations = useAsyncData(({ signal }) => getRecommendations(12, { signal }), []);
  const { add, pendingId, feedback, clearFeedback } = useAddToCart();
  const data = recommendations.data;

  return (
    <PageContainer
      title="Recommended for you"
      subtitle={
        data
          ? data.strategy === 'PURCHASE_HISTORY'
            ? 'Picked using the books you have bought before.'
            : 'Popular books while we learn what you like.'
          : undefined
      }
    >
      <AddToCartFeedback feedback={feedback} onDismiss={clearFeedback} />
      {recommendations.loading && <LoadingSpinner label="Finding books for you..." />}
      {recommendations.error && (
        <ErrorMessage error={recommendations.error} title="Could not load recommendations" onRetry={recommendations.reload} />
      )}
      {data && data.books.length === 0 && (
        <EmptyState title="No recommendations yet" message="Browse the catalogue and place an order to get suggestions.">
          <Link to="/catalogue" className="btn btn--primary">Browse books</Link>
        </EmptyState>
      )}
      {data && <BookGrid books={data.books} onAddToCart={add} addingBookId={pendingId} label="Recommended books" />}
    </PageContainer>
  );
}
