import { describe, expect, it } from 'vitest';
import { formatAuthors, formatDate, formatMoney, humanize } from './format.js';
import { newIdempotencyKey } from './idempotency.js';

describe('format helpers', () => {
  it('formats money from numbers and decimal strings', () => {
    expect(formatMoney(899)).toContain('899.00');
    expect(formatMoney('1698.50')).toContain('1,698.50');
    expect(formatMoney(undefined)).toBe('-');
  });

  it('formats dates and tolerates missing values', () => {
    expect(formatDate('2026-10-20')).toMatch(/2026/);
    expect(formatDate(null)).toBe('-');
    expect(formatDate('not a date')).toBe('-');
  });

  it('humanizes enum values', () => {
    expect(humanize('ORDER_NOT_PAYABLE')).toBe('Order not payable');
    expect(humanize('PACKED')).toBe('Packed');
  });

  it('joins authors', () => {
    expect(formatAuthors(['A', 'B'])).toBe('A, B');
    expect(formatAuthors([])).toBe('Unknown author');
  });
});

describe('newIdempotencyKey', () => {
  it('creates unique keys that satisfy the API rule (16-64 chars of A-Z a-z 0-9 _ -)', () => {
    const a = newIdempotencyKey();
    const b = newIdempotencyKey();
    expect(a).not.toBe(b);
    expect(a).toMatch(/^[A-Za-z0-9_-]{16,64}$/);
  });
});
