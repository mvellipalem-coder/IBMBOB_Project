import { describe, expect, it } from 'vitest';
import { clearSession, getAccessToken, getSession, saveSession } from './authStorage.js';

describe('authStorage', () => {
  it('keeps the session in sessionStorage and never in localStorage', () => {
    saveSession({ accessToken: 'tok', user: { id: 1 }, expiresAt: Date.now() + 60000 });
    expect(window.sessionStorage.getItem('ebookstore.session')).toContain('tok');
    expect(window.localStorage.length).toBe(0);
  });

  it('never stores a password or payment details', () => {
    saveSession({ accessToken: 'tok', user: { id: 1, fullName: 'Asha' }, expiresAt: Date.now() + 60000 });
    const stored = window.sessionStorage.getItem('ebookstore.session');
    expect(stored).not.toMatch(/password|card|cvv|upi/i);
  });

  it('returns null and clears the entry once the token has expired', () => {
    saveSession({ accessToken: 'tok', user: {}, expiresAt: Date.now() - 1 });
    expect(getSession()).toBeNull();
    expect(getAccessToken()).toBeNull();
    expect(window.sessionStorage.getItem('ebookstore.session')).toBeNull();
  });

  it('clears the session', () => {
    saveSession({ accessToken: 'tok', user: {}, expiresAt: Date.now() + 60000 });
    clearSession();
    expect(getAccessToken()).toBeNull();
  });
});
