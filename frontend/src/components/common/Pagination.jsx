/** Previous / Next navigation. `page` is zero-based, like the backend. */
export default function Pagination({ page, totalPages, onPageChange }) {
  if (!totalPages || totalPages <= 1) return null;
  return (
    <nav className="pagination" aria-label="Pagination">
      <button
        type="button"
        className="btn btn--secondary btn--small"
        onClick={() => onPageChange(page - 1)}
        disabled={page <= 0}
      >
        Previous
      </button>
      <span className="pagination__status" aria-live="polite">
        Page {page + 1} of {totalPages}
      </span>
      <button
        type="button"
        className="btn btn--secondary btn--small"
        onClick={() => onPageChange(page + 1)}
        disabled={page >= totalPages - 1}
      >
        Next
      </button>
    </nav>
  );
}
