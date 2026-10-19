# Order Management Specification & Design

## Overview
This document describes order lifecycle management, history retrieval, purchase confirmation details, buy-again workflows, and cancellation rules for the EBookStore capstone backend.

---

## 1. Endpoints & Operations

| Method | Endpoint | Description | Auth Required |
|---|---|---|---|
| `GET` | `/api/orders` | Paginated order history for the authenticated user | Customer / Admin |
| `GET` | `/api/orders/{id}` | Full order summary and snapshot details | Customer / Admin (Owner only) |
| `POST` | `/api/orders/{id}/cancel` | Cancel order within 48h with stock/point restoration & refund | Customer / Admin (Owner only) |
| `POST` | `/api/orders/{id}/buy-again` | Re-adds items from previous order back into active cart | Customer / Admin (Owner only) |

---

## 2. Order Details & Snapshots

When viewing an order (`GET /api/orders/{id}`), the server returns historical snapshot details:
- **Order Identification**: `id`, unique formatted `orderNumber` (e.g., `EB-YYYYMMDD-0001`).
- **Items & Pricing**: Line items with snapshot title, ISBN, quantity, unit price at purchase time, and line total.
- **Totals**: Subtotal, gift points redeemed, points discount, final payable amount, and points to be earned upon delivery.
- **Shipping Address**: Snapshot of recipient name, phone, line1, line2, city, state, postal code, and country code.
- **Delivery Estimate**: Server-calculated `expectedDeliveryDate`.
- **Payment Summary**: Method (`CARD`, `UPI`, `COD`), status (`PENDING`, `SUCCESS`, `FAILED`, `REFUNDED`), and transaction reference (`SIM-XXXXXXXXXXXX`).
- **Lifecycle Events**: Ordered timeline of status transitions in `statusHistory`.
- **Cancellation Metadata**: Dynamic `cancellation` indicator informing the client if the order is cancellable, the expiration timestamp, or the reason why cancellation is not permitted.

---

## 3. Order Cancellation Policy

### Rules & Validation Order
1. **Ownership**: Authenticated user must own the order. Non-owners receive `404 ORDER_NOT_FOUND` to avoid ID enumeration.
2. **State Check**:
   - If already cancelled: returns `409 ORDER_ALREADY_CANCELLED`.
   - If not in a cancellable status (`PLACED`, `CONFIRMED`, `PACKED` are cancellable; `SHIPPED` and `DELIVERED` are not): returns `409 ORDER_NOT_CANCELLABLE_STATUS`.
3. **Time Window**:
   - Cancellation is permitted strictly within `app.business.cancellation-window-hours` (default: 48 hours) from order `createdAt`.
   - Evaluated server-side using UTC `Clock`. Beyond 48h returns `409 CANCELLATION_WINDOW_EXPIRED`.

### Transactional Effects
Order cancellation executes within a single database transaction:
1. Status transitions to `CANCELLED`, setting `cancelledAt` and `cancelReason`.
2. New entry appended to `order_status_history`.
3. **Inventory Restoration**: Reserved quantities for all books in the order are restored via row-level locks in ascending ID order to prevent deadlocks.
4. **Gift Point Refund**: If points were redeemed at checkout, a `REFUND` ledger transaction is created and balance is refunded.
5. **Payment Gateway Refund**:
   - If payment status was `SUCCESS`, `paymentGateway.refundPayment(...)` is invoked and payment status becomes `REFUNDED` with `refundedAt`.
   - If payment status was `PENDING`, payment status becomes `FAILED` ("Order cancelled before payment").

---

## 4. Buy-Again Operation

`POST /api/orders/{id}/buy-again` enables repurchasing books from previous orders:
- **Minimum Quantity Strategy**: Re-adds each book to the user's active cart. If the book is already in the cart, the cart quantity is set to at least the previously ordered quantity (up to available stock and per-line limit of 10).
- **Catalogue & Stock Validation**:
  - Inactive or deleted books are skipped and recorded in `issues` with reason `BOOK_INACTIVE`.
  - Out-of-stock books (stock = 0) are skipped with reason `OUT_OF_STOCK`.
  - Books with limited stock are added up to available quantity with reason `QUANTITY_REDUCED`.
  - Live catalogue pricing is applied automatically in the updated cart.
- **Fail-Safe**: If no books could be added from the order, returns `409 NO_ITEMS_AVAILABLE`.

---

## 5. Concurrency & Deadlock Prevention

All order modifications enforce strict lock ordering:
1. Lock User row (`SELECT ... FROM users WHERE id = ? FOR NO KEY UPDATE`) with `lock_timeout = 5000ms`.
2. Lock Cart row (for buy-again).
3. Lock Book rows ordered by book ID ascending (`SELECT ... FROM books WHERE id IN (...) ORDER BY id FOR NO KEY UPDATE`).
