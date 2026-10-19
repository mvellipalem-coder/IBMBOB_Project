// Client-side checks give quick feedback only. The server validates everything again and is the authority.
// Rules mirror backend/src/main/resources/openapi.yaml.

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PHONE_PATTERN = /^[0-9+() -]{7,20}$/;
const POSTAL_CODE_PATTERN = /^[A-Za-z0-9 -]{3,12}$/;
const COUNTRY_PATTERN = /^[A-Z]{2}$/;

function required(value) {
  return typeof value === 'string' ? value.trim().length > 0 : value !== undefined && value !== null;
}

/** Returns { field: message } for every invalid field; an empty object means valid. */
export function validateRegistration({ fullName, email, password, confirmPassword, phone }) {
  const errors = {};
  const name = (fullName ?? '').trim();
  if (name.length < 2 || name.length > 100) errors.fullName = 'Enter your full name (2 to 100 characters).';
  if (!EMAIL_PATTERN.test((email ?? '').trim()) || email.trim().length > 254) {
    errors.email = 'Enter a valid email address.';
  }
  if (!password || password.length < 8 || password.length > 72) {
    errors.password = 'Password must be 8 to 72 characters.';
  } else if (!/[a-z]/.test(password) || !/[A-Z]/.test(password) || !/[0-9]/.test(password)) {
    errors.password = 'Password needs a lower-case letter, an upper-case letter and a digit.';
  }
  if (confirmPassword !== password) errors.confirmPassword = 'Passwords do not match.';
  if (required(phone) && !PHONE_PATTERN.test(phone.trim())) {
    errors.phone = 'Use 7 to 20 digits, spaces or the characters + - ( ).';
  }
  return errors;
}

export function validateLogin({ email, password }) {
  const errors = {};
  if (!EMAIL_PATTERN.test((email ?? '').trim())) errors.email = 'Enter a valid email address.';
  if (!password) errors.password = 'Enter your password.';
  return errors;
}

export function validateAddress(values) {
  const errors = {};
  const { recipientName, phone, line1, line2, city, state, postalCode, countryCode } = values;
  if (!required(recipientName)) errors.recipientName = 'Enter the recipient name.';
  else if (recipientName.trim().length > 100) errors.recipientName = 'Use at most 100 characters.';
  if (!PHONE_PATTERN.test((phone ?? '').trim())) {
    errors.phone = 'Use 7 to 20 digits, spaces or the characters + - ( ).';
  }
  if (!required(line1)) errors.line1 = 'Enter the street address.';
  else if (line1.trim().length > 150) errors.line1 = 'Use at most 150 characters.';
  if (required(line2) && line2.trim().length > 150) errors.line2 = 'Use at most 150 characters.';
  if (!required(city)) errors.city = 'Enter the city.';
  else if (city.trim().length > 80) errors.city = 'Use at most 80 characters.';
  if (!required(state)) errors.state = 'Enter the state.';
  else if (state.trim().length > 80) errors.state = 'Use at most 80 characters.';
  if (!POSTAL_CODE_PATTERN.test((postalCode ?? '').trim())) {
    errors.postalCode = 'Use 3 to 12 letters, digits, spaces or hyphens.';
  }
  if (!COUNTRY_PATTERN.test((countryCode ?? '').trim().toUpperCase())) {
    errors.countryCode = 'Use a 2-letter country code, for example IN.';
  }
  return errors;
}

/** Search text must be 2 to 100 characters after trimming (server rule). */
export function validateSearchText(text) {
  const value = (text ?? '').trim();
  if (value.length === 0) return null;
  if (value.length < 2) return 'Type at least 2 characters to search.';
  if (value.length > 100) return 'Search text can have at most 100 characters.';
  return null;
}

/** Maps backend `fieldErrors` ([{ field, message }]) to { field: message } for form display. */
export function fieldErrorsToMap(fieldErrors) {
  const map = {};
  (fieldErrors ?? []).forEach(({ field, message }) => {
    if (field && !map[field]) map[field] = message;
  });
  return map;
}
