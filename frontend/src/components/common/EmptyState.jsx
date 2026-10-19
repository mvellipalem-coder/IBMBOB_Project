/** Friendly "nothing here" panel. Pass a call-to-action as children. */
export default function EmptyState({ title, message, children }) {
  return (
    <section className="empty-state" aria-label={title}>
      <h2 className="empty-state__title">{title}</h2>
      {message && <p className="empty-state__text">{message}</p>}
      {children && <div className="empty-state__actions">{children}</div>}
    </section>
  );
}
