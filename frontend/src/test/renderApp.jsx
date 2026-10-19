import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import App from '../App.jsx';
import { AuthProvider } from '../context/AuthContext.jsx';
import { CartProvider } from '../context/CartContext.jsx';
import { saveSession } from '../services/authStorage.js';
import { user } from './fixtures.js';

/** Puts a valid session in sessionStorage, as if the user had logged in earlier in this tab. */
export function signIn() {
  saveSession({ accessToken: 'test.jwt.token', user, expiresAt: Date.now() + 30 * 60 * 1000 });
}

/** Renders the whole app (real router, providers and pages) at the given URL. */
export function renderApp(initialPath = '/') {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <AuthProvider>
        <CartProvider>
          <App />
        </CartProvider>
      </AuthProvider>
    </MemoryRouter>,
  );
}

/** Renders one component inside the providers it needs. */
export function renderWithProviders(ui, { route = '/' } = {}) {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <AuthProvider>
        <CartProvider>{ui}</CartProvider>
      </AuthProvider>
    </MemoryRouter>,
  );
}
