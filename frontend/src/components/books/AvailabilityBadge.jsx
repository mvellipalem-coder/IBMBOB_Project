/** "In stock" / "Out of stock" label. Text is always present, color is only a hint. */
export default function AvailabilityBadge({ inStock }) {
  return (
    <span className={`badge ${inStock ? 'badge--success' : 'badge--danger'}`}>
      {inStock ? 'In stock' : 'Out of stock'}
    </span>
  );
}
