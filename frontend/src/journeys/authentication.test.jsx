import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { getAccessToken } from '../services/authStorage.js';
import { authResponse, user } from '../test/fixtures.js';
import { apiError, mockBackend } from '../test/mockBackend.js';
import { renderApp, signIn } from '../test/renderApp.jsx';

describe('Journey: registration and login', () => {
  it('registers a customer, then sends them to log in (registration does not log in)', async () => {
    const backend = mockBackend({
      'POST /api/auth/register': { status: 201, body: user },
    });
    renderApp('/register');

    await userEvent.type(screen.getByLabelText(/Full name/), 'Asha Rao');
    await userEvent.type(screen.getByLabelText(/^Email/), 'asha.rao@example.com');
    await userEvent.type(screen.getByLabelText(/^Password/), 'Passw0rdOK');
    await userEvent.type(screen.getByLabelText(/Confirm password/), 'Passw0rdOK');
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }));

    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument();
    expect(screen.getByText(/Your account was created/)).toBeInTheDocument();
    expect(screen.getByLabelText(/^Email/)).toHaveValue('asha.rao@example.com');
    expect(backend.callsTo('POST /api/auth/register')[0].body).toEqual({
      fullName: 'Asha Rao',
      email: 'asha.rao@example.com',
      password: 'Passw0rdOK',
    });
    expect(getAccessToken()).toBeNull();
  });

  it('shows the server message when the email is already registered', async () => {
    mockBackend({
      'POST /api/auth/register': apiError(409, 'EMAIL_ALREADY_REGISTERED', 'This email address is already registered.'),
    });
    renderApp('/register');
    await userEvent.type(screen.getByLabelText(/Full name/), 'Asha Rao');
    await userEvent.type(screen.getByLabelText(/^Email/), 'asha.rao@example.com');
    await userEvent.type(screen.getByLabelText(/^Password/), 'Passw0rdOK');
    await userEvent.type(screen.getByLabelText(/Confirm password/), 'Passw0rdOK');
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }));
    expect(await screen.findByText('This email address is already registered.')).toBeInTheDocument();
  });

  it('does not call the backend when the form is invalid', async () => {
    const backend = mockBackend({});
    renderApp('/register');
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }));
    expect(await screen.findByText('Enter a valid email address.')).toBeInTheDocument();
    expect(backend).not.toHaveBeenCalled();
  });

  it('logs in only after the backend returns a token, and keeps no password', async () => {
    mockBackend({
      'POST /api/auth/login': { body: authResponse },
      'GET /api/cart': { body: { items: [], totalQuantity: 0, subtotal: 0 } },
      'GET /api/books': { body: { page: 0, size: 8, totalElements: 0, totalPages: 0, first: true, last: true, content: [] } },
      'GET /api/categories': { body: [] },
    });
    renderApp('/login');
    await userEvent.type(screen.getByLabelText(/^Email/), 'asha.rao@example.com');
    await userEvent.type(screen.getByLabelText(/^Password/), 'Passw0rdOK');
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }));

    expect(await screen.findByText(/Hi, Asha/)).toBeInTheDocument();
    expect(getAccessToken()).toBe('test.jwt.token');
    expect(window.sessionStorage.getItem('ebookstore.session')).not.toContain('Passw0rdOK');
    expect(window.localStorage.length).toBe(0);
  });

  it('stays logged out and shows a generic message for bad credentials', async () => {
    mockBackend({
      'POST /api/auth/login': apiError(401, 'INVALID_CREDENTIALS', 'Invalid email or password.'),
    });
    renderApp('/login');
    await userEvent.type(screen.getByLabelText(/^Email/), 'asha.rao@example.com');
    await userEvent.type(screen.getByLabelText(/^Password/), 'wrong');
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password.');
    expect(getAccessToken()).toBeNull();
    expect(screen.queryByText(/Hi, /)).not.toBeInTheDocument();
  });

  it('tells the user when the server cannot be reached', async () => {
    mockBackend({ 'POST /api/auth/login': { networkError: true } });
    renderApp('/login');
    await userEvent.type(screen.getByLabelText(/^Email/), 'asha.rao@example.com');
    await userEvent.type(screen.getByLabelText(/^Password/), 'Passw0rdOK');
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }));
    expect(await screen.findByText(/Unable to reach the server/)).toBeInTheDocument();
    expect(getAccessToken()).toBeNull();
  });
});

