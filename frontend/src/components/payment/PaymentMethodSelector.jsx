export const PAYMENT_METHODS = [
  { value: 'CARD', label: 'Credit / debit card', description: 'Paid securely online. No card details are collected in this demo.' },
  { value: 'UPI', label: 'UPI', description: 'Paid online through UPI. No UPI PIN is collected in this demo.' },
  { value: 'COD', label: 'Cash on delivery', description: 'Pay when your order arrives.' },
];

/** Radio group for choosing how to pay. Uses a fieldset/legend so screen readers announce the group. */
export default function PaymentMethodSelector({ value, onChange, disabled = false, error }) {
  return (
    <fieldset className="payment-methods" disabled={disabled} aria-describedby={error ? 'payment-method-error' : undefined}>
      <legend>Payment method</legend>
      {PAYMENT_METHODS.map((method) => {
        const id = `payment-${method.value}`;
        return (
          <div key={method.value} className={`payment-option${value === method.value ? ' payment-option--selected' : ''}`}>
            <input
              id={id}
              type="radio"
              name="paymentMethod"
              value={method.value}
              checked={value === method.value}
              onChange={() => onChange(method.value)}
              aria-describedby={`${id}-description`}
            />
            <label htmlFor={id}>
              <span className="payment-option__label">{method.label}</span>
              <span id={`${id}-description`} className="payment-option__description">
                {method.description}
              </span>
            </label>
          </div>
        );
      })}
      {error && (
        <p id="payment-method-error" className="form-field__error">
          {error}
        </p>
      )}
    </fieldset>
  );
}
