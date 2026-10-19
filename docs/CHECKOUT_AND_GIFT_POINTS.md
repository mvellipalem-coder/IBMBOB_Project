# Checkout, Delivery Addresses and Gift Points

This document explains how `POST /api/checkout`, the address API and the gift-point rules work. The approved contract is
`backend/src/main/resources/openapi.yaml`; this file explains the rules behind it. Every number below is
**configuration** (`app.business.*` in `application.properties`), not hard-coded Java.

## 1. Business rules and their demo defaults

The contract fixes the rules below. The "demo default" column is what ships in `application.properties`.
Change the property to change the rule; no code change or migration is needed.

| Rule | Property | Demo default | Source |
|---|---|---|---|
| Money value of one gift point | `app.business.point-value` | `1.00` | openapi.yaml: "1 point = 1.00 off" |
| Highest share of the **subtotal** payable with points | `app.business.max-redeem-percent-of-subtotal` | `50` | openapi.yaml: "at most 50% of the cart subtotal (rounded down)" |
| Amount paid that earns one point | `app.business.earn-amount-per-point` | `10.00` | openapi.yaml: example total 1099.00 earns 109 points |
| Saved addresses per user | `app.business.max-addresses-per-user` | `10` | openapi.yaml: `ADDRESS_LIMIT_REACHED` |
| Normal delivery time (days) | `app.business.delivery-days` | `5` | openapi.yaml: "order date + 5 days" |
| Extra days for remote postal codes | `app.business.remote-area-extra-days` | `2` | openapi.yaml: "+2 for remote postal codes" |
| Which postal codes are "remote" | `app.business.remote-area-postal-prefixes` | **empty** | *Not specified by the contract.* |
| How long an `Idempotency-Key` is remembered | `app.business.idempotency-key-ttl-hours` | `24` | openapi.yaml: `IdempotencyKey` parameter |

**Remote areas.** The contract mentions "+2 for remote postal codes" but never says which codes are remote. No list is
invented: the default is empty, so by default **no** postal code is remote and every order is due in 5 days. To use the
rule, set a comma-separated list of postal-code prefixes, for example `app.business.remote-area-postal-prefixes=744,790`.
Matching ignores case, spaces and hyphens.

## 2. The points-to-money conversion rule

```
discount          = pointsToRedeem x pointValue                  (2 decimal places)
maxRedeemable     = floor( subtotal x maxRedeemPercent / 100 / pointValue )   (whole points)
allowed           = pointsToRedeem <= min(balance, maxRedeemable)
totalAmount       = subtotal - discount                          (never negative: discount <= 50% of subtotal)
pointsEarned      = floor( totalAmount / earnAmountPerPoint )    (credited when the order is DELIVERED)
```

Worked examples with the demo defaults:

| Subtotal | Balance | Request | Result |
|---|---|---|---|
| 1199.00 | 250 | 100 points | Discount 100.00, total 1099.00, earns 109 points on delivery |
| 1199.00 | 1000 | 600 points | `422 INVALID_POINTS_REDEMPTION`: "You can redeem at most 599 points for this order." |
| 1199.00 | 1000 | 599 points | Discount 599.00, total 600.00 |
| 1199.00 | 250 | 300 points | `422 INVALID_POINTS_REDEMPTION`: "You only have 250 gift points available." |

Rounding is always **down**, so the discount can never exceed the configured share of the subtotal.

`GET /api/gift-points/balance` returns the balance together with `pointValue`, `maxRedeemPercentOfSubtotal` and
`earnAmountPerPoint`, so a client can show the same rule the server will apply. The client never sends a discount or a total.

### Safety rules for points

1. **The balance comes from the database** (`users.points_balance`), read after the user's row is locked. The request only says how many points to *spend*.
2. **A balance can never be negative.** The service checks first. As a second line of defence the database has `CHECK (points_balance >= 0)`, so an over-redemption makes the ledger insert fail and the whole checkout rolls back.
3. **Every change is in the ledger** (`gift_point_transactions`, append-only; the database refuses `UPDATE` and `DELETE`). Checkout writes one `REDEEM` row (negative `points`, with `balance_after`). `ux_gpt_once_per_order` allows only one `REDEEM` per order.
4. **Earning happens on delivery, not at checkout.** Checkout only stores the expected amount in `orders.points_earned`. The delivery module (not part of this task) will write the `EARN` row.

## 3. What checkout does

`POST /api/checkout` with header `Idempotency-Key` and body `{ addressId, paymentMethod, pointsToRedeem? }`.
Unknown fields (price, total, status, user id ...) are rejected with `400`.

Everything below happens in **one database transaction** (`CheckoutServiceImpl.checkout`):

