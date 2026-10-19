import { useEffect, useRef } from 'react';

/**
 * Modal confirmation built on the native <dialog> element, which gives focus trapping,
 * Escape-to-close and correct screen-reader semantics without extra code.
 */
export default function ConfirmationDialog({
  open,
  title,
  message,
  confirmLabel = 'Confirm',
  cancelLabel = 'Cancel',
  onConfirm,
  onCancel,
  busy = false,
  danger = false,
  children,
}) {
  const dialogRef = useRef(null);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);

  return (
    <dialog
      ref={dialogRef}
      className="dialog"
      aria-labelledby="dialog-title"
      aria-describedby="dialog-message"
      onCancel={(event) => {
        event.preventDefault(); // we control closing through the `open` prop
        if (!busy) onCancel();
      }}
    >
      <h2 id="dialog-title" className="dialog__title">
        {title}
      </h2>
      <p id="dialog-message" className="dialog__message">
        {message}
      </p>
      {children}
      <div className="dialog__actions">
        <button type="button" className="btn btn--secondary" onClick={onCancel} disabled={busy}>
          {cancelLabel}
        </button>
        <button
          type="button"
          className={`btn ${danger ? 'btn--danger' : 'btn--primary'}`}
          onClick={onConfirm}
          disabled={busy}
        >
          {busy ? 'Please wait...' : confirmLabel}
        </button>
      </div>
    </dialog>
  );
}
