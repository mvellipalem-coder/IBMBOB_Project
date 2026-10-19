import { useState } from 'react';
import FormField from '../common/FormField.jsx';

const MONEY_PATTERN = /^\d{1,8}(\.\d{1,2})?$/;

/** Min / max price form. Calls `onApply({ minPrice, maxPrice })` with strings ('' means no limit). */
export default function PriceFilter({ minPrice = '', maxPrice = '', onApply }) {
  const [min, setMin] = useState(minPrice);
  const [max, setMax] = useState(maxPrice);
  const [errors, setErrors] = useState({});

  const handleSubmit = (event) => {
    event.preventDefault();
    const found = {};
    if (min !== '' && !MONEY_PATTERN.test(min)) found.min = 'Enter an amount such as 250 or 250.50.';
    if (max !== '' && !MONEY_PATTERN.test(max)) found.max = 'Enter an amount such as 800 or 800.50.';
    if (!found.min && !found.max && min !== '' && max !== '' && Number(min) > Number(max)) {
      found.min = 'Minimum price cannot be higher than the maximum.';
    }
    setErrors(found);
    if (Object.keys(found).length === 0) onApply({ minPrice: min, maxPrice: max });
  };

  return (
    <form className="price-filter" onSubmit={handleSubmit} noValidate>
      <fieldset>
        <legend>Price range</legend>
        <div className="price-filter__fields">
          <FormField
            id="price-min"
            label="Min"
            placeholder="Min price"
            type="text"
            inputMode="decimal"
            value={min}
            onChange={(event) => setMin(event.target.value.trim())}
            error={errors.min}
          />
          <FormField
            id="price-max"
            label="Max"
            placeholder="Max price"
            type="text"
            inputMode="decimal"
            value={max}
            onChange={(event) => setMax(event.target.value.trim())}
            error={errors.max}
          />
        </div>
        <button type="submit" className="btn btn--secondary btn--small price-filter__apply">
          Apply
        </button>
      </fieldset>
    </form>
  );
}