1. Lock the user's row (`SELECT ... FOR UPDATE`).
2. Idempotency check (section 4).
3. Reject an empty cart: `409 EMPTY_CART`.
4. Load the address **for this user only**: `404 ADDRESS_NOT_FOUND` (also for another user's address).
5. Lock the books of the cart in ascending id order. Re-price every line from the catalogue and check stock and availability: `409 INSUFFICIENT_STOCK` with a message such as `Only 1 copy of "Effective Java" is available. Update your cart and try again.` A book that was withdrawn from sale after it was added gets `"<title>" is no longer available. Update your cart and try again.`
6. Validate the points against the balance and the cap: `422 INVALID_POINTS_REDEMPTION`.
7. Create the order (`PLACED`) with snapshots of the items (title, ISBN, unit price, quantity) and of the delivery address, a `PENDING` payment for `totalAmount`, and a status-history row. The order number is `EB-yyyyMMdd-NNNN` from a database sequence.
8. Decrement stock.
9. Append the `REDEEM` ledger entry (if points > 0).
10. Store the idempotency key with the order id, and clear the cart.

If **anything** fails, everything rolls back: no order, stock and points unchanged, the cart is kept and the idempotency key is *not* burned, so the client can fix the problem and retry with the same key.

The response is `201` with the order, a `Location: /api/orders/{id}` header, and `payment.status = PENDING`. Orders with
`CARD` or `UPI` are paid later with `POST /api/payments/{orderId}/process` (a different module); `COD` stays `PENDING` until delivery.

## 4. Idempotency

`Idempotency-Key` is required (`^[A-Za-z0-9_-]{16,64}$`; a missing or malformed key is `400 VALIDATION_FAILED` with the field name `Idempotency-Key`). Keys are scoped to user + operation (`CHECKOUT`) and remembered for 24 hours.

| Situation | Result |
|---|---|
| New key | Normal checkout |
| Same key, same request (address, method and points; omitting points equals `0`) | The **original order** again, `201` with `Idempotent-Replayed: true`. Nothing is changed a second time |
| Same key, different request | `422 IDEMPOTENCY_KEY_REUSED` |
| Same key sent at the same moment | The second request waits for the first one's lock and then replays its result. If the lock cannot be obtained within 5 seconds the client gets `409 IDEMPOTENCY_KEY_IN_PROGRESS` and retries |
| Key older than 24 hours | Forgotten; usable again |

The key row is written in the **same transaction** as the order, so a stored key always points to a committed order.
`UNIQUE(user_id, operation, idempotency_key)` in the database is the last line of defence.

## 5. Concurrency

Locks are always taken in the same order, so concurrent checkouts cannot deadlock:

1. **User row** - one user's checkouts and address changes run one after the other. This is what stops the same points being spent twice and the same key creating two orders.
2. **Cart row.**
3. **Book rows, ascending id** - two users buying the last copy are serialised; the second sees the reduced stock and gets `409 INSUFFICIENT_STOCK`. Stock never goes below zero (`ck_books_stock` is the backstop).

Waiting is bounded (`lock_timeout` of 5 seconds per checkout transaction), never infinite.

The integration tests prove this with real threads against a real PostgreSQL: two buyers for the last copy (one `201`, one `409`), six buyers for four copies (four `201`, two `409`), the same key twice at once (one order), and two checkouts of one user spending the same points (one order, balance 0).

## 6. Delivery addresses (`/api/addresses`)

| Rule | Behaviour |
|---|---|
| Ownership | Every lookup includes the user id from the token. Another user's address gives `404 ADDRESS_NOT_FOUND`, the same as a missing one |
| Limit | At most 10 per user: `409 ADDRESS_LIMIT_REACHED` |
| Default | The first address becomes the default. `isDefault=true` moves the flag to this address. `isDefault=false` or omitted never removes the flag from the current default. The database allows only one default per user (`ux_addresses_one_default`) |
| Delete | `204`. If the default is deleted, the most recently created remaining address becomes the default |
| List order | Default first, then newest first |
| Snapshots | An order keeps its own copy of the address, so editing or deleting the saved address never changes past orders |

Changes lock the user's row first, so two simultaneous requests cannot both pass the limit check or create two defaults.

## 7. Error summary

| Situation | Status | Code |
|---|---|---|
| Missing/malformed `Idempotency-Key`, unknown or invalid body fields, bad `pointsToRedeem` | 400 | `VALIDATION_FAILED` |
| No token | 401 | `UNAUTHORIZED` |
| Address missing or not yours | 404 | `ADDRESS_NOT_FOUND` |
| Empty cart | 409 | `EMPTY_CART` |
| Stock too low or book withdrawn | 409 | `INSUFFICIENT_STOCK` |
| Same key in progress | 409 | `IDEMPOTENCY_KEY_IN_PROGRESS` |
| More points than balance or cap | 422 | `INVALID_POINTS_REDEMPTION` |
| Same key, different request | 422 | `IDEMPOTENCY_KEY_REUSED` |

Error messages never contain stack traces, SQL or other users' data.

## 8. Where the code is

| Concern | Class |
|---|---|
| Endpoints | `AddressController`, `CheckoutController`, `GiftPointController` |
| Checkout transaction | `CheckoutServiceImpl` |
| Point maths | `GiftPointPolicy` |
| Delivery date | `DeliveryEstimator` |
| Addresses / ledger reads | `AddressServiceImpl`, `GiftPointServiceImpl` |
| Schema added for checkout | `db/migration/V3__checkout_support.sql` |
| Tests | `GiftPointPolicyTest`, `DeliveryEstimatorTest`, `AddressControllerIntegrationTest`, `CheckoutControllerIntegrationTest` |
