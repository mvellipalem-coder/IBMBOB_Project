# Wireframe traceability

Maps each requirement of the capstone customer journey and its wireframes (slides 6-10 of the capstone deck) to the React page or component and the backend endpoint that supports it. Paths are relative to `frontend/src`; endpoints are under `/api`.

Status: **Done** = built and covered by tests, **Partial** = built, with a stated limit. Items the backend cannot support are in section 3 and are not faked in the UI.

## 1. The 13-step customer journey

| # | Requirement | React page / component | Backend endpoint(s) | Status |
|---|---|---|---|---|
| 1 | Landing page with available books | `pages/HomePage.jsx` (Bestsellers this Month, New Launches), `components/books/BookGrid.jsx`, `BookCard.jsx`, `CategoryNav.jsx` | `GET /books?sort=rating_desc`, `GET /books?sort=newest`, `GET /categories` | Done |
| 2 | Login and registration | `pages/LoginPage.jsx`, `pages/RegisterPage.jsx`, `context/AuthContext.jsx`, `components/layout/ProtectedRoute.jsx` | `POST /auth/register`, `POST /auth/login`, `GET /users/me` | Done |
| 3 | Catalogue by category and publisher | `pages/CataloguePage.jsx` (category sidebar `CategoryNav`, filter bar, `SearchBar`, `PriceFilter`, `Pagination`) | `GET /books` (`categoryId`, `publisherId`, `minPrice`, `maxPrice`, `inStock`, `sort`), `GET /books/search`, `GET /categories`, `GET /publishers` | Done |
| 4 | Book selection and related products | `pages/BookDetailsPage.jsx` (details, "Related Reads" panel, `Breadcrumbs`) | `GET /books/{id}`, `GET /books/{id}/related` | Done |
| 5 | Cart: update quantity, remove | `pages/CartPage.jsx`, `components/cart/CartItem.jsx` (- / + stepper, Remove with confirmation), `context/CartContext.jsx` | `GET /cart`, `POST /cart/items`, `PUT /cart/items/{itemId}`, `DELETE /cart/items/{itemId}` | Done |
| 6 | Recommendations from order history | `pages/HomePage.jsx` (Recommended for You, signed-in only), `pages/RecommendationsPage.jsx` | `GET /recommendations` (`PURCHASE_HISTORY` or `POPULAR` strategy) | Done |
| 7 | Delivery address selection | `pages/CheckoutPage.jsx` (saved-address choice, new-address form), `pages/AddressPage.jsx`, `components/address/*` | `GET/POST /addresses`, `PUT/DELETE /addresses/{id}` | Done |
| 8 | Payment method selection | `components/payment/PaymentMethodSelector.jsx` in `CheckoutPage.jsx` | `POST /checkout` (`paymentMethod`: `CARD`, `UPI`, `COD`) | Partial: only these three methods exist |
| 9 | Gift-point redemption | `CheckoutPage.jsx` ("Points to redeem"), `components/cart/OrderSummary.jsx` | `GET /gift-points/balance`, `POST /checkout` (`pointsToRedeem`) | Done (partial discount, limited by the server's max percent) |
| 10 | Payment completion | `pages/PaymentPage.jsx` ("Complete your payment" panel) | `POST /payments/{orderId}/process` (with `Idempotency-Key`) | Done |
| 11 | Purchase confirmation | `pages/OrderConfirmationPage.jsx` (success only when the server reports `payment.status` `SUCCESS`, or a COD order exists) | `GET /orders/{id}` | Done |
| 12 | Order history with buy-again | `pages/OrderHistoryPage.jsx`, `components/orders/OrderCard.jsx` (Buy again), `hooks/useBuyAgain.js`, `pages/OrderDetailsPage.jsx` | `GET /orders`, `GET /orders/{id}`, `POST /orders/{id}/buy-again` | Done |
| 13 | Cancellation within 48 hours when eligible | `pages/OrderDetailsPage.jsx` (Cancel order with confirmation, window and reason text), `OrderCard.jsx` (hint link) | `POST /orders/{id}/cancel`, `cancellation` object in `GET /orders/{id}` | Done. The list shows only a hint (`GET /orders` has no `cancellation`); the server decides. |

## 2. Wireframe screens

| Wireframe element | Where it is built | Notes |
|---|---|---|
| Dark "Book Worm" top bar: brand, My Orders, cart icon with count badge, user | `components/layout/Navbar.jsx` | Badge count comes from `GET /cart`. |
| Left category list (All, Romance, ...) | `components/books/CategoryNav.jsx` | Sidebar on wide screens, scrolling row on phones. |
| Filter row (search, price, sort) | `CataloguePage.jsx`, `SearchBar.jsx`, `PriceFilter.jsx` | Publisher and in-stock filters added; language/format not possible (section 3). |
| Horizontal book cards (cover, title, author, category/publisher links, price) | `components/books/BookCard.jsx` | |
| Recommended / Bestsellers / New Launches sections | `HomePage.jsx` | |
| Book details: breadcrumbs, cover, author, price, Add to Cart, Related Reads | `BookDetailsPage.jsx`, `components/layout/Breadcrumbs.jsx` | |
| Add-to-cart confirmation | `components/books/AddToCartFeedback.jsx` (toast, closes itself after 6 s) | Shown only after the server answers. |
| Shopping Cart with quantity stepper | `CartPage.jsx`, `CartItem.jsx` | |
| Address form and "Use Saved Address" | `CheckoutPage.jsx`, `components/address/AddressForm.jsx` | Saved addresses are radio choices. |
| Grand Total panel | `components/cart/OrderSummary.jsx` | Values are the server's; the browser does no arithmetic. |
| Complete Payment panel on illustrated backdrop | `PaymentPage.jsx` (`Backdrop`, `pay-panel`) | See "Card number, name, CVV, expiry" below. |
| Purchase success with books list and "Continue your Shopping" | `OrderConfirmationPage.jsx` | |

## 3. Wireframe items the backend cannot support yet

These are not mocked. Each needs a backend change first.

| Wireframe item | Missing backend support | UI behaviour today |
|---|---|---|
| My Wishlist, Add to Wishlist | No wishlist table or endpoints | Not shown |
| My Writers, About the writer | No writer profile data (authors are names only) | Author names shown; no bio page |
| Reviews list and review form | No review endpoints (only `ratingAvg`/`ratingCount`) | Rating summary shown |
| Language and Format filters/fields | `Book` has no `language` or `format` | Not shown |
| "Sells" count (e.g. 145 copies sold) | No sales-count field | Not shown |
| Tax and Delivery Charges lines | The cart and order have no tax/delivery fields | Only subtotal, gift-point discount and total are shown |
| Apply Coupon and Discount | No coupon model or endpoint | Not shown |
| Wallet and Debit-card tabs | `PaymentMethod` is `CARD`, `UPI`, `COD` only | Three methods offered |
| Card number, name, CVV, expiry entry | Deliberately not collected: the backend simulates the gateway and the app must not handle card data | "Pay now" button only; no card data stored or sent |
| "Delivery by Mon, 21 Jul" on cards and details | `expectedDeliveryDate` exists only on an order (order date + 5 days) | Shown on order screens only |
| Back-cover image | Only one `imageUrl` per book | A generated cover is used when `imageUrl` is null |
| Book description in lists | `description` is only in `GET /books/{id}` | Description on the details page only |

## 4. Other backend gaps (also in `FRONTEND_INTEGRATION.md`)

- No admin order-status endpoint, so orders stay `PLACED` and gift points are never earned on delivery. This is the largest gap.
- `PUT /users/me`, admin catalogue CRUD, logout/revocation and refresh-token endpoints are missing.

## 5. Verification

| Check | Result |
|---|---|
| `npm test` | 13 files, 119 tests passed |
| `npm run build` | Succeeds |
| `npm audit` | 0 vulnerabilities |
