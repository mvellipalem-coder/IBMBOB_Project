# EBookStore REST API — Complete Test Plan

> **Status: NOT EXECUTED — no test in this document is marked as passed.**
> Mark each test `[PASS]` / `[FAIL]` / `[SKIP]` only after you have run it.

---

## Contents

1. [Environment and conventions](#1-environment-and-conventions)
2. [Test tool setup](#2-test-tool-setup)
3. [Global state required before any test](#3-global-state-required-before-any-test)
4. [Test cases by domain](#4-test-cases-by-domain)
   - [AUTH — Registration & Login](#auth--registration--login)
   - [CATALOGUE — Browse, Search, Filters](#catalogue--browse-search-filters)
   - [CART — Create, Update, Remove, Stock](#cart--create-update-remove-stock)
   - [ADDR — Addresses](#addr--addresses)
   - [CHK — Checkout](#chk--checkout)
   - [PAY — Payments](#pay--payments)
   - [ORD — Orders](#ord--orders)
   - [GP — Gift Points](#gp--gift-points)
   - [REC — Recommendations](#rec--recommendations)
5. [Automated test code (Postman / Newman)](#5-automated-test-code-postman--newman)
6. [Checklist for manual execution](#6-checklist-for-manual-execution)

---

## 1. Environment and conventions

| Property | Value |
|---|---|
| Base URL | `http://localhost:8080` |
| Content-Type (all requests with bodies) | `application/json` |
| Auth header | `Authorization: Bearer {{TOKEN}}` |
| Idempotency header (checkout, payment) | `Idempotency-Key: <UUID v4>` |
| Error schema | `ErrorResponse` — always check `code` (machine-readable), not `message` |
| Money precision | 2 decimal places; `subtotal - pointsDiscount = totalAmount` always |
| Password pattern | ≥ 8 chars, ≥ 1 upper, ≥ 1 lower, ≥ 1 digit |
| All IDs | Replace `{{BOOK_ID}}`, `{{ADDRESS_ID}}`, etc., with values obtained in earlier steps |

**Notation**

- `→` means "then assert"
- `DB:` means a direct database query to verify side-effects
- `[ ]` checkboxes are filled in after actual execution

---

## 2. Test tool setup

### Option A — Swagger UI (zero install)

1. Start the backend: `mvn spring-boot:run` inside `backend/`.
2. Navigate to `http://localhost:8080/swagger-ui.html`.
3. Use **Try it out** for each case. Copy the `curl` equivalent for reproducibility.
4. Record the HTTP status and response body in the checklist below.

### Option B — Postman

1. Create a new Workspace → **EBookStore API**.
2. Import the file at `backend/src/main/resources/openapi.yaml` (**File → Import**).
3. Set a collection variable `baseUrl = http://localhost:8080`.
4. Use the **Tests** tab to add assertions (see [Section 5](#5-automated-test-code-postman--newman)).
5. Run via **Collection Runner** or `newman run collection.json -e env.json`.

### Option C — Insomnia

1. **Application → Import → From File** → select `openapi.yaml`.
2. Set environment variable `baseUrl = http://localhost:8080`.
3. Execute requests manually, record results.

### Option D — curl (minimal)

```bash
BASE=http://localhost:8080

# Register
curl -s -X POST $BASE/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"fullName":"Test User","email":"test@example.com","password":"Test-Passw0rd"}'

# Login → copy accessToken
curl -s -X POST $BASE/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"Test-Passw0rd"}'
```

---

## 3. Global state required before any test

Run once before the first test. Each test case notes which of these is a precondition.

| Symbol | What to prepare | How |
|---|---|---|
| `USER_A` | Registered customer | `POST /api/auth/register` with unique email |
| `TOKEN_A` | JWT for USER_A | `POST /api/auth/login` with USER_A credentials |
| `USER_B` | Second customer (for ownership tests) | Same as USER_A, different email |
| `TOKEN_B` | JWT for USER_B | Login as USER_B |
| `BOOK_IN_STOCK` | A book with stock ≥ 5, active=true | Query `GET /api/books?inStock=true&size=1` → pick `content[0].id` |
| `BOOK_LOW_STOCK` | A book with stock = 1 | Find or set via DB: `UPDATE books SET stock_quantity=1 WHERE id=...` |
| `BOOK_INACTIVE` | A book with active=false | DB: `UPDATE books SET active=false WHERE id=...` |
| `ADDRESS_A` | Saved address for USER_A | `POST /api/addresses` with TOKEN_A |
| `ADDRESS_B` | Saved address for USER_B | `POST /api/addresses` with TOKEN_B |
| `CATEGORY_ID` | Any valid category id | `GET /api/categories` → `[0].id` |
| `PUBLISHER_ID` | Any valid publisher id | `GET /api/publishers` → `[0].id` |

---

## 4. Test cases by domain

---

### AUTH — Registration & Login

#### AUTH-01 · Successful registration
| Field | Value |
|---|---|
| **ID** | AUTH-01 |
| **Endpoint** | `POST /api/auth/register` |
| **Objective** | Happy-path: new account created, password never returned |
| **Preconditions** | None |
| **Headers** | `Content-Type: application/json` |
| **Request body** | `{"fullName":"Asha Rao","email":"auth01@example.com","password":"Passw0rd-OK!"}` |
| **Expected status** | `201 Created` |
| **Expected response fields** | `id` (integer), `fullName="Asha Rao"`, `email="auth01@example.com"`, `role="CUSTOMER"`, `createdAt` (ISO-8601 UTC), NO `password` / `passwordHash` field |
| **DB verification** | `SELECT password_hash FROM users WHERE email='auth01@example.com'` → starts with `$2` (BCrypt), ≠ plaintext |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### AUTH-02 · Duplicate email (case-insensitive)
| Field | Value |
|---|---|
| **ID** | AUTH-02 |
| **Endpoint** | `POST /api/auth/register` |
| **Objective** | Registering the same email again (or in a different case) returns 409 |
| **Preconditions** | AUTH-01 completed |
| **Request body** | `{"fullName":"Bob","email":"AUTH01@EXAMPLE.COM","password":"Passw0rd-OK!"}` |
| **Expected status** | `409 Conflict` |
| **Expected response** | `code="EMAIL_ALREADY_REGISTERED"` |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### AUTH-03 · Registration — field validation errors
| Sub | Body | Expected status | Expected `fieldErrors[].field` |
|---|---|---|---|
| a | `{}` | `400` | `fullName`, `email`, `password` |
| b | `{"fullName":"A","email":"not-an-email","password":"weak"}` | `400` | `fullName` (too short), `email` (invalid format), `password` (too short / no digit / no upper) |
| c | `{"fullName":"Valid Name","email":"x@x.com","password":"nouppercase1"}` | `400` | `password` |
| d | `{"fullName":"Valid Name","email":"x@x.com","password":"NOLOWER1"}` | `400` | `password` |
| e | `{"fullName":"Valid Name","email":"x@x.com","password":"NoDigitHere!"}` | `400` | `password` |

**Result for each sub-case:** `[ ] PASS  [ ] FAIL  [ ] SKIP`

#### AUTH-04 · Registration — unknown field rejected
| Field | Value |
|---|---|
| **ID** | AUTH-04 |
| **Request body** | `{"fullName":"X","email":"auth04@example.com","password":"Passw0rd-OK!","role":"ADMIN"}` |
| **Expected status** | `400 Bad Request` |
| **Expected response** | `code="VALIDATION_FAILED"` — the `role` field is not in `RegisterRequest.additionalProperties: false` |
| **DB verification** | `SELECT count(*) FROM users WHERE email='auth04@example.com'` → `0` |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### AUTH-05 · Successful login
| Field | Value |
|---|---|
| **ID** | AUTH-05 |
| **Endpoint** | `POST /api/auth/login` |
| **Preconditions** | USER_A registered |
| **Request body** | `{"email":"<USER_A email>","password":"<USER_A password>"}` |
| **Expected status** | `200 OK` |
| **Expected response** | `accessToken` (non-empty string), `tokenType="Bearer"`, `expiresIn=1800`, `user.role="CUSTOMER"`, NO password field in `user` |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### AUTH-06 · Wrong password returns generic 401
| Field | Value |
|---|---|
| **ID** | AUTH-06 |
| **Request body** | `{"email":"<USER_A email>","password":"WrongPassw0rd!"}` |
| **Expected status** | `401 Unauthorized` |
| **Expected response** | `code="INVALID_CREDENTIALS"` — same message as AUTH-07 (timing-safe) |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### AUTH-07 · Unknown email returns generic 401
| Field | Value |
|---|---|
| **ID** | AUTH-07 |
| **Request body** | `{"email":"nobody@example.com","password":"Passw0rd-OK!"}` |
| **Expected status** | `401 Unauthorized` |
| **Expected response** | `code="INVALID_CREDENTIALS"`, same `message` as AUTH-06 |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### AUTH-08 · Get current user profile
| Field | Value |
|---|---|
| **ID** | AUTH-08 |
| **Endpoint** | `GET /api/users/me` |
| **Headers** | `Authorization: Bearer {{TOKEN_A}}` |
| **Expected status** | `200 OK` |
| **Expected response** | `email` matches USER_A, `role="CUSTOMER"`, no `passwordHash` |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### AUTH-09 · Protected endpoint — no token
| Field | Value |
|---|---|
| **ID** | AUTH-09 |
| **Endpoint** | `GET /api/users/me` (no Authorization header) |
| **Expected status** | `401 Unauthorized` |
| **Expected response** | `code="UNAUTHORIZED"` |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### AUTH-10 · Protected endpoint — expired / malformed token
| Sub | Token value | Expected status |
|---|---|---|
| a | `Bearer garbage` | `401` |
| b | A valid JWT with the signature replaced | `401` |
| c | `Basic dXNlcjpwYXNz` (Basic auth) | `401` |

**Result:** `[ ] PASS  [ ] FAIL  [ ] SKIP`

---

### CATALOGUE — Browse, Search, Filters

#### CAT-01 · List books — defaults
| Field | Value |
|---|---|
| **ID** | CAT-01 |
| **Endpoint** | `GET /api/books` |
| **Headers** | *(none — public endpoint)* |
| **Expected status** | `200 OK` |
| **Expected response** | `page=0`, `size=20`, `totalElements≥1`, `content` array of `BookSummary`, no inactive books |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### CAT-02 · List books — pagination
| Field | Value |
|---|---|
| **ID** | CAT-02 |
| **Query params** | `page=0&size=2` |
| **Expected status** | `200` |
| **Expected response** | `size=2`, `content.length≤2`, `first=true` |
| **Follow-up** | Repeat with `page=1`; `first=false`, `content` contains different books |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### CAT-03 · List books — invalid pagination
| Sub | Params | Expected |
|---|---|---|
| a | `size=0` | `400`, `code="VALIDATION_FAILED"`, `fieldErrors[].field="size"` |
| b | `size=101` | `400`, `code="VALIDATION_FAILED"` |
| c | `page=-1` | `400` |
| d | `categoryId=0` | `400` (minimum is 1) |
| e | `minPrice=-1` | `400` |

**Result:** `[ ] PASS  [ ] FAIL  [ ] SKIP`

#### CAT-04 · List books — filter by categoryId
| Field | Value |
|---|---|
| **ID** | CAT-04 |
| **Query params** | `categoryId={{CATEGORY_ID}}` |
| **Expected** | All `content[*].categoryId` equal `{{CATEGORY_ID}}` |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### CAT-05 · List books — filter by price range
| Field | Value |
|---|---|
| **ID** | CAT-05 |
| **Query params** | `minPrice=100&maxPrice=500` |
| **Expected** | All `content[*].price` between 100 and 500 inclusive |
| **Negative — minPrice > maxPrice** | `minPrice=900&maxPrice=100` → `400`, `fieldErrors[].field="minPrice"` |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### CAT-06 · List books — inStock filter
| Field | Value |
|---|---|
| **ID** | CAT-06 |
| **Query params** | `inStock=true` |
| **Expected** | All `content[*].inStock=true`; BOOK_LOW_STOCK (stock 1) appears, BOOK_LOW_STOCK with stock set to 0 does NOT appear |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### CAT-07 · List books — sort orders
| Sort value | Expected ordering |
|---|---|
| `price_asc` | `content[0].price ≤ content[1].price` |
| `price_desc` | `content[0].price ≥ content[1].price` |
| `title_asc` | Alphabetical A→Z |
| `title_desc` | Alphabetical Z→A |
| `rating_desc` | `content[0].ratingAvg ≥ content[1].ratingAvg` |
| `newest` | Newest creation date first |
| `INVALID_SORT` | `400 VALIDATION_FAILED` |

**Result:** `[ ] PASS  [ ] FAIL  [ ] SKIP`

#### CAT-08 · Search books — keyword match
| Field | Value |
|---|---|
| **ID** | CAT-08 |
| **Endpoint** | `GET /api/books/search?q=clean+code` |
| **Expected** | `200`, `content` non-empty, results contain books matching "clean code" in title or author; inactive book does NOT appear |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### CAT-09 · Search books — query validation
| Sub | Params | Expected |
|---|---|---|
| a | No `q` parameter | `400`, `fieldErrors[].field="q"` |
| b | `q=x` (1 char) | `400` (minLength=2) |
| c | `q=` + 101 chars | `400` (maxLength=100) |

**Result:** `[ ] PASS  [ ] FAIL  [ ] SKIP` 

#### CAT-10 · Get book detail
| Field | Value |
|---|---|
| **ID** | CAT-10 |
| **Endpoint** | `GET /api/books/{{BOOK_IN_STOCK}}` |
| **Expected status** | `200 OK` |
| **Expected response** | All `BookSummary` fields + `description` + `maxOrderableQuantity` in `[0,10]` |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### CAT-11 · Get book detail — not found / inactive
| Sub | Path | Expected |
|---|---|---|
| a | `/api/books/999999999` | `404`, `code="BOOK_NOT_FOUND"` |
| b | `/api/books/{{BOOK_INACTIVE}}` | `404`, `code="BOOK_NOT_FOUND"` |
| c | `/api/books/0` | `400`, `code="VALIDATION_FAILED"` (minimum id = 1) |
| d | `/api/books/abc` | `400` |

**Result:** `[ ] PASS  [ ] FAIL  [ ] SKIP`

#### CAT-12 · Related books
| Field | Value |
|---|---|
| **ID** | CAT-12 |
| **Endpoint** | `GET /api/books/{{BOOK_IN_STOCK}}/related` |
| **Expected** | `200`, JSON array, `{{BOOK_IN_STOCK}}` not in the array, no inactive books |
| **Negative** | `limit=0` → `400`; `limit=13` → `400`; unknown book → `404` |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### CAT-13 · List categories and publishers
| Sub | Endpoint | Expected |
|---|---|---|
| a | `GET /api/categories` | `200`, non-empty array, each has `id`, `name`; sorted by name |
| b | `GET /api/publishers` | `200`, non-empty array, same fields |

**Result:** `[ ] PASS  [ ] FAIL  [ ] SKIP`

---

### CART — Create, Update, Remove, Stock

#### CART-01 · Get empty cart
| Field | Value |
|---|---|
| **ID** | CART-01 |
| **Endpoint** | `GET /api/cart` |
| **Headers** | `Authorization: Bearer {{TOKEN_A}}` |
| **Preconditions** | USER_A has no items in cart |
| **Expected** | `200`, `items=[]`, `totalQuantity=0`, `subtotal=0` — **never** 404 |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### CART-02 · Get cart — unauthenticated
| Field | Value |
|---|---|
| **ID** | CART-02 |
| **Headers** | *(none)* |
| **Expected** | `401`, `code="UNAUTHORIZED"` |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### CART-03 · Add a new item — creates a line (201)
| Field | Value |
|---|---|
| **ID** | CART-03 |
| **Endpoint** | `POST /api/cart/items` |
| **Headers** | `Authorization: Bearer {{TOKEN_A}}`, `Content-Type: application/json` |
| **Body** | `{"bookId":{{BOOK_IN_STOCK}},"quantity":1}` |
| **Expected status** | `201 Created` (new line) |
| **Expected response** | `items` has 1 entry, `items[0].quantity=1`, `items[0].lineTotal = price * 1`, `subtotal = lineTotal`, `items[0].maxQuantity ≤ 10` |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### CART-04 · Add same book again — merges (200)
| Field | Value |
|---|---|
| **ID** | CART-04 |
| **Preconditions** | CART-03 done |
| **Body** | `{"bookId":{{BOOK_IN_STOCK}},"quantity":2}` |
| **Expected status** | `200 OK` (existing line updated) |
| **Expected response** | `items` still has 1 entry, `items[0].quantity=3` |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### CART-05 · Add item — quantity defaults to 1
| Field | Value |
|---|---|
| **ID** | CART-05 |
| **Body** | `{"bookId":{{BOOK_IN_STOCK}}}` (no quantity field) |
| **Expected status** | `201` or `200` |
| **Expected response** | quantity incremented by 1 |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### CART-06 · Add item — book not found
| Field | Value |
|---|---|
| **ID** | CART-06 |
| **Body** | `{"bookId":999999999,"quantity":1}` |
| **Expected status** | `404` |
| **Expected response** | `code="BOOK_NOT_FOUND"` |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### CART-07 · Add item — inactive book
| Field | Value |
|---|---|
| **ID** | CART-07 |
| **Body** | `{"bookId":{{BOOK_INACTIVE}},"quantity":1}` |
| **Expected status** | `404` |
| **Expected response** | `code="BOOK_NOT_FOUND"` |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### CART-08 · Add item — insufficient stock
| Field | Value |
|---|---|
| **ID** | CART-08 |
| **Preconditions** | BOOK_LOW_STOCK has stock = 1, cart is empty |
| **Body** | `{"bookId":{{BOOK_LOW_STOCK}},"quantity":2}` |
| **Expected status** | `409 Conflict` |
| **Expected response** | `code="INSUFFICIENT_STOCK"`, message contains book title |
| **DB verification** | Cart has 0 lines for USER_A after failed add |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### CART-09 · Add item — per-line limit exceeded (>10)
| Field | Value |
|---|---|
| **ID** | CART-09 |
| **Preconditions** | Cart has 8 copies of BOOK_IN_STOCK |
| **Body** | `{"bookId":{{BOOK_IN_STOCK}},"quantity":3}` |
| **Expected status** | `409 Conflict` |
| **Expected response** | `code="CART_LINE_LIMIT_EXCEEDED"` |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### CART-10 · Add item — request body validation
| Sub | Body | Expected field in `fieldErrors` |
|---|---|---|
| a | `{"quantity":1}` | `bookId` (required, missing) |
| b | `{"bookId":0,"quantity":1}` | `bookId` (minimum=1) |
| c | `{"bookId":9,"quantity":0}` | `quantity` (minimum=1) |
| d | `{"bookId":9,"quantity":11}` | `quantity` (maximum=10) |
| e | `{"bookId":9,"quantity":1,"price":1.00}` | rejected (`additionalProperties:false`) |
| f | `{"bookId":9,"userId":99}` | rejected |

All sub-cases: `400`, `code="VALIDATION_FAILED"`. **Result:** `[ ] PASS  [ ] FAIL  [ ] SKIP`

#### CART-11 · Update cart item — sets quantity (not adds)
| Field | Value |
|---|---|
| **ID** | CART-11 |
| **Endpoint** | `PUT /api/cart/items/{{ITEM_ID}}` |
| **Preconditions** | Item from CART-03, itemId captured |
| **Body** | `{"quantity":5}` |
| **Expected status** | `200` |
| **Expected response** | `items[0].quantity=5` (not 3+5) |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### CART-12 · Update cart item — exceeds stock
| Field | Value |
|---|---|
| **ID** | CART-12 |
| **Preconditions** | Item for BOOK_LOW_STOCK (stock=1) in cart |
| **Body** | `{"quantity":3}` |
| **Expected status** | `409` |
| **Expected response** | `code="INSUFFICIENT_STOCK"` |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### CART-13 · Update cart item — another user's item
| Field | Value |
|---|---|
| **ID** | CART-13 |
| **Preconditions** | USER_A has an item (itemId known). Test runs as USER_B. |
| **Headers** | `Authorization: Bearer {{TOKEN_B}}` |
| **Endpoint** | `PUT /api/cart/items/{{USER_A_ITEM_ID}}` |
| **Body** | `{"quantity":2}` |
| **Expected status** | `404` |
| **Expected response** | `code="CART_ITEM_NOT_FOUND"` |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### CART-14 · Remove cart item
| Field | Value |
|---|---|
| **ID** | CART-14 |
| **Endpoint** | `DELETE /api/cart/items/{{ITEM_ID}}` |
| **Expected status** | `200 OK` (returns updated cart, NOT 204) |
| **Expected response** | Removed item no longer in `items` |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### CART-15 · Remove non-existent item
| Field | Value |
|---|---|
| **ID** | CART-15 |
| **Endpoint** | `DELETE /api/cart/items/999999999` |
| **Expected status** | `404` |
| **Expected response** | `code="CART_ITEM_NOT_FOUND"` |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### CART-16 · Cart reflects current catalogue price
| Field | Value |
|---|---|
| **ID** | CART-16 |
| **Preconditions** | Item in cart. DB: `UPDATE books SET price=1.00 WHERE id={{BOOK_IN_STOCK}}` |
| **Action** | `GET /api/cart` |
| **Expected** | `items[0].book.price=1.00`, `lineTotal=1.00 * quantity` |
| **Cleanup** | Restore original price in DB |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

---

### ADDR — Addresses

#### ADDR-01 · Create first address — auto-becomes default
| Field | Value |
|---|---|
| **ID** | ADDR-01 |
| **Endpoint** | `POST /api/addresses` |
| **Headers** | `Authorization: Bearer {{TOKEN_A}}`, `Content-Type: application/json` |
| **Preconditions** | USER_A has 0 addresses |
| **Body** | `{"recipientName":"Asha Rao","phone":"+91 98765 43210","line1":"12 MG Road","city":"Pune","state":"Maharashtra","postalCode":"411001"}` |
| **Expected status** | `201 Created` |
| **Expected response** | `isDefault=true`, `countryCode="IN"` (server default), `id` (integer), `Location` header = `/api/addresses/{id}` |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### ADDR-02 · Second address — not default unless requested
| Field | Value |
|---|---|
| **ID** | ADDR-02 |
| **Body** | `{"recipientName":"Office","phone":"1234567","line1":"5 Park St","city":"Mumbai","state":"Maharashtra","postalCode":"400001","isDefault":false}` |
| **Expected response** | `isDefault=false` |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### ADDR-03 · Create address with isDefault=true — moves default
| Field | Value |
|---|---|
| **ID** | ADDR-03 |
| **Body** | `{"recipientName":"New Default","phone":"1234567","line1":"1 New Rd","city":"Delhi","state":"Delhi","postalCode":"110001","isDefault":true}` |
| **Expected** | `isDefault=true`. Previous default address now `isDefault=false` (verify via `GET /api/addresses`) |
| **DB verification** | `SELECT count(*) FROM addresses WHERE user_id=? AND is_default=true` → `1` |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### ADDR-04 · Address limit (10 max)
| Field | Value |
|---|---|
| **ID** | ADDR-04 |
| **Preconditions** | USER_A already has 10 addresses |
| **Expected status** | `409 Conflict` |
| **Expected response** | `code="ADDRESS_LIMIT_REACHED"`, `message="You can save at most 10 addresses."` |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### ADDR-05 · Address validation
| Sub | Body field violation | Expected |
|---|---|---|
| a | `recipientName=""` | `400`, field=`recipientName` |
| b | `phone="abc"` | `400`, field=`phone` |
| c | `postalCode="!!"` | `400`, field=`postalCode` |
| d | `countryCode="india"` (not 2 uppercase) | `400`, field=`countryCode` |
| e | Extra `userId:1` field | `400` (additionalProperties:false) |

**Result:** `[ ] PASS  [ ] FAIL  [ ] SKIP`

#### ADDR-06 · Update address
| Field | Value |
|---|---|
| **ID** | ADDR-06 |
| **Endpoint** | `PUT /api/addresses/{{ADDRESS_A}}` |
| **Body** | Same as ADDR-01 with `recipientName:"Updated Name"` |
| **Expected status** | `200` |
| **Expected response** | `recipientName="Updated Name"` |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### ADDR-07 · Update — false never removes current default
| Field | Value |
|---|---|
| **ID** | ADDR-07 |
| **Preconditions** | ADDRESS_A is the current default |
| **Body** | Same as ADDR-06 but `isDefault:false` |
| **Expected response** | `isDefault=true` — false does NOT strip the current default |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### ADDR-08 · Update another user's address → 404
| Field | Value |
|---|---|
| **ID** | ADDR-08 |
| **Headers** | `Authorization: Bearer {{TOKEN_A}}` |
| **Endpoint** | `PUT /api/addresses/{{ADDRESS_B}}` (USER_B's address) |
| **Expected status** | `404` |
| **Expected response** | `code="ADDRESS_NOT_FOUND"` |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### ADDR-09 · Delete address — default promoted to next newest
| Field | Value |
|---|---|
| **ID** | ADDR-09 |
| **Preconditions** | USER_A has ≥ 2 addresses; ADDRESS_A is the current default |
| **Endpoint** | `DELETE /api/addresses/{{ADDRESS_A}}` |
| **Expected status** | `204 No Content` |
| **DB verification** | The next-newest address for USER_A now has `is_default=true` |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### ADDR-10 · Delete another user's address → 404
| Field | Value |
|---|---|
| **ID** | ADDR-10 |
| **Headers** | `Authorization: Bearer {{TOKEN_A}}` |
| **Endpoint** | `DELETE /api/addresses/{{ADDRESS_B}}` |
| **Expected status** | `404` |
| **Expected response** | `code="ADDRESS_NOT_FOUND"` |
| **DB verification** | ADDRESS_B still exists for USER_B |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

---

### CHK — Checkout

#### CHK-01 · Happy-path checkout (CARD, no points)
| Field | Value |
|---|---|
| **ID** | CHK-01 |
| **Endpoint** | `POST /api/checkout` |
| **Headers** | `Authorization: Bearer {{TOKEN_A}}`, `Idempotency-Key: {{UUID_1}}`, `Content-Type: application/json` |
| **Preconditions** | USER_A cart has ≥ 1 item; ADDRESS_A exists |
| **Body** | `{"addressId":{{ADDRESS_A}},"paymentMethod":"CARD"}` |
| **Expected status** | `201 Created` |
| **Expected response** | `status="PLACED"`, `orderNumber` matches `EB-YYYYMMDD-NNNN`, `payment.status="PENDING"`, `payment.method="CARD"`, `totalAmount = subtotal - pointsDiscount`, `items[*].unitPrice` = catalogue price at time of checkout, `shippingAddress` = copy of ADDRESS_A, `cancellation.cancellable=true`, `Location` header = `/api/orders/{id}` |
| **DB verification** | `SELECT stock_quantity FROM books WHERE id=...` decreased by ordered quantity; `SELECT count(*) FROM cart_items WHERE cart_id=...` = 0 (cart cleared) |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### CHK-02 · Checkout with gift point redemption
| Field | Value |
|---|---|
| **ID** | CHK-02 |
| **Preconditions** | USER_A has ≥ 100 gift points (add manually: `INSERT INTO gift_point_transactions ...`); cart has 1 book priced ≥ 200 |
| **Body** | `{"addressId":{{ADDRESS_A}},"paymentMethod":"UPI","pointsToRedeem":100}` |
| **Expected response** | `pointsRedeemed=100`, `pointsDiscount=100.00`, `totalAmount = subtotal - 100.00`, `payment.amount = totalAmount` |
| **DB verification** | `SELECT points_balance FROM users WHERE id=...` decreased by 100; `SELECT type,points FROM gift_point_transactions WHERE order_id=...` = `REDEEM, -100` |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### CHK-03 · Checkout — empty cart
| Field | Value |
|---|---|
| **ID** | CHK-03 |
| **Preconditions** | USER_A cart is empty |
| **Body** | `{"addressId":{{ADDRESS_A}},"paymentMethod":"CARD"}` |
| **Expected status** | `409 Conflict` |
| **Expected response** | `code="EMPTY_CART"` |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### CHK-04 · Checkout — another user's address
| Field | Value |
|---|---|
| **ID** | CHK-04 |
| **Preconditions** | USER_A cart has items |
| **Body** | `{"addressId":{{ADDRESS_B}},"paymentMethod":"CARD"}` (USER_B's address) |
| **Expected status** | `404` |
| **Expected response** | `code="ADDRESS_NOT_FOUND"` |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### CHK-05 · Checkout — stock changed since adding to cart
| Field | Value |
|---|---|
| **ID** | CHK-05 |
| **Preconditions** | USER_A cart has 3 copies of BOOK_LOW_STOCK; DB: set stock to 1 |
| **Body** | `{"addressId":{{ADDRESS_A}},"paymentMethod":"CARD"}` |
| **Expected status** | `409 Conflict` |
| **Expected response** | `code="INSUFFICIENT_STOCK"`, message contains book title |
| **DB verification** | Stock unchanged at 1; no order created; cart items unchanged |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### CHK-06 · Checkout — points exceed balance
| Field | Value |
|---|---|
| **ID** | CHK-06 |
| **Preconditions** | USER_A has 50 gift points |
| **Body** | `{"addressId":{{ADDRESS_A}},"paymentMethod":"CARD","pointsToRedeem":100}` |
| **Expected status** | `422 Unprocessable Entity` |
| **Expected response** | `code="INVALID_POINTS_REDEMPTION"`, message contains available balance |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### CHK-07 · Checkout — points exceed 50% cap
| Field | Value |
|---|---|
| **ID** | CHK-07 |
| **Preconditions** | USER_A has 1000 points; cart subtotal = 200.00; cap = 100 points (50% of 200) |
| **Body** | `{"addressId":{{ADDRESS_A}},"paymentMethod":"CARD","pointsToRedeem":150}` |
| **Expected status** | `422 Unprocessable Entity` |
| **Expected response** | `code="INVALID_POINTS_REDEMPTION"`, message references the cap |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### CHK-08 · Checkout — idempotency replay
| Field | Value |
|---|---|
| **ID** | CHK-08 |
| **Preconditions** | CHK-01 completed (key UUID_1 used, order O1 created) |
| **Action** | Repeat CHK-01 with identical body and **same** `Idempotency-Key: {{UUID_1}}` |
| **Expected status** | `201 Created` |
| **Expected response** | Same `id` and `orderNumber` as O1; `Idempotent-Replayed: true` header present |
| **DB verification** | Still exactly 1 order in DB for this key |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### CHK-09 · Checkout — idempotency key reused with different body
| Field | Value |
|---|---|
| **ID** | CHK-09 |
| **Preconditions** | UUID_1 already used for CHK-01 |
| **Body** | Different `paymentMethod` (e.g., `"COD"` instead of `"CARD"`) with **same** `Idempotency-Key: {{UUID_1}}` |
| **Expected status** | `422 Unprocessable Entity` |
| **Expected response** | `code="IDEMPOTENCY_KEY_REUSED"` |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### CHK-10 · Checkout — missing idempotency key
| Field | Value |
|---|---|
| **ID** | CHK-10 |
| **Headers** | No `Idempotency-Key` header |
| **Expected status** | `400 Bad Request` |
| **Expected response** | `code="VALIDATION_FAILED"`, `fieldErrors[].field="Idempotency-Key"` |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### CHK-11 · Checkout — prices recalculated at checkout
| Field | Value |
|---|---|
| **ID** | CHK-11 |
| **Preconditions** | Book B in cart at price 100.00; DB: change price to 200.00 |
| **Action** | Checkout |
| **Expected response** | `items[*].unitPrice = 200.00` (server uses current catalogue price, not price at time of add) |
| **Cleanup** | Restore price |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### CHK-12 · Checkout — address/price snapshot survives later changes
| Field | Value |
|---|---|
| **ID** | CHK-12 |
| **Preconditions** | CHK-01 order O1 exists |
| **Action** | DB: `UPDATE addresses SET city='Changed' WHERE id={{ADDRESS_A}}`; `UPDATE books SET price=1.00 WHERE id=...` |
| **Verify** | `GET /api/orders/{{O1_ID}}` → `shippingAddress.city = "Pune"` (original), `items[0].unitPrice = original price` |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### CHK-13 · Checkout — COD payment stays PENDING
| Field | Value |
|---|---|
| **ID** | CHK-13 |
| **Body** | `{"addressId":{{ADDRESS_A}},"paymentMethod":"COD"}` |
| **Expected status** | `201` |
| **Expected response** | `payment.method="COD"`, `payment.status="PENDING"` |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

---

### PAY — Payments

#### PAY-01 · Process payment — success
| Field | Value |
|---|---|
| **ID** | PAY-01 |
| **Endpoint** | `POST /api/payments/{{ORDER_ID}}/process` |
| **Headers** | `Authorization: Bearer {{TOKEN_A}}`, `Idempotency-Key: {{UUID_P1}}` |
| **Preconditions** | Order from CHK-01 (CARD, status=PLACED, payment=PENDING) |
| **Expected status** | `200 OK` |
| **Expected response** | `status="SUCCESS"`, `transactionRef` matches `^SIM-[0-9A-F]{12}$`, `paidAt` non-null, `failureReason=null`, `amount = order totalAmount` |
| **DB verification** | `SELECT status FROM payments WHERE order_id=...` = `SUCCESS` |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### PAY-02 · Get payment details
| Field | Value |
|---|---|
| **ID** | PAY-02 |
| **Endpoint** | `GET /api/payments/{{ORDER_ID}}` |
| **Preconditions** | PAY-01 done |
| **Expected status** | `200` |
| **Expected response** | `status="SUCCESS"`, `transactionRef` non-null |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### PAY-03 · Process payment — simulated failure
| Field | Value |
|---|---|
| **ID** | PAY-03 |
| **Preconditions** | Instruct the simulated gateway to fail: call internal test endpoint or set `simulatedPaymentGateway.setSimulateFailure(true)` via direct Spring bean access or a test-only endpoint if exposed. Alternatively, check if the `SimulatedPaymentGateway` accepts config via env var. Create a new CARD order. |
| **Expected status** | `200 OK` |
| **Expected response** | `status="FAILED"`, `failureReason` non-null, `paidAt=null`, `transactionRef=null` |
| **DB verification** | Order `status="CANCELLED"`; `stock_quantity` restored to pre-checkout value |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### PAY-04 · Process payment — duplicate key replay
| Field | Value |
|---|---|
| **ID** | PAY-04 |
| **Preconditions** | PAY-01 done (UUID_P1 used) |
| **Action** | Repeat with **same** `Idempotency-Key: {{UUID_P1}}` |
| **Expected status** | `200 OK` |
| **Expected response** | Same `transactionRef` as PAY-01; `Idempotent-Replayed: true` header present |
| **DB verification** | No second payment record created |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### PAY-05 · Process payment — new key on already settled order
| Field | Value |
|---|---|
| **ID** | PAY-05 |
| **Preconditions** | PAY-01 done (payment SUCCESS) |
| **Action** | New UUID in `Idempotency-Key` header |
| **Expected status** | `409 Conflict` |
| **Expected response** | `code="PAYMENT_ALREADY_SETTLED"` |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### PAY-06 · Process payment — COD order
| Field | Value |
|---|---|
| **ID** | PAY-06 |
| **Preconditions** | CHK-13 order (COD) |
| **Expected status** | `409 Conflict` |
| **Expected response** | `code="PAYMENT_NOT_REQUIRED"` |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### PAY-07 · Process payment — cancelled order
| Field | Value |
|---|---|
| **ID** | PAY-07 |
| **Preconditions** | Order that was cancelled (either by PAY-03 or by ORD-02) |
| **Expected status** | `409 Conflict` |
| **Expected response** | `code="ORDER_NOT_PAYABLE"` |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### PAY-08 · Process payment — another user's order
| Field | Value |
|---|---|
| **ID** | PAY-08 |
| **Headers** | `Authorization: Bearer {{TOKEN_B}}` (USER_B) |
| **Endpoint** | `POST /api/payments/{{USER_A_ORDER_ID}}/process` |
| **Expected status** | `404` |
| **Expected response** | `code="ORDER_NOT_FOUND"` |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### PAY-09 · Process payment — idempotency key reused for different order
| Field | Value |
|---|---|
| **ID** | PAY-09 |
| **Preconditions** | UUID_P1 was used for order O1. Create new order O2. |
| **Action** | `POST /api/payments/{{O2_ID}}/process` with `Idempotency-Key: {{UUID_P1}}` |
| **Expected status** | `422 Unprocessable Entity` |
| **Expected response** | `code="IDEMPOTENCY_KEY_REUSED"` |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### PAY-10 · Get payment — another user's order
| Field | Value |
|---|---|
| **ID** | PAY-10 |
| **Headers** | `Authorization: Bearer {{TOKEN_B}}` |
| **Endpoint** | `GET /api/payments/{{USER_A_ORDER_ID}}` |
| **Expected status** | `404` |
| **Expected response** | `code="ORDER_NOT_FOUND"` |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

---

### ORD — Orders

#### ORD-01 · List orders — own orders
| Field | Value |
|---|---|
| **ID** | ORD-01 |
| **Endpoint** | `GET /api/orders` |
| **Headers** | `Authorization: Bearer {{TOKEN_A}}` |
| **Preconditions** | USER_A has ≥ 1 order |
| **Expected status** | `200` |
| **Expected response** | `totalElements ≥ 1`, `content[0]` has `id`, `orderNumber`, `status`, `totalAmount`, `itemCount`, `paymentMethod`, `paymentStatus` |
| **Isolation** | USER_B's orders never appear |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### ORD-02 · List orders — pagination and status filter
| Sub | Params | Expected |
|---|---|---|
| a | `page=0&size=1` | `size=1`, `first=true` |
| b | `status=PLACED` | All `content[*].status="PLACED"` |
| c | `status=INVALID_STATUS` | `400`, `code="VALIDATION_FAILED"` |
| d | `size=0` | `400` |

**Result:** `[ ] PASS  [ ] FAIL  [ ] SKIP`

#### ORD-03 · Get order details
| Field | Value |
|---|---|
| **ID** | ORD-03 |
| **Endpoint** | `GET /api/orders/{{ORDER_ID}}` |
| **Expected status** | `200` |
| **Expected response** | All `Order` fields present: `id`, `orderNumber`, `status`, `shippingAddress`, `items` (≥1), `subtotal`, `totalAmount`, `pointsRedeemed`, `pointsDiscount`, `payment`, `statusHistory`, `cancellation` |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### ORD-04 · Get order — another user's order
| Field | Value |
|---|---|
| **ID** | ORD-04 |
| **Headers** | `Authorization: Bearer {{TOKEN_B}}` |
| **Endpoint** | `GET /api/orders/{{USER_A_ORDER_ID}}` |
| **Expected status** | `404` |
| **Expected response** | `code="ORDER_NOT_FOUND"` |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### ORD-05 · Cancel order — within 48h window
| Field | Value |
|---|---|
| **ID** | ORD-05 |
| **Endpoint** | `POST /api/orders/{{ORDER_ID}}/cancel` |
| **Preconditions** | PLACED order, paid (SUCCESS) by PAY-01 |
| **Body** | `{"reason":"Ordered by mistake"}` |
| **Expected status** | `200` |
| **Expected response** | `status="CANCELLED"`, `cancelReason="Ordered by mistake"`, `cancelledAt` non-null, `payment.status="REFUNDED"`, `cancellation.cancellable=false`, `cancellation.reason="ORDER_ALREADY_CANCELLED"` |
| **DB verification** | Stock restored; `SELECT type FROM gift_point_transactions WHERE order_id=... AND type='REFUND'` returns 1 row (if points were redeemed); `SELECT status FROM payments ...` = `REFUNDED` |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### ORD-06 · Cancel order — no body (uses default reason)
| Field | Value |
|---|---|
| **ID** | ORD-06 |
| **Body** | *(empty / omitted)* |
| **Expected** | `200`, `cancelReason="Cancelled by customer"` |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### ORD-07 · Cancel order — after 48 hours
| Field | Value |
|---|---|
| **ID** | ORD-07 |
| **Preconditions** | Create new PLACED order; DB: `UPDATE orders SET created_at = NOW() - INTERVAL '49 hours' WHERE id=...` |
| **Expected status** | `409 Conflict` |
| **Expected response** | `code="CANCELLATION_WINDOW_EXPIRED"`, `message="Orders can only be cancelled within 48 hours of being placed."` |
| **DB verification** | Order still PLACED; stock unchanged |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### ORD-08 · Cancel order — wrong status (SHIPPED)
| Field | Value |
|---|---|
| **ID** | ORD-08 |
| **Preconditions** | DB: `UPDATE orders SET status='SHIPPED' WHERE id=...` |
| **Expected status** | `409 Conflict` |
| **Expected response** | `code="ORDER_NOT_CANCELLABLE_STATUS"`, `message="Orders that are SHIPPED or DELIVERED cannot be cancelled."` |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### ORD-09 · Cancel order — already cancelled (idempotency)
| Field | Value |
|---|---|
| **ID** | ORD-09 |
| **Preconditions** | ORD-05 completed (order already CANCELLED) |
| **Action** | Call cancel again on same order |
| **Expected status** | `409 Conflict` |
| **Expected response** | `code="ORDER_ALREADY_CANCELLED"` |
| **DB verification** | No change in stock, payment, points |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### ORD-10 · Cancel order — another user's order
| Field | Value |
|---|---|
| **ID** | ORD-10 |
| **Headers** | `Authorization: Bearer {{TOKEN_B}}` |
| **Endpoint** | `POST /api/orders/{{USER_A_ORDER_ID}}/cancel` |
| **Expected status** | `404` |
| **Expected response** | `code="ORDER_NOT_FOUND"` |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### ORD-11 · Buy-again — success
| Field | Value |
|---|---|
| **ID** | ORD-11 |
| **Endpoint** | `POST /api/orders/{{ORDER_ID}}/buy-again` |
| **Preconditions** | An order containing BOOK_IN_STOCK (stock ≥ original quantity) |
| **Expected status** | `200` |
| **Expected response** | `cart.items.length ≥ 1`; item for BOOK_IN_STOCK present with quantity ≥ ordered quantity; `issues=[]` |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### ORD-12 · Buy-again — out-of-stock book
| Field | Value |
|---|---|
| **ID** | ORD-12 |
| **Preconditions** | Order contains BOOK_LOW_STOCK; DB: `UPDATE books SET stock_quantity=0 WHERE id=...` |
| **Expected status** | `200` (partial success) OR `409` (if all items unavailable) |
| **Expected response** | If 200: `issues[*].reason="OUT_OF_STOCK"` for that book. If 409: `code="NO_ITEMS_AVAILABLE"` |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### ORD-13 · Buy-again — inactive book
| Field | Value |
|---|---|
| **ID** | ORD-13 |
| **Preconditions** | Order contains BOOK_INACTIVE |
| **Expected status** | `200` or `409` |
| **Expected response** | `issues[*].reason="BOOK_INACTIVE"` for that book |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### ORD-14 · Buy-again — all items unavailable (409)
| Field | Value |
|---|---|
| **ID** | ORD-14 |
| **Preconditions** | Order where every book is inactive or out-of-stock |
| **Expected status** | `409 Conflict` |
| **Expected response** | `code="NO_ITEMS_AVAILABLE"` |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### ORD-15 · Buy-again — idempotent (calling twice gives same cart)
| Field | Value |
|---|---|
| **ID** | ORD-15 |
| **Action** | Call buy-again on same order twice without other cart changes |
| **Expected** | Second call returns same quantities (no doubling) |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### ORD-16 · Buy-again — another user's order
| Field | Value |
|---|---|
| **ID** | ORD-16 |
| **Headers** | `Authorization: Bearer {{TOKEN_B}}` |
| **Endpoint** | `POST /api/orders/{{USER_A_ORDER_ID}}/buy-again` |
| **Expected status** | `404` |
| **Expected response** | `code="ORDER_NOT_FOUND"` |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

---

### GP — Gift Points

#### GP-01 · Get balance — new user has 0 points
| Field | Value |
|---|---|
| **ID** | GP-01 |
| **Endpoint** | `GET /api/gift-points/balance` |
| **Headers** | `Authorization: Bearer {{TOKEN_A}}` (fresh user, no orders) |
| **Expected** | `balance=0`, `pointValue=1.00`, `maxRedeemPercentOfSubtotal=50`, `earnAmountPerPoint=10.00` |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### GP-02 · Get balance reflects redemption
| Field | Value |
|---|---|
| **ID** | GP-02 |
| **Preconditions** | CHK-02 completed (100 points redeemed) |
| **Expected** | `balance` = initial balance - 100 |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### GP-03 · List transactions — empty ledger
| Field | Value |
|---|---|
| **ID** | GP-03 |
| **Endpoint** | `GET /api/gift-points/transactions` |
| **Expected** | `200`, `totalElements=0`, `content=[]`, `first=true` |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### GP-04 · List transactions — REDEEM entry appears after checkout
| Field | Value |
|---|---|
| **ID** | GP-04 |
| **Preconditions** | CHK-02 completed |
| **Expected** | `content[0].type="REDEEM"`, `content[0].points < 0`, `content[0].orderId` = checkout order id |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### GP-05 · List transactions — type filter
| Sub | Params | Expected |
|---|---|---|
| a | `type=REDEEM` | All `content[*].type="REDEEM"` |
| b | `type=EARN` | All `content[*].type="EARN"` |
| c | `type=BOGUS` | `400`, `code="VALIDATION_FAILED"` |

**Result:** `[ ] PASS  [ ] FAIL  [ ] SKIP`

#### GP-06 · List transactions — pagination validation
| Sub | Params | Expected |
|---|---|---|
| a | `size=101` | `400` |
| b | `page=-1` | `400` |

**Result:** `[ ] PASS  [ ] FAIL  [ ] SKIP`

#### GP-07 · Unauthenticated access
| Sub | Endpoint | Expected |
|---|---|---|
| a | `GET /api/gift-points/balance` | `401`, `code="UNAUTHORIZED"` |
| b | `GET /api/gift-points/transactions` | `401` |

**Result:** `[ ] PASS  [ ] FAIL  [ ] SKIP`

---

### REC — Recommendations

#### REC-01 · New user — POPULAR strategy
| Field | Value |
|---|---|
| **ID** | REC-01 |
| **Endpoint** | `GET /api/recommendations` |
| **Headers** | `Authorization: Bearer {{TOKEN_A}}` (no orders yet) |
| **Expected** | `200`, `strategy="POPULAR"`, `books` non-empty array of `BookSummary`, all `inStock=true` |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### REC-02 · Returning user — PURCHASE_HISTORY strategy
| Field | Value |
|---|---|
| **ID** | REC-02 |
| **Preconditions** | USER_A has ≥ 1 non-cancelled PLACED or delivered order |
| **Expected** | `strategy="PURCHASE_HISTORY"`, recommended books do NOT include books already purchased |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### REC-03 · Cancelled orders ignored — falls back to POPULAR
| Field | Value |
|---|---|
| **ID** | REC-03 |
| **Preconditions** | USER_A has only cancelled orders |
| **Expected** | `strategy="POPULAR"` |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### REC-04 · Limit parameter
| Sub | Params | Expected |
|---|---|---|
| a | `limit=2` | `books.length ≤ 2` |
| b | `limit=0` | `400`, `code="VALIDATION_FAILED"` |
| c | `limit=21` | `400` (maximum=20) |

**Result:** `[ ] PASS  [ ] FAIL  [ ] SKIP`

#### REC-05 · Out-of-stock and inactive books excluded
| Field | Value |
|---|---|
| **ID** | REC-05 |
| **Preconditions** | Known out-of-stock or inactive books exist |
| **Expected** | None of those appear in `books` |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

#### REC-06 · Unauthenticated → 401
| Field | Value |
|---|---|
| **ID** | REC-06 |
| **Headers** | No Authorization |
| **Expected** | `401`, `code="UNAUTHORIZED"` |
| **Result** | `[ ] PASS  [ ] FAIL  [ ] SKIP` |

---

## 5. Automated test code (Postman / Newman)

The snippets below go in the **Tests** tab of each Postman request. They use Postman's built-in `pm` API.

### Collection-level pre-request script (set once)

```javascript
// Auto-capture token from login response
if (pm.response && pm.response.code === 200 &&
    pm.request.url.toString().includes("/api/auth/login")) {
  const body = pm.response.json();
  if (body.accessToken) {
    pm.collectionVariables.set("TOKEN_A", body.accessToken);
  }
}
```

### AUTH-01 — Register

```javascript
pm.test("Status is 201", () => pm.response.to.have.status(201));
pm.test("Returns UserProfile with role=CUSTOMER", () => {
  const body = pm.response.json();
  pm.expect(body.id).to.be.a("number");
  pm.expect(body.email).to.equal("auth01@example.com");
  pm.expect(body.role).to.equal("CUSTOMER");
  pm.expect(body).to.not.have.property("password");
  pm.expect(body).to.not.have.property("passwordHash");
  pm.collectionVariables.set("USER_A_ID", body.id);
});
```

### AUTH-05 — Login

```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
pm.test("Returns valid AuthResponse", () => {
  const body = pm.response.json();
  pm.expect(body.accessToken).to.be.a("string").and.not.empty;
  pm.expect(body.tokenType).to.equal("Bearer");
  pm.expect(body.expiresIn).to.equal(1800);
  pm.expect(body.user.role).to.equal("CUSTOMER");
  pm.collectionVariables.set("TOKEN_A", body.accessToken);
});
```

### CART-03 — Add item (new line → 201)

```javascript
pm.test("Status is 201", () => pm.response.to.have.status(201));
pm.test("Cart has one item with correct lineTotal", () => {
  const cart = pm.response.json();
  pm.expect(cart.items).to.have.lengthOf(1);
  const item = cart.items[0];
  pm.expect(item.lineTotal).to.be.closeTo(item.book.price * item.quantity, 0.005);
  pm.expect(cart.subtotal).to.equal(item.lineTotal);
  pm.collectionVariables.set("ITEM_ID", item.itemId);
});
```

### CHK-01 — Checkout (happy path)

```javascript
pm.test("Status is 201", () => pm.response.to.have.status(201));
pm.test("Order structure valid", () => {
  const order = pm.response.json();
  pm.expect(order.status).to.equal("PLACED");
  pm.expect(order.orderNumber).to.match(/^EB-\d{8}-\d+$/);
  pm.expect(order.payment.status).to.equal("PENDING");
  pm.expect(order.totalAmount).to.equal(order.subtotal - order.pointsDiscount);
  pm.expect(order.cancellation.cancellable).to.be.true;
  pm.collectionVariables.set("ORDER_ID", order.id);
});
pm.test("No Idempotent-Replayed header on first call", () => {
  pm.expect(pm.response.headers.get("Idempotent-Replayed")).to.be.null;
});
pm.test("Location header present", () => {
  pm.expect(pm.response.headers.get("Location")).to.match(/\/api\/orders\/\d+/);
});
```

### PAY-01 — Process payment

```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
pm.test("Payment succeeded", () => {
  const pay = pm.response.json();
  pm.expect(pay.status).to.equal("SUCCESS");
  pm.expect(pay.transactionRef).to.match(/^SIM-[0-9A-F]{12}$/);
  pm.expect(pay.paidAt).to.be.a("string");
  pm.expect(pay.failureReason).to.be.null;
});
```

### CHK-08 — Idempotency replay

```javascript
pm.test("Status is 201", () => pm.response.to.have.status(201));
pm.test("Idempotent-Replayed header is true", () => {
  pm.expect(pm.response.headers.get("Idempotent-Replayed")).to.equal("true");
});
pm.test("Same order id as original", () => {
  const order = pm.response.json();
  pm.expect(order.id).to.equal(
    parseInt(pm.collectionVariables.get("ORDER_ID"))
  );
});
```

### ORD-07 — Cancel after 48h

```javascript
pm.test("Status is 409", () => pm.response.to.have.status(409));
pm.test("Error code is CANCELLATION_WINDOW_EXPIRED", () => {
  pm.expect(pm.response.json().code).to.equal("CANCELLATION_WINDOW_EXPIRED");
});
```

### Generic 400 validation check (reuse for all validation tests)

```javascript
pm.test("Status is 400", () => pm.response.to.have.status(400));
pm.test("Error code is VALIDATION_FAILED", () => {
  const body = pm.response.json();
  pm.expect(body.code).to.equal("VALIDATION_FAILED");
  pm.expect(body.fieldErrors).to.be.an("array").and.not.empty;
});
pm.test("Response never echoes password", () => {
  pm.expect(pm.response.text()).to.not.include(pm.collectionVariables.get("PASSWORD"));
});
```

### Newman run command

```bash
newman run EBookStore.postman_collection.json \
  --environment EBookStore.postman_environment.json \
  --reporters cli,junit \
  --reporter-junit-export results.xml \
  --bail
```

---

## 6. Checklist for manual execution

Use this checklist when running tests via Swagger UI, Postman, Insomnia, or curl. Check each box only **after** a test has been run and its result recorded.

### Setup

- [ ] Backend started on `localhost:8080` (`mvn spring-boot:run` in `backend/`)
- [ ] Swagger UI accessible at `http://localhost:8080/swagger-ui.html`
- [ ] Database reachable (embedded or local PostgreSQL)
- [ ] Flyway migrations applied (check startup log: "Successfully applied N migrations")
- [ ] Seed data loaded (check `GET /api/books` returns ≥ 1 result)
- [ ] Global state prepared: USER_A, TOKEN_A, USER_B, TOKEN_B, BOOK_IN_STOCK, BOOK_LOW_STOCK, BOOK_INACTIVE, ADDRESS_A, ADDRESS_B

### Authentication

- [ ] AUTH-01 Successful registration
- [ ] AUTH-02 Duplicate email → 409
- [ ] AUTH-03a–e Field validation errors
- [ ] AUTH-04 Unknown field → 400
- [ ] AUTH-05 Successful login
- [ ] AUTH-06 Wrong password → 401
- [ ] AUTH-07 Unknown email → 401 (same message as AUTH-06)
- [ ] AUTH-08 Get own profile
- [ ] AUTH-09 No token → 401
- [ ] AUTH-10a–c Bad tokens → 401

### Catalogue

- [ ] CAT-01 List books defaults
- [ ] CAT-02 Pagination
- [ ] CAT-03a–e Invalid pagination/filters
- [ ] CAT-04 Filter by category
- [ ] CAT-05 Price range filter + minPrice > maxPrice → 400
- [ ] CAT-06 inStock filter
- [ ] CAT-07 All sort orders
- [ ] CAT-08 Keyword search
- [ ] CAT-09a–c Search query validation
- [ ] CAT-10 Book detail
- [ ] CAT-11a–d Book not found / inactive / invalid id
- [ ] CAT-12 Related books + negatives
- [ ] CAT-13a–b Categories and publishers

### Cart

- [ ] CART-01 Empty cart
- [ ] CART-02 No token → 401
- [ ] CART-03 Add new item → 201
- [ ] CART-04 Add same book → 200, merged
- [ ] CART-05 Default quantity = 1
- [ ] CART-06 Book not found → 404
- [ ] CART-07 Inactive book → 404
- [ ] CART-08 Insufficient stock → 409
- [ ] CART-09 Per-line limit → 409
- [ ] CART-10a–f Request body validation → 400
- [ ] CART-11 Update sets quantity
- [ ] CART-12 Update exceeds stock → 409
- [ ] CART-13 Update another user's item → 404
- [ ] CART-14 Remove item → 200 with updated cart
- [ ] CART-15 Remove non-existent item → 404
- [ ] CART-16 Cart uses current price

### Addresses

- [ ] ADDR-01 Create first address → default
- [ ] ADDR-02 Second address, not default
- [ ] ADDR-03 isDefault=true moves default; DB: count=1
- [ ] ADDR-04 11th address → 409
- [ ] ADDR-05a–e Validation errors
- [ ] ADDR-06 Update address
- [ ] ADDR-07 isDefault=false does not remove current default
- [ ] ADDR-08 Update another user's → 404
- [ ] ADDR-09 Delete default → next address promoted
- [ ] ADDR-10 Delete another user's → 404

### Checkout

- [ ] CHK-01 Happy path (CARD, no points)
- [ ] CHK-02 With gift point redemption
- [ ] CHK-03 Empty cart → 409 EMPTY_CART
- [ ] CHK-04 Another user's address → 404
- [ ] CHK-05 Stock changed since add → 409 INSUFFICIENT_STOCK
- [ ] CHK-06 Points exceed balance → 422
- [ ] CHK-07 Points exceed 50% cap → 422
- [ ] CHK-08 Idempotency replay → 201 + Idempotent-Replayed:true
- [ ] CHK-09 Key reused with different body → 422
- [ ] CHK-10 Missing Idempotency-Key → 400
- [ ] CHK-11 Price recalculated at checkout
- [ ] CHK-12 Address/price snapshot persists
- [ ] CHK-13 COD → payment PENDING

### Payments

- [ ] PAY-01 Successful payment → SUCCESS + SIM-xxx ref
- [ ] PAY-02 Get payment details
- [ ] PAY-03 Simulated failure → FAILED, order CANCELLED, stock restored
- [ ] PAY-04 Duplicate key → replay + Idempotent-Replayed:true
- [ ] PAY-05 New key on settled → 409 PAYMENT_ALREADY_SETTLED
- [ ] PAY-06 COD order → 409 PAYMENT_NOT_REQUIRED
- [ ] PAY-07 Cancelled order → 409 ORDER_NOT_PAYABLE
- [ ] PAY-08 Another user's order → 404
- [ ] PAY-09 Key reused for different order → 422
- [ ] PAY-10 Get another user's payment → 404

### Orders

- [ ] ORD-01 List own orders
- [ ] ORD-02a–d Pagination and status filter
- [ ] ORD-03 Get order details (all fields)
- [ ] ORD-04 Get another user's order → 404
- [ ] ORD-05 Cancel within 48h → REFUNDED payment, stock restored, points refunded
- [ ] ORD-06 Cancel with no body → default reason
- [ ] ORD-07 Cancel after 48h → 409 CANCELLATION_WINDOW_EXPIRED
- [ ] ORD-08 Cancel SHIPPED → 409 ORDER_NOT_CANCELLABLE_STATUS
- [ ] ORD-09 Cancel already cancelled → 409 ORDER_ALREADY_CANCELLED
- [ ] ORD-10 Cancel another user's → 404
- [ ] ORD-11 Buy-again success
- [ ] ORD-12 Buy-again out-of-stock book → issue or 409
- [ ] ORD-13 Buy-again inactive book → issue
- [ ] ORD-14 Buy-again all unavailable → 409 NO_ITEMS_AVAILABLE
- [ ] ORD-15 Buy-again idempotent
- [ ] ORD-16 Buy-again another user's → 404

### Gift Points

- [ ] GP-01 New user balance = 0
- [ ] GP-02 Balance decreases after redemption
- [ ] GP-03 Empty ledger
- [ ] GP-04 REDEEM entry after checkout
- [ ] GP-05a–c Type filter + invalid type
- [ ] GP-06a–b Pagination validation
- [ ] GP-07a–b Unauthenticated → 401

### Recommendations

- [ ] REC-01 New user → POPULAR
- [ ] REC-02 Returning user → PURCHASE_HISTORY, excludes bought books
- [ ] REC-03 Only cancelled orders → POPULAR fallback
- [ ] REC-04a–c Limit param + validation
- [ ] REC-05 No out-of-stock or inactive books
- [ ] REC-06 Unauthenticated → 401

---

### Test execution summary

| Domain | Total cases | Passed | Failed | Skipped |
|---|---|---|---|---|
| Authentication | 13 | — | — | — |
| Catalogue | 24 | — | — | — |
| Cart | 18 | — | — | — |
| Addresses | 10 | — | — | — |
| Checkout | 13 | — | — | — |
| Payments | 10 | — | — | — |
| Orders | 16 | — | — | — |
| Gift Points | 7 | — | — | — |
| Recommendations | 6 | — | — | — |
| **Total** | **117** | — | — | — |

> Fill in the counts after each run. A test is PASS only when the actual HTTP status, response body, and (where noted) DB state all match the expected values.
