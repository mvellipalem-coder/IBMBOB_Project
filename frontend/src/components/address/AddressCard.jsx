import AddressDisplay from './AddressDisplay.jsx';

/**
 * A saved address with Edit / Delete buttons. When `selectable` is true it is also a radio option
 * (used on the checkout page), grouped by `name`.
 */
export default function AddressCard({
  address,
  selectable = false,
  selected = false,
  onSelect,
  onEdit,
  onDelete,
  name = 'address',
}) {
  const inputId = `${name}-${address.id}`;
  return (
    <div className={`address-card${selected ? ' address-card--selected' : ''}`}>
      {selectable && (
        <input
          id={inputId}
          type="radio"
          name={name}
          className="address-card__radio"
          checked={selected}
          onChange={() => onSelect(address)}
        />
      )}
      <div className="address-card__content">
        {selectable ? (
          <label htmlFor={inputId} className="address-card__label">
            <AddressDisplay address={address} />
          </label>
        ) : (
          <AddressDisplay address={address} />
        )}
        {address.isDefault && <span className="badge badge--info">Default</span>}
        {(onEdit || onDelete) && (
          <div className="address-card__actions">
            {onEdit && (
              <button
                type="button"
                className="btn btn--link"
                onClick={() => onEdit(address)}
                aria-label={`Edit address for ${address.recipientName}, ${address.line1}`}
              >
                Edit
              </button>
            )}
            {onDelete && (
              <button
                type="button"
                className="btn btn--link btn--link-danger"
                onClick={() => onDelete(address)}
                aria-label={`Delete address for ${address.recipientName}, ${address.line1}`}
              >
                Delete
              </button>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
