/** Inline feedback banner. `type` is one of: success, info, warning, error. */
export default function Notice({ type = 'info', children, onDismiss }) {
  const role = type === 'warning' || type === 'error' ? 'alert' : 'status';
  return (
    <div className={`notice notice--${type}`} role={role}>
      <div className="notice__body">{children}</div>
      {onDismiss && (
        <button type="button" className="notice__close" onClick={onDismiss} aria-label="Dismiss message">
          &times;
        </button>
      )}
    </div>
  );
}
