// Keeps the login session (JWT + public profile) for the current browser tab only.
// sessionStorage is cleared when the tab closes and is not shared between tabs, which limits exposure
// compared with localStorage. The token is short lived (the server sets `expiresIn`).

const STORAGE_KEY = 'ebookstore.session';

function readFromStorage() {
  try {
    const raw = window.sessionStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const session = JSON.parse(raw);
    if (!session?.accessToken || !session.expiresAt || session.expiresAt <= Date.now()) {
      window.sessionStorage.removeItem(STORAGE_KEY);
      return null;
    }
    return session;
  } catch {
    return null;
  }
}

// In-memory copy so the app still works if storage is unavailable.
let current = readFromStorage();

export function getSession() {
  if (current && current.expiresAt <= Date.now()) {
    clearSession();
  }
  return current;
}

export function getAccessToken() {
  return getSession()?.accessToken ?? null;
}

export function saveSession(session) {
  current = session;
  try {
    window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
  } catch {
    // Storage unavailable: keep the session in memory only.
  }
}

export function clearSession() {
  current = null;
  try {
    window.sessionStorage.removeItem(STORAGE_KEY);
  } catch {
    // Nothing to clean up.
  }
}
