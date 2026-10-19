/** Dropdown to filter by publisher. `value` is a publisher id string; '' means all publishers. */
export default function PublisherFilter({ publishers = [], value, onChange, disabled = false }) {
  return (
    <div className="filter">
      <label htmlFor="publisher-filter">Publisher</label>
      <select
        id="publisher-filter"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        disabled={disabled}
      >
        <option value="">All publishers</option>
        {publishers.map((publisher) => (
          <option key={publisher.id} value={publisher.id}>
            {publisher.name}
          </option>
        ))}
      </select>
    </div>
  );
}
