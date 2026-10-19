/**
 * Creates a random Idempotency-Key (UUID v4, 36 characters, allowed by the API's 16-64 character rule).
 * Create it once per user action and reuse it when retrying that same action.
 */
export function newIdempotencyKey() {
  if (globalThis.crypto?.randomUUID) {
    return globalThis.crypto.randomUUID();
  }
  // Fallback for non-secure contexts, still built from the cryptographic random generator.
  const bytes = new Uint8Array(16);
  globalThis.crypto.getRandomValues(bytes);
  return Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');
}
