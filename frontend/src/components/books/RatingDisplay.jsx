/** Star rating shown as text plus stars, e.g. "4.5 (12)". The text makes it readable by screen readers. */
export default function RatingDisplay({ average, count }) {
  const value = Number(average) || 0;
  const filled = Math.round(value);
  return (
    <p className="rating" aria-label={`Rated ${value.toFixed(1)} out of 5 from ${count} reviews`}>
      <span className="rating__stars" aria-hidden="true">
        {'\u2605'.repeat(filled)}
        {'\u2606'.repeat(5 - filled)}
      </span>
      <span className="rating__text" aria-hidden="true">
        {value.toFixed(1)} ({count})
      </span>
    </p>
  );
}
