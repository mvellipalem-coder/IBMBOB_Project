/**
 * Shows a problem with an optional "Try again" button.
 * `error` may be an ApiError, an Error or a plain string. Messages come from the server and never contain
 * stack traces or internal details.
 */
export default function ErrorMessage({ error, title = 'Something went wrong', onRetry }) {
  const message =
    typeof error === 'string' ? error : error?.message || 'An unexpected error occurred. Please try again.';
  return (
    <div className="error-message" role="alert">
      <p className="error-message__title">{title}</p>
      <p className="error-message__text">{message}</p>
      {onRetry && (
        <button type="button" className="btn btn--secondary btn--small" onClick={onRetry}>
          Try again
        </button>
      )}
    </div>
  );
}
