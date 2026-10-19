/**
 * Small centered loading indicator. `role="status"` makes screen readers announce the label.
 */
export default function LoadingSpinner({ label = 'Loading...', fullPage = false }) {
  return (
    <div className={`spinner${fullPage ? ' spinner--full-page' : ''}`} role="status" aria-live="polite">
      <span className="spinner__circle" aria-hidden="true" />
      <span className="spinner__label">{label}</span>
    </div>
  );
}
