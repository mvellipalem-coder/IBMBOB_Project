import { useRef, useState } from 'react';
import { fieldErrorsToMap, validateAddress } from '../../utils/validation.js';
import { focusFirstInvalid } from '../../utils/dom.js';
import ErrorMessage from '../common/ErrorMessage.jsx';
import FormField from '../common/FormField.jsx';

const EMPTY_ADDRESS = {
  recipientName: '',
  phone: '',
  line1: '',
  line2: '',
  city: '',
  state: '',
  postalCode: '',
  countryCode: 'IN',
  isDefault: false,
};

/**
 * Create / edit form for a delivery address.
 * `onSubmit(values)` must return a promise. If it rejects with an ApiError, server field errors are shown
 * next to the matching inputs and any other message is shown at the top of the form.
 */
export default function AddressForm({ initialValues, onSubmit, onCancel, submitLabel = 'Save address' }) {
  const [values, setValues] = useState({ ...EMPTY_ADDRESS, ...initialValues, line2: initialValues?.line2 ?? '' });
  const [errors, setErrors] = useState({});
  const [submitError, setSubmitError] = useState(null);
  const [saving, setSaving] = useState(false);
  const formRef = useRef(null);

  const setField = (name) => (event) => {
    const value = event.target.type === 'checkbox' ? event.target.checked : event.target.value;
    setValues((previous) => ({ ...previous, [name]: value }));
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    const found = validateAddress(values);
    setErrors(found);
    setSubmitError(null);
    if (Object.keys(found).length > 0) {
      focusFirstInvalid(formRef.current);
      return;
    }
    const payload = {
      recipientName: values.recipientName.trim(),
      phone: values.phone.trim(),
      line1: values.line1.trim(),
      line2: values.line2.trim() || null,
      city: values.city.trim(),
      state: values.state.trim(),
      postalCode: values.postalCode.trim(),
      countryCode: values.countryCode.trim().toUpperCase(),
      isDefault: values.isDefault,
    };
    setSaving(true);
    try {
      await onSubmit(payload);
    } catch (error) {
      const serverErrors = fieldErrorsToMap(error.fieldErrors);
      if (Object.keys(serverErrors).length > 0) {
        setErrors(serverErrors);
        focusFirstInvalid(formRef.current);
      } else {
        setSubmitError(error);
      }
    } finally {
      setSaving(false);
    }
  };

  return (
    <form ref={formRef} className="form address-form" onSubmit={handleSubmit} noValidate>
      {submitError && <ErrorMessage error={submitError} title="Could not save the address" />}
      <p className="form__note">Fields marked * are required.</p>

      <div className="form__grid">
        <FormField
          id="address-recipient"
          label="Recipient name"
          value={values.recipientName}
          onChange={setField('recipientName')}
          error={errors.recipientName}
          autoComplete="name"
          maxLength={100}
          required
        />
        <FormField
          id="address-phone"
          label="Phone"
          type="tel"
          value={values.phone}
          onChange={setField('phone')}
          error={errors.phone}
          hint="Digits, spaces and + - ( ) only."
          autoComplete="tel"
          maxLength={20}
          required
        />
      </div>
      <FormField
        id="address-line1"
        label="Address line 1"
        value={values.line1}
        onChange={setField('line1')}
        error={errors.line1}
        autoComplete="address-line1"
        maxLength={150}
        required
      />
      <FormField
        id="address-line2"
        label="Address line 2"
        value={values.line2}
        onChange={setField('line2')}
        error={errors.line2}
        autoComplete="address-line2"
        maxLength={150}
      />
      <div className="form__grid">
        <FormField
          id="address-city"
          label="City"
          value={values.city}
          onChange={setField('city')}
          error={errors.city}
          autoComplete="address-level2"
          maxLength={80}
          required
        />
        <FormField
          id="address-state"
          label="State"
          value={values.state}
          onChange={setField('state')}
          error={errors.state}
          autoComplete="address-level1"
          maxLength={80}
          required
        />
        <FormField
          id="address-postal"
          label="Postal code"
          value={values.postalCode}
          onChange={setField('postalCode')}
          error={errors.postalCode}
          autoComplete="postal-code"
          maxLength={12}
          required
        />
        <FormField
          id="address-country"
          label="Country code"
          value={values.countryCode}
          onChange={setField('countryCode')}
          error={errors.countryCode}
          hint="2 letters, for example IN."
          autoComplete="country"
          maxLength={2}
          required
        />
      </div>

      <div className="form-check">
        <input
          id="address-default"
          type="checkbox"
          checked={values.isDefault}
          onChange={setField('isDefault')}
        />
        <label htmlFor="address-default">Use as my default address</label>
      </div>

      <div className="form__actions">
        {onCancel && (
          <button type="button" className="btn btn--secondary" onClick={onCancel} disabled={saving}>
            Cancel
          </button>
        )}
        <button type="submit" className="btn btn--primary" disabled={saving}>
          {saving ? 'Saving...' : submitLabel}
        </button>
      </div>
    </form>
  );
}
