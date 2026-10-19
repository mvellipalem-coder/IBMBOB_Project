import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import * as authService from '../services/authService.js';
import { setUnauthorizedHandler } from '../services/httpClient.js';
import { clearSession, getSession, saveSession } from '../services/authStorage.js';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [session, setSession] = useState(() => getSession());
  const [sessionExpired, setSessionExpired] = useState(false);

  const logout = useCallback(() => {
    clearSession();
    setSession(null);
  }, []);

  // If the server rejects our token, drop the session so the user is sent to the login page.
  useEffect(() => {
    setUnauthorizedHandler(() => {
      clearSession();
      setSession(null);
      setSessionExpired(true);
    });
    return () => setUnauthorizedHandler(null);
  }, []);

  // A session restored from this tab's storage is re-checked with GET /api/users/me. This confirms the
  // server still accepts the token and refreshes the profile. A rejected token (401) is handled by the
  // unauthorized handler above; other failures (for example the server being down) keep the session.
  useEffect(() => {
    const restored = getSession();
    if (!restored) return undefined;
    const controller = new AbortController();
    authService
      .getProfile({ signal: controller.signal })
      .then((profile) => {
        const current = getSession();
        if (controller.signal.aborted || !current) return;
        const refreshed = { ...current, user: profile };
        saveSession(refreshed);
        setSession(refreshed);
      })
      .catch(() => {});
    return () => controller.abort();
    // Only on first load: a fresh login already received the profile from the login response.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Log out automatically when the token's lifetime ends.
  useEffect(() => {
    if (!session) return undefined;
    const remaining = session.expiresAt - Date.now();
    if (remaining <= 0) {
      logout();
      setSessionExpired(true);
      return undefined;
    }
    const timer = setTimeout(() => {
      logout();
      setSessionExpired(true);
    }, Math.min(remaining, 2 ** 31 - 1));
    return () => clearTimeout(timer);
  }, [session, logout]);

  const login = useCallback(async (credentials) => {
    // Real call to the backend. The user is only "logged in" if the server returns a token.
    const response = await authService.login(credentials);
    const next = {
      accessToken: response.accessToken,
      user: response.user,
      expiresAt: Date.now() + response.expiresIn * 1000,
    };
    saveSession(next);
    setSession(next);
    setSessionExpired(false);
    return response.user;
  }, []);

  const register = useCallback((details) => authService.register(details), []);

  const value = useMemo(
    () => ({
      user: session?.user ?? null,
      isAuthenticated: Boolean(session),
      sessionExpired,
      dismissSessionExpired: () => setSessionExpired(false),
      login,
      register,
      logout,
    }),
    [session, sessionExpired, login, register, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside <AuthProvider>.');
  return context;
}
