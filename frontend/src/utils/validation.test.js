import { describe, expect, it } from 'vitest';
import {
  fieldErrorsToMap,
  validateAddress,
  validateLogin,
  validateRegistration,
  validateSearchText,
} from './validation.js';

const validRegistration = {
  fullName: 'Asha Rao',
  email: 'asha@example.com',
  password: 'Passw0rdOK',
  confirmPassword: 'Passw0rdOK',
  phone: '',
};

describe('validateRegistration', () => {
  it('accepts valid input', () => {
    expect(validateRegistration(validRegistration)).toEqual({});
  });

  it.each([
    ['too short', 'Ab1'],
    ['no upper-case letter', 'passw0rdok'],
    ['no lower-case letter', 'PASSW0RDOK'],
    ['no digit', 'PasswordOK'],
  ])('rejects a password with %s', (_label, password) => {
    const errors = validateRegistration({ ...validRegistration, password, confirmPassword: password });
    expect(errors.password).toBeDefined();
  });

  it('rejects mismatching passwords, bad email, short name and bad phone', () => {
    const errors = validateRegistration({
      fullName: 'A',
      email: 'nope',
      password: 'Passw0rdOK',
      confirmPassword: 'different',
      phone: 'abc',
    });
    expect(Object.keys(errors).sort()).toEqual(['confirmPassword', 'email', 'fullName', 'phone']);
  });
});

describe('validateLogin', () => {
  it('requires a valid email and a password', () => {
    expect(validateLogin({ email: '', password: '' })).toEqual({
      email: expect.any(String),
      password: expect.any(String),
    });
    expect(validateLogin({ email: 'a@b.co', password: 'x' })).toEqual({});
  });
});

describe('validateAddress', () => {
  const valid = {
    recipientName: 'Asha Rao',
    phone: '+91 98765 43210',
    line1: '12 MG Road',
    line2: '',
    city: 'Pune',
    state: 'Maharashtra',
    postalCode: '411001',
    countryCode: 'IN',
  };

  it('accepts a valid address', () => {
    expect(validateAddress(valid)).toEqual({});
  });

  it('flags every missing or malformed field', () => {
    const errors = validateAddress({ ...valid, recipientName: '', phone: '12', city: ' ', postalCode: '#', countryCode: 'India' });
    expect(Object.keys(errors).sort()).toEqual(['city', 'countryCode', 'phone', 'postalCode', 'recipientName']);
  });
});

describe('validateSearchText', () => {
  it('allows empty text, rejects one character, accepts two', () => {
    expect(validateSearchText('')).toBeNull();
    expect(validateSearchText('a')).toMatch(/at least 2/i);
    expect(validateSearchText('ab')).toBeNull();
  });
});

describe('fieldErrorsToMap', () => {
  it('maps backend field errors by field name, keeping the first message', () => {
    expect(
      fieldErrorsToMap([
        { field: 'email', message: 'first' },
        { field: 'email', message: 'second' },
        { field: 'quantity', message: 'bad' },
      ]),
    ).toEqual({ email: 'first', quantity: 'bad' });
    expect(fieldErrorsToMap(undefined)).toEqual({});
  });
});
