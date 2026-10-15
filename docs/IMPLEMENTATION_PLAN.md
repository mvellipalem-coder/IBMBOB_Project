# EBookStore - Implementation Plan (confirmed)

## Assumptions
- A1 Roles: `CUSTOMER`, `ADMIN` (admin APIs only, no admin UI in v1).
- A2 A book has one category, one publisher, one or more authors.
- A3 Single currency; prices `NUMERIC(10,2)`.
- A4 Payment is simulated (`CARD`, `UPI`, `COD`, `GIFT_POINTS`). No card data is collected or stored.
- A5 Gift points: earn 1 point per 10 currency units on delivery; 1 point = 1 unit; max 50% of order value payable with points; reversed on cancellation.
- A6 Order status: `PLACED -> CONFIRMED -> PACKED -> SHIPPED -> DELIVERED`, or `CANCELLED`.
- A7 Cancel only if status is `PLACED`/`CONFIRMED`/`PACKED` and less than 48h since `placedAt`. Restores stock, reverses points, simulated refund.
- A8 Tentative delivery = order date + 5 days (+2 for remote pincodes), configurable.
- A9 Physical books only. A10 Stock decremented at order placement.
- A11 Rule-based recommendations (same category/author/publisher as past orders, excluding bought books).
- A12 Cart stored per user in DB. A13 Local single-node run (UI `localhost:5173`, API `localhost:8080`).

## Open questions
Email verification / password reset (assumed out of scope); admin UI; point expiry (assumed none); partial refunds (assumed full); partial order cancellation (assumed none); seed data size (40-60 books); deployment target (assumed local only).

## Functional requirements
FR-1 Register/login/JWT auth - FR-2 Browse by category/publisher - FR-3 Search, filter, sort, paginate - FR-4 Book details and related books - FR-5 Add to cart - FR-6 Update/remove cart items - FR-7 Address book and selection - FR-8 Payment selection and simulation - FR-9 Gift points redeem/track - FR-10 Order creation - FR-11 Order history and buy again - FR-12 Recommendations - FR-13 Cancellation (48h, status-based) - FR-14 Status tracking and delivery date.

## Non-functional requirements
Usability (responsive, validation messages), performance (<500ms search for ~1000 books, indexes, pagination), reliability (transactional checkout, optimistic locking on stock), maintainability (layered, DTOs, Flyway, consistent errors), testability (>=70% service-layer coverage), portability (env-var config), documentation (OpenAPI + Swagger UI + README), modern-browser compatibility.

## Architecture
React SPA (5173) -> REST/JSON + JWT -> Spring Boot API (127.0.0.1:8080) -> Spring Security -> Controllers -> Services -> Spring Data JPA -> PostgreSQL. Modular monolith, feature-based packages.

```
EBookStore/
  backend/   Spring Boot (Maven) - com.ebookstore.{auth,user,catalog,cart,address,order,payment,points,recommendation,common,config}
  frontend/  React + Vite - src/{pages,components,services,context,styles}
  docs/
```

## Modules
auth, user, catalog, cart, address, payment (mock gateway), points (ledger), order (checkout, history, cancel, tracking), recommendation, common (errors, pagination), frontend.

## Database entities
`users`, `address`, `category`, `publisher`, `author`, `book`, `book_author`, `cart`, `cart_item`, `orders`, `order_item` (price/title snapshot), `payment`, `point_transaction`, `order_status_history`.

Relationships: user 1-N address/orders/point_transaction, user 1-1 cart; category/publisher 1-N book; book N-N author; cart 1-N cart_item; orders 1-N order_item; orders 1-1 payment; orders 1-N order_status_history.

## API modules (`/api/v1`)
- Auth: `POST /auth/register`, `POST /auth/login`, `GET|PUT /users/me`
- Catalog: `GET /books` (q, categoryId, publisherId, minPrice, maxPrice, inStock, sort, page, size), `GET /books/{id}`, `GET /books/{id}/related`, `GET /categories`, `GET /publishers`
- Admin catalogue: `POST|PUT|DELETE /admin/books`, `POST /admin/categories`, `POST /admin/publishers`
- Cart: `GET /cart`, `POST /cart/items`, `PUT|DELETE /cart/items/{itemId}`, `DELETE /cart`
- Address: `GET|POST /addresses`, `PUT|DELETE /addresses/{id}`, `PUT /addresses/{id}/default`
- Points: `GET /points`
- Orders: `POST /orders`, `GET /orders`, `GET /orders/{id}`, `POST /orders/{id}/cancel`, `POST /orders/{id}/reorder`, `GET /orders/{id}/tracking`
- Payment: `GET /payment-methods`
- Recommendations: `GET /recommendations`
- Admin orders: `PUT /admin/orders/{id}/status`

## Security requirements
BCrypt hashing; stateless short-lived JWT (secret from env var); RBAC; all user data scoped to the authenticated user (no IDOR); secrets only via env vars, `.env` git-ignored; API bound to 127.0.0.1; CORS limited to the UI origin; Bean Validation and parameterised queries only; no card data; generic client errors, structured logs without secrets/PII; latest stable dependencies with lock files and vulnerability scans.
Policy note: IBM policy prefers SAML/OIDC. v1 uses email+password+JWT, designed so it can be swapped for OIDC (e.g. Keycloak). To be confirmed with evaluators.

## Phases
0 Setup - 1 Auth - 2 Catalogue - 3 Cart - 4 Address/checkout shell - 5 Payment and points - 6 Orders - 7 Reorder/cancel/recommend - 8 Hardening and tests - 9 Docs and release.
One branch per phase (`feature/<name>`), small commits, one PR per phase.

## Acceptance criteria (summary)
- FR-1: register 201, duplicate email 409, valid login returns JWT, bad credentials 401 generic, protected endpoints 401 without a token.
- FR-2/3: category/publisher filtering is correct; case-insensitive search on title/author/ISBN; combined filters, sorting and pagination work.
- FR-4: all detail fields shown; related books exclude current book, max 6.
- FR-5/6: duplicate adds merge; quantity above stock rejected; subtotal updates; carts are private.
- FR-7: address CRUD, exactly one default, address required at checkout.
- FR-8/9: defined outcome per payment method; failed payment changes no stock/points; balance equals ledger; redemption above balance or 50% cap rejected.
- FR-10: order and items created atomically; stock reduced; cart emptied; totals correct.
- FR-11/12: history shows only own orders newest first; buy again lists unavailable items; recommendations exclude purchased books, fallback for new users.
- FR-13: cancel succeeds within 48h in allowed status (restores stock, reverses points, refund); otherwise 409 with reason.
- FR-14: timeline, current status and tentative delivery date; only valid transitions.
- General: Swagger UI documents every endpoint, all tests pass, README gets a new developer running in <15 min, no secrets in the repo.