describe('Journey: protected pages and sessions', () => {
  it.each(['/cart', '/checkout', '/addresses', '/orders', '/orders/15', '/orders/15/payment', '/orders/15/confirmation', '/recommendations'])(
    'redirects a signed-out visitor from %s to the login page without calling the API',
    async (path) => {
      const backend = mockBackend({});
      renderApp(path);
      expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument();
      expect(backend).not.toHaveBeenCalled();
    },
  );

  it('returns to the originally requested page after login', async () => {
    mockBackend({
      'POST /api/auth/login': { body: authResponse },
      'GET /api/cart': { body: { items: [], totalQuantity: 0, subtotal: 0 } },
    });
    renderApp('/cart');
    await userEvent.type(await screen.findByLabelText(/^Email/), 'asha.rao@example.com');
    await userEvent.type(screen.getByLabelText(/^Password/), 'Passw0rdOK');
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }));
    expect(await screen.findByText('Your cart is empty')).toBeInTheDocument();
  });

  it('ends the session and asks to log in again when the server rejects the token', async () => {
    signIn();
    mockBackend({
      'GET /api/cart': apiError(401, 'UNAUTHORIZED', 'Authentication is required.'),
    });
    renderApp('/cart');
    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument();
    expect(screen.getByText(/Your session has ended/)).toBeInTheDocument();
    expect(getAccessToken()).toBeNull();
  });

  it('re-checks a restored session with GET /api/users/me and refreshes the profile', async () => {
    signIn();
    const backend = mockBackend({
      'GET /api/users/me': { body: { ...user, fullName: 'Asha Updated' } },
      'GET /api/cart': { body: { items: [], totalQuantity: 0, subtotal: 0 } },
      'GET /api/recommendations': { body: { strategy: 'POPULAR', books: [] } },
      'GET /api/categories': { body: [] },
      'GET /api/books': { body: { page: 0, size: 8, totalElements: 0, totalPages: 0, first: true, last: true, content: [] } },
    });
    renderApp('/');
    expect(await screen.findByText(/Hi, Asha/)).toBeInTheDocument();
    await waitFor(() => expect(backend.callsTo('GET /api/users/me')).toHaveLength(1));
    expect(backend.callsTo('GET /api/users/me')[0].headers.Authorization).toBe('Bearer test.jwt.token');
  });

  it('drops a restored session the server no longer accepts', async () => {
    signIn();
    mockBackend({
      'GET /api/users/me': apiError(401, 'UNAUTHORIZED', 'Authentication is required.'),
      'GET /api/cart': apiError(401, 'UNAUTHORIZED', 'Authentication is required.'),
      'GET /api/recommendations': apiError(401, 'UNAUTHORIZED', 'Authentication is required.'),
      'GET /api/categories': { body: [] },
      'GET /api/books': { body: { page: 0, size: 8, totalElements: 0, totalPages: 0, first: true, last: true, content: [] } },
    });
    renderApp('/');
    expect((await screen.findAllByRole('link', { name: 'Log in' })).length).toBeGreaterThan(0);
    expect(getAccessToken()).toBeNull();
  });

  it('keeps the session when the profile check fails only because the server is unreachable', async () => {
    signIn();
    mockBackend({
      'GET /api/users/me': { networkError: true },
      'GET /api/cart': { body: { items: [], totalQuantity: 0, subtotal: 0 } },
      'GET /api/recommendations': { body: { strategy: 'POPULAR', books: [] } },
      'GET /api/categories': { body: [] },
      'GET /api/books': { body: { page: 0, size: 8, totalElements: 0, totalPages: 0, first: true, last: true, content: [] } },
    });
    renderApp('/');
    expect(await screen.findByText(/Hi, Asha/)).toBeInTheDocument();
    expect(getAccessToken()).toBe('test.jwt.token');
  });

  it('logs out and clears the stored session', async () => {
    signIn();
    mockBackend({
      'GET /api/cart': { body: { items: [], totalQuantity: 0, subtotal: 0 } },
      'GET /api/recommendations': { body: { strategy: 'POPULAR', books: [] } },
      'GET /api/categories': { body: [] },
      'GET /api/books': { body: { page: 0, size: 8, totalElements: 0, totalPages: 0, first: true, last: true, content: [] } },
    });
    renderApp('/');
    await userEvent.click(await screen.findByRole('button', { name: 'Log out' }));
    await waitFor(() => expect(getAccessToken()).toBeNull());
    expect(screen.getAllByRole('link', { name: 'Log in' }).length).toBeGreaterThan(0);
  });
});
