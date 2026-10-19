import { Link } from 'react-router-dom';
import EmptyState from '../components/common/EmptyState.jsx';
import PageContainer from '../components/layout/PageContainer.jsx';
import { useDocumentTitle } from '../hooks/useDocumentTitle.js';

export default function NotFoundPage() {
  useDocumentTitle('Page not found');
  return (
    <PageContainer>
      <EmptyState title="Page not found" message="The page you are looking for does not exist.">
        <Link to="/" className="btn btn--primary">Go to the home page</Link>
      </EmptyState>
    </PageContainer>
  );
}
