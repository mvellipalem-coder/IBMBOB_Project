# Frontend integration

How the React app (`frontend/`) talks to the Spring Boot API, what is covered, and what the backend still lacks.
The contract is [`backend/src/main/resources/openapi.yaml`](../backend/src/main/resources/openapi.yaml).
Nothing in the frontend is mocked at run time: every screen calls the real API.

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `VITE_API_BASE_URL` | `http://localhost:8080/api` | Backend base URL including `/api`. A production build refuses a non-HTTPS URL unless it is localhost. |
| `VITE_REQUEST_TIMEOUT_MS` | `15000` | Request timeout. |
| `VITE_CURRENCY`, `VITE_LOCALE` | `INR`, `en-IN` | Money and date formatting only. |

Copy [`frontend/.env.example`](../frontend/.env.example) to `.env.local`. Only `VITE_` variables reach the browser, so never put secrets in them.
The backend must allow the UI origin through `CORS_ALLOWED_ORIGIN` (already `http://localhost:5173` by default).

## Architecture

| Layer | Location | Notes |
|---|---|---|
| API client | `src/services/httpClient.js` | One `fetch` wrapper: base URL, Bearer token, timeout, cancellation, `ApiError` with `status`, `code`, `fieldErrors`. |
| API services | `src/services/*Service.js` | One file per API area; field names match the DTOs in `openapi.yaml`. |
| Session | `src/services/authStorage.js`, `src/context/AuthContext.jsx` | See "Authentication" below. |
| Cart state | `src/context/CartContext.jsx` | Holds the server cart; every change returns the server's cart. |
| Pages / components | `src/pages`, `src/components` | Pages only compose components and call services. |

## Authentication token strategy

- The API issues a short-lived JWT in the response body (default 30 minutes, `expiresIn`). There is no refresh token and no cookie, so the SPA must hold the token itself.
- The token and public profile are kept in `sessionStorage` (cleared when the tab closes, not shared between tabs) and in memory. Nothing is placed in `localStorage`, and no long-lived credential exists to store.
- Passwords exist only in form state while typing and are sent once to `POST /api/auth/login`. No card, CVV or UPI data is collected anywhere.
- The session ends on logout, on expiry (timer), and on any `401` for a request that carried a token. The user is then sent to the login page with a notice.
- On page load a stored session is re-validated with `GET /api/users/me`.
- Route guards (`ProtectedRoute`) only improve usability. The backend enforces authorization on every request.
- Residual risk: any XSS flaw could read `sessionStorage`. Mitigations in place: React escaping, no `dangerouslySetInnerHTML`, only `http(s)` image URLs, short token life. For production, serve the app with a strict Content-Security-Policy and consider moving to an `HttpOnly` `SameSite` cookie (BFF or backend change) or OIDC, which the security policy prefers.

## Purchase safety

- Checkout sends only `addressId`, `paymentMethod` and `pointsToRedeem`. Prices and totals are never sent.
- All amounts shown (cart, order, payment, confirmation) are the values the server returned. The browser does no price arithmetic.
- `Idempotency-Key` (UUID v4) is generated once per distinct checkout or payment attempt and reused on retry, so a timeout cannot create a second order or charge twice.
- Success is shown only from server state: card/UPI orders show "confirmed" only when `payment.status` is `SUCCESS`; COD shows "placed" only after the order exists. A declined payment shows the failure and never a success message. An unpaid card order shows "Payment still needed".

## Coverage of the approved contract

| Feature | Endpoint(s) | Screen |
|---|---|---|
| Register, login | `POST /auth/register`, `POST /auth/login` | Register, Login |
| Current user | `GET /users/me` | Session re-validation |
| Catalogue, search, filters, sort, paging | `GET /books`, `GET /books/search`, `GET /categories`, `GET /publishers` | Catalogue, Home |
| Book details, related | `GET /books/{id}`, `GET /books/{id}/related` | Book details |
| Cart | `GET/POST /cart`, `PUT/DELETE /cart/items/{itemId}` | Cart |
| Addresses | `GET/POST /addresses`, `PUT/DELETE /addresses/{id}` | Addresses, Checkout |
| Checkout, gift points | `POST /checkout`, `GET /gift-points/balance` | Checkout |
| Payment | `POST /payments/{orderId}/process` | Payment |
| Orders | `GET /orders`, `GET /orders/{id}`, `POST /orders/{id}/cancel`, `POST /orders/{id}/buy-again` | History, Details, Confirmation |
| Recommendations | `GET /recommendations` | Recommendations |

## Backend endpoints that are still missing

These appear in [`IMPLEMENTATION_PLAN.md`](IMPLEMENTATION_PLAN.md) or are needed by the UI, but do not exist in the contract or controllers. The frontend does **not** fake them.

| Missing endpoint | Effect on the UI |
|---|---|
| `PUT /api/users/me` (edit profile) | No profile-edit page. |
| `PUT /api/addresses/{id}/default` | Default address is changed by editing an address and ticking "default" (`isDefault` in `PUT /api/addresses/{id}`), which the contract supports. |
| `PUT /api/admin/orders/{id}/status` (and any status advance) | Orders stay `PLACED`; later statuses and "points earned on delivery" can only appear if set directly in the database. |
| `POST/PUT/DELETE /api/admin/books`, `/admin/categories`, `/admin/publishers` | No catalogue administration. |
| `GET /api/gift-points/transactions` is implemented but unused | No points-history page yet (not requested). |
| Book cover images | `imageUrl` is `null` in the seed data, so a generated title cover is shown. |
| Logout / token revocation endpoint | Logout only forgets the token locally; the JWT stays valid until it expires (max 30 minutes). |
| Refresh-token endpoint | The user must log in again after the token expires. |
| `GET /api/orders/{id}/tracking` (planned) | Not needed: tracking comes from `statusHistory` in `GET /orders/{id}`. |

## Tests

```powershell
cd frontend
npm install
npm test
```

119 tests (Vitest, Testing Library) cover: the HTTP client (token, errors, network failure, 401 handling), session storage rules, form validation, and components (book card, category sidebar, breadcrumbs, add-to-cart toast, cart item stepper, order summary, address form, dialog, payment selector).
User journeys run the real app and router against a fake `fetch` (`src/test/mockBackend.js`) and check the exact requests sent: registration and login, protected routes, expired sessions, landing page sections, browsing and filtering, book details, cart changes, addresses, checkout with and without payment, declined payments, idempotent retries, order history, cancellation, buy again (from the list and the details page), and recommendations.

The UI follows the capstone wireframes (dark "Book Worm" theme). Which wireframe items map to which page and endpoint, and which cannot be built yet, is in [WIREFRAME_TRACEABILITY.md](WIREFRAME_TRACEABILITY.md).
